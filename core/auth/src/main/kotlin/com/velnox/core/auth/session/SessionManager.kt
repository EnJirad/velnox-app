package com.velnox.core.auth.session

import com.velnox.core.common.coroutines.DispatcherProvider
import com.velnox.core.common.di.ApplicationScope
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.network.auth.AuthTokenProvider
import com.velnox.core.storage.secure.SecureTokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Velnox session token.
 *
 * This is the only component that reads or writes the token, and it is the
 * implementation behind [AuthTokenProvider] so OkHttp can attach it without the
 * network layer knowing anything about storage.
 *
 * ## Threading contract
 *
 * [currentSessionToken] and [onSessionRejected] are called from OkHttp's
 * dispatcher threads and must never block, so the token is held in a `@Volatile`
 * field and read synchronously. [SecureTokenStore] is only touched before the
 * first request (restore) and after an explicit sign-in/sign-out.
 *
 * ## Restore semantics
 *
 * A persisted token is loaded once per process. Until that finishes,
 * [isRestored] is `false` and callers must not conclude "signed out" — that race
 * is exactly what would flash a login screen on a cold start with a valid session.
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: SecureTokenStore,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : AuthTokenProvider {

    @Volatile
    private var sessionToken: String? = null

    private val restored = CompletableDeferred<Unit>()

    private val _hasSession = MutableStateFlow(false)

    /** `true` once a token was successfully loaded from or written to storage. */
    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    /** `true` once the initial disk read finished (successfully or not). */
    val isRestored: Boolean get() = restored.isCompleted

    init {
        applicationScope.launch {
            val stored = withContext(dispatchers.io) { tokenStore.readSessionToken() }
            if (!stored.isNullOrBlank()) {
                sessionToken = stored
                _hasSession.value = true
                VelnoxLog.i(TAG) { "Restored persisted session" }
            }
            restored.complete(Unit)
            if (!tokenStore.isPersistent()) {
                VelnoxLog.w(TAG) { "Session storage is not persistent on this device" }
            }
        }
    }

    /** Suspends until the initial disk read has completed. */
    suspend fun awaitRestored() {
        restored.await()
    }

    /**
     * The token OkHttp should present, or `null` when signed out.
     * Synchronous by contract — see [AuthTokenProvider.currentSessionToken].
     */
    override fun currentSessionToken(): String? = sessionToken

    /**
     * Called by the network layer on HTTP 401.
     *
     * Runs on an OkHttp thread, so it only flips in-memory state and schedules the
     * disk write. The backend has already refused the token (expired, or its `jti`
     * is in `revoked_tokens`), so retrying with it is pointless.
     */
    override fun onSessionRejected() {
        if (sessionToken == null) return
        VelnoxLog.w(TAG) { "Session rejected by the server — clearing" }
        sessionToken = null
        _hasSession.value = false
        applicationScope.launch {
            withContext(dispatchers.io) { tokenStore.clearSessionToken() }
            sessionRejectedListeners.forEach { listener -> runCatching { listener() } }
        }
    }

    /** Persist a freshly issued session token. */
    suspend fun acceptToken(token: String) {
        restored.await()
        sessionToken = token
        _hasSession.value = true
        withContext(dispatchers.io) { tokenStore.writeSessionToken(token) }
        VelnoxLog.i(TAG) { "New session stored" }
    }

    /** Drop the session locally (sign-out). */
    suspend fun clear() {
        sessionToken = null
        _hasSession.value = false
        withContext(dispatchers.io) { tokenStore.clearSessionToken() }
    }

    private val sessionRejectedListeners = mutableListOf<() -> Unit>()

    /**
     * Registers a callback for "the server invalidated our session".
     *
     * Used to drop the realtime socket and reset any in-memory user state without
     * making this class depend on either.
     */
    fun onSessionRejected(listener: () -> Unit) {
        synchronized(sessionRejectedListeners) { sessionRejectedListeners.add(listener) }
    }

    private companion object {
        const val TAG = "SessionManager"
    }
}
