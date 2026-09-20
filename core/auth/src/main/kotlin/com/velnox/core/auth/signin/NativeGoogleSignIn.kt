package com.velnox.core.auth.signin

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.velnox.core.auth.BuildConfig
import com.velnox.core.common.coroutines.DispatcherProvider
import com.velnox.core.common.error.AppError
import com.velnox.core.logging.VelnoxLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of an on-device Google sign-in attempt.
 *
 * Cancellation is modelled separately from failure: the user closing the account
 * chooser is not an error and must not raise an error dialog.
 */
sealed interface GoogleSignInOutcome {
    /** A Google ID token was obtained; exchange it with the Velnox backend next. */
    data class IdTokenObtained(val idToken: String) : GoogleSignInOutcome

    /** The user dismissed the Google sheet. */
    data object Cancelled : GoogleSignInOutcome

    /** `velnox.google.webClientId` was not set for this build. */
    data object NotConfigured : GoogleSignInOutcome

    /** No Google account is available on this device. */
    data object NoAccountOnDevice : GoogleSignInOutcome

    data class Failed(val error: AppError) : GoogleSignInOutcome
}

/**
 * Native Google sign-in via Credential Manager.
 *
 * ## Why this and not a WebView or an OAuth redirect
 *
 * Velnox web signs in with a full-page Google OAuth redirect and stores the
 * resulting session in an httpOnly cookie. Neither half of that survives on
 * Android: a Custom Tab cannot hand its cookies back to the app, and putting the
 * OAuth flow in a WebView is both prohibited by this project's rules and bad
 * practice (Google actively blocks embedded user agents).
 *
 * Credential Manager is the supported native equivalent. It returns a **Google ID
 * token** whose `aud` is the supplied `serverClientId`. Passing the Velnox *web*
 * client id here is what lets the backend's existing audience check
 * (`claims.aud !== GOOGLE_CLIENT_ID`) pass unchanged — no new OAuth client, no
 * relaxed verification.
 *
 * ## No fake login, ever
 *
 * If the client id is missing, this class reports [GoogleSignInOutcome.NotConfigured]
 * and the UI explains that sign-in is unavailable in this build. It never returns
 * a synthesised identity, never accepts a token it produced itself, and never
 * bypasses the server. Authorization always comes from the backend's response.
 */
@Singleton
class NativeGoogleSignIn @Inject constructor(
    @ApplicationContext private val context: Context,
    // Injected through the module's own DispatcherProvider, like AuthRepository and
    // SessionManager. A bare `CoroutineDispatcher` parameter cannot be provided by
    // Dagger — a Kotlin default value is invisible to it — so the unqualified type had
    // no binding and every app failed at hiltJavaCompile.
    private val dispatchers: DispatcherProvider,
) {

    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    /** `true` when this build carries a Google web client id. */
    val isConfigured: Boolean get() = webClientId.isNotBlank()

    /**
     * Shows the Google account sheet and returns an ID token.
     *
     * @param activity an Activity context is required: Credential Manager renders a
     *   system sheet and needs a live window.
     */
    suspend fun requestIdToken(activity: Activity): GoogleSignInOutcome {
        if (!isConfigured) {
            VelnoxLog.w(TAG) { "Google sign-in requested but no web client id is configured" }
            return GoogleSignInOutcome.NotConfigured
        }

        return withContext(dispatchers.io) {
            try {
                // Always show the account chooser: a user with several Google
                // accounts on the device must be able to pick the right one, and
                // auto-select would silently sign them in as whoever used the
                // device last.
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val response = credentialManager.getCredential(
                    context = activity,
                    request = request,
                )

                extractIdToken(response)
            } catch (cancellation: GetCredentialCancellationException) {
                VelnoxLog.d(TAG) { "User dismissed the Google sign-in sheet" }
                GoogleSignInOutcome.Cancelled
            } catch (noCredential: NoCredentialException) {
                VelnoxLog.w(TAG) { "No Google account is available on this device" }
                GoogleSignInOutcome.NoAccountOnDevice
            } catch (failure: GetCredentialException) {
                VelnoxLog.e(TAG) { "Credential Manager failure: ${failure.type}" }
                GoogleSignInOutcome.Failed(
                    AppError.Unexpected(serverMessage = failure.message, cause = failure),
                )
            } catch (throwable: Throwable) {
                VelnoxLog.e(TAG) { "Unexpected Google sign-in failure" }
                GoogleSignInOutcome.Failed(
                    AppError.Unexpected(serverMessage = throwable.message, cause = throwable),
                )
            }
        }
    }

    private suspend fun extractIdToken(response: GetCredentialResponse): GoogleSignInOutcome =
        try {
            val credential = response.credential
            // The typed subclass carries the parsed token; fall back to the raw
            // credential type so a Play-services-version difference degrades to a
            // clear error instead of a cast failure.
            val googleCredential = if (
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data)
            } else if (credential is CustomCredential) {
                GoogleIdTokenCredential.createFrom(credential.data)
            } else {
                null
            }

            if (googleCredential == null) {
                GoogleSignInOutcome.Failed(
                    AppError.Unexpected(serverMessage = "Unsupported credential type: ${credential.type}"),
                )
            } else {
                GoogleSignInOutcome.IdTokenObtained(googleCredential.idToken)
            }
        } catch (parseError: GoogleIdTokenParsingException) {
            VelnoxLog.e(TAG) { "Could not parse the Google ID token" }
            GoogleSignInOutcome.Failed(
                AppError.Unexpected(serverMessage = parseError.message, cause = parseError),
            )
        }

    private val webClientId: String
        get() = BuildConfig.VELNOX_GOOGLE_WEB_CLIENT_ID.trim()

    private companion object {
        const val TAG = "NativeGoogleSignIn"
    }
}
