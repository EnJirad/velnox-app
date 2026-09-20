package com.velnox.core.auth.repository

import com.velnox.core.auth.api.VelnoxAuthApi
import com.velnox.core.auth.dto.ChangePasswordRequest
import com.velnox.core.auth.dto.MemberLoginRequest
import com.velnox.core.auth.dto.NativeGoogleLoginRequest
import com.velnox.core.auth.mapper.partialIdentity
import com.velnox.core.auth.mapper.toDomain
import com.velnox.core.auth.model.AuthState
import com.velnox.core.auth.model.VelnoxUser
import com.velnox.core.auth.session.SessionManager
import com.velnox.core.common.coroutines.DispatcherProvider
import com.velnox.core.common.di.ApplicationScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.network.safeApiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for "who is signed in" across all three apps.
 *
 * ## Restore, and why it is not a straight line
 *
 * A persisted JWT is not proof of identity: it may have been revoked by a logout on
 * another device, invalidated by an employee-deactivation, or simply expired. So the
 * token is always validated with `GET /api/auth/me` before the app treats the user as
 * signed in — and the three outcomes are kept distinct:
 *
 *  * valid → [AuthState.Authenticated]
 *  * rejected (401 / 404) → the token is discarded, [AuthState.Unauthenticated]
 *  * **unreachable** (offline, timeout, 5xx) → the token is **kept** and the state
 *    becomes [AuthState.Unverified]. A tunnel must not sign a user out, and the app
 *    must not invent an identity it could not confirm.
 *
 * ## What this class never does
 *
 * It never fabricates a user, never accepts a locally generated token, and never
 * grants a role or permission the server did not report.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: VelnoxAuthApi,
    private val sessionManager: SessionManager,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Restoring)

    /** Observable authentication state for the whole app. */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        applicationScope.launch { restoreSession() }
    }

    /**
     * Validates any persisted session. Also serves as the retry action from the
     * [AuthState.Unverified] screen.
     */
    suspend fun restoreSession(): VelnoxResult<VelnoxUser> {
        sessionManager.awaitRestored()

        if (sessionManager.currentSessionToken().isNullOrBlank()) {
            _authState.value = AuthState.Unauthenticated
            return VelnoxResult.Failure(AppError.Unauthorized())
        }

        return when (val result = fetchCurrentUser()) {
            is VelnoxResult.Success -> result

            is VelnoxResult.Failure -> {
                if (result.error.isSessionInvalid || result.error is AppError.NotFound) {
                    VelnoxLog.i(TAG) { "Persisted session is no longer valid" }
                    sessionManager.clear()
                    _authState.value = AuthState.Unauthenticated
                } else {
                    // Keep the token — the failure says nothing about its validity.
                    _authState.value = AuthState.Unverified(result.error)
                }
                result
            }
        }
    }

    /**
     * Completes native Google sign-in.
     *
     * [idToken] comes from Credential Manager. The backend verifies it against
     * Google's token-info endpoint with the same audience check the browser flow
     * uses. If no session token comes back, the deployment has not been updated with
     * the native support in `ANDROID_AUTH.md` — reported as a configuration error,
     * never worked around with a locally minted credential.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): VelnoxResult<VelnoxUser> {
        if (idToken.isBlank()) {
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "Missing Google ID token."))
        }

        val response = safeApiCall(json) {
            api.signInWithGoogleIdToken(NativeGoogleLoginRequest(idToken = idToken))
        }

        return when (response) {
            is VelnoxResult.Failure -> response
            is VelnoxResult.Success -> storeSessionAndLoadUser(
                apiToken = response.data.token,
                fallbackUser = response.data.user?.toDomain(),
            )
        }
    }

    /**
     * VelCenter staff sign-in with an e-mail address or an employee id.
     *
     * The backend's rules stay authoritative and unchanged: the account must be
     * `active`, must hold `owner`/`admin`/`staff`, and must have a password hash. The
     * client checks below only avoid a pointless round trip.
     */
    suspend fun signInStaff(identifier: String, password: String): VelnoxResult<VelnoxUser> {
        val trimmed = identifier.trim()
        if (trimmed.isEmpty() || password.isEmpty()) {
            return VelnoxResult.Failure(
                AppError.Validation(serverMessage = "Member ID or e-mail and password are required."),
            )
        }

        val response = safeApiCall(json) {
            api.memberLogin(MemberLoginRequest(identifier = trimmed, password = password))
        }

        return when (response) {
            is VelnoxResult.Failure -> response
            is VelnoxResult.Success -> storeSessionAndLoadUser(
                apiToken = response.data.token,
                fallbackUser = partialIdentity(
                    id = response.data.userId,
                    email = response.data.email,
                    name = response.data.name,
                    role = response.data.role,
                    department = response.data.department,
                ),
            )
        }
    }

    /**
     * Signs out.
     *
     * The local session is always cleared — leaving a usable token on the device
     * after the user asked to sign out would be the worse failure. When the server
     * call fails, the failure is returned so the UI can say that the session may
     * still be live elsewhere until it expires.
     */
    suspend fun signOut(): VelnoxResult<Unit> {
        val remoteResult = safeApiCall(json) { api.logout() }
        sessionManager.clear()
        _authState.value = AuthState.Unauthenticated
        return when (remoteResult) {
            is VelnoxResult.Success -> VelnoxResult.Success(Unit)
            is VelnoxResult.Failure -> remoteResult
        }
    }

    /** Re-reads `/api/auth/me`. Used after profile edits and on pull-to-refresh. */
    suspend fun refreshUser(): VelnoxResult<VelnoxUser> = fetchCurrentUser()

    /** `POST /api/auth/change-password` for the signed-in account. */
    suspend fun changePassword(currentPassword: String, newPassword: String): VelnoxResult<Unit> {
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            return VelnoxResult.Failure(
                AppError.Validation(
                    serverCode = "VALIDATION_ERROR",
                    serverMessage = "New password must be at least $MIN_PASSWORD_LENGTH characters.",
                ),
            )
        }

        val response = safeApiCall(json) {
            api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
        }

        // A successful change clears `must_change_password` server-side, so the
        // cached identity is stale until it is re-read.
        if (response.isSuccess) refreshUser()
        return when (response) {
            is VelnoxResult.Success -> VelnoxResult.Success(Unit)
            is VelnoxResult.Failure -> response
        }
    }

    /** Latest known identity, if the session has been confirmed. */
    fun currentUserOrNull(): VelnoxUser? = _authState.value.userOrNull

    private suspend fun fetchCurrentUser(): VelnoxResult<VelnoxUser> {
        val response = withContext(dispatchers.io) { safeApiCall(json) { api.me() } }
        return when (response) {
            is VelnoxResult.Failure -> response
            is VelnoxResult.Success -> {
                val user = response.data.user.toDomain()
                _authState.value = AuthState.Authenticated(user)
                VelnoxResult.Success(user)
            }
        }
    }

    /**
     * Stores the session token a login response produced, then establishes the full
     * identity from `/api/auth/me`, so callers receive a complete [VelnoxUser]
     * (permissions, department, must-change-password flag) instead of the trimmed
     * login payload.
     */
    private suspend fun storeSessionAndLoadUser(
        apiToken: String?,
        fallbackUser: VelnoxUser?,
    ): VelnoxResult<VelnoxUser> {
        if (apiToken.isNullOrBlank()) {
            VelnoxLog.e(TAG) {
                "Login succeeded but no session token was returned — the backend needs the " +
                    "native auth support described in ANDROID_AUTH.md"
            }
            return VelnoxResult.Failure(
                AppError.Serialization(
                    serverMessage = "This server build cannot issue a native session. " +
                        "See ANDROID_AUTH.md for the required backend support.",
                ),
            )
        }

        sessionManager.acceptToken(apiToken)

        return when (val me = fetchCurrentUser()) {
            is VelnoxResult.Success -> me

            is VelnoxResult.Failure -> when {
                me.error.isSessionInvalid -> {
                    sessionManager.clear()
                    _authState.value = AuthState.Unauthenticated
                    me
                }

                // The token was just issued, so a transport failure here does not
                // invalidate it. Use the identity the login call returned rather
                // than bouncing the user back to the sign-in screen.
                fallbackUser != null -> {
                    _authState.value = AuthState.Authenticated(fallbackUser)
                    VelnoxResult.Success(fallbackUser)
                }

                else -> {
                    _authState.value = AuthState.Unverified(me.error)
                    me
                }
            }
        }
    }

    private companion object {
        const val TAG = "AuthRepository"

        /** Mirrors the backend's own minimum (`newPassword.length < 8`). */
        const val MIN_PASSWORD_LENGTH = 8
    }
}
