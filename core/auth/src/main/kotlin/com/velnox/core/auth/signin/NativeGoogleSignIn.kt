package com.velnox.core.auth.signin

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.velnox.core.auth.BuildConfig
import com.velnox.core.common.coroutines.DispatcherProvider
import com.velnox.core.common.error.AppError
import com.velnox.core.logging.VelnoxLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of an on-device Google sign-in attempt.
 *
 * Every case is a distinct outcome on purpose. Collapsing them into one "failed" value
 * is how the account chooser returning nothing, the provider being absent, and the token
 * being unreadable all end up described to the user as "no account found" — a message
 * that is only correct for one of them and sends everyone else looking in the wrong
 * place.
 */
sealed interface GoogleSignInOutcome {
    /**
     * A Google ID token was obtained; exchange it with the Velnox backend next.
     *
     * [nonce] is the value requested on this attempt and must accompany the token, so
     * the backend can confirm the token was minted for this request.
     */
    data class IdTokenObtained(val idToken: String, val nonce: String) : GoogleSignInOutcome

    /** The user dismissed the Google sheet. Not a failure, and not an error dialog. */
    data object Cancelled : GoogleSignInOutcome

    /** `velnox.google.webClientId` was not set for this build. */
    data object NotConfigured : GoogleSignInOutcome

    /**
     * Google returned no credential at all.
     *
     * Genuinely ambiguous: either the device has no Google account, or this build is
     * not authorised to request one — Google only issues an ID token to an app whose
     * package name **and** signing certificate are registered as an Android OAuth
     * client in the same project as the web client id. Both are configuration or device
     * states, never something this class can work around, and the user-facing message
     * says both because guessing one would be a lie half the time.
     */
    data object NoCredentialAvailable : GoogleSignInOutcome

    /** No credential provider is installed or it is too old (Google Play services). */
    data object ProviderUnavailable : GoogleSignInOutcome

    /** A credential came back but its ID token could not be read. */
    data class TokenUnusable(val detail: String?) : GoogleSignInOutcome

    /** Anything else, carrying the provider's own message so it stays diagnosable. */
    data class Failed(val error: AppError) : GoogleSignInOutcome
}

/**
 * Native Google sign-in via Credential Manager.
 *
 * ## Why this and not a WebView or an OAuth redirect
 *
 * Velnox web signs in with a full-page Google OAuth redirect and stores the resulting
 * session in an httpOnly cookie. Neither half of that survives on Android: a Custom Tab
 * cannot hand its cookies back to the app, and putting the OAuth flow in a WebView is
 * both prohibited by this project's rules and bad practice (Google actively blocks
 * embedded user agents).
 *
 * Credential Manager is the supported native equivalent. It returns a **Google ID
 * token** whose `aud` is the supplied `serverClientId`. Passing the Velnox *web* client
 * id here is what lets the backend's existing audience check
 * (`claims.aud !== GOOGLE_CLIENT_ID`) pass unchanged.
 *
 * ## What has to be true outside this file for the sheet to appear
 *
 * The client id is necessary but not sufficient. Google authorises the *request* from
 * the pair (package name, signing certificate SHA-1), so the project must also hold an
 * **Android** OAuth client for the installed application id and for the certificate the
 * APK is actually signed with. `./gradlew :app:velshop:signingReport` reports what this
 * build will present, and CI prints the same for the CI-signed debug APK — whose
 * certificate differs from a developer machine's and, unless
 * `VELNOX_DEBUG_KEYSTORE_BASE64` pins it, changes between runs. See `ANDROID_AUTH.md`.
 *
 * ## No fake login, ever
 *
 * If the client id is missing, this class reports [GoogleSignInOutcome.NotConfigured]
 * and the UI explains that sign-in is unavailable in this build. It never returns a
 * synthesised identity, never accepts a token it produced itself, and never bypasses the
 * server. Authorization always comes from the backend's response.
 *
 * ## Two different cancellations
 *
 * Closing the account sheet ([GoogleSignInOutcome.Cancelled]) is a user action and is
 * reported as such. Coroutine cancellation — the screen going away while the sheet is
 * open — is not: it is rethrown so the suspending caller is cancelled as usual, and no
 * outcome is invented for a sign-in the user never abandoned.
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

        val nonce = GoogleNonce.generate()

        return try {
            // Credential Manager presents system UI, and the documented usage is to call
            // it with an Activity context from the main thread — the call ultimately
            // reaches `Activity.startIntentSenderForResult`. It is deliberately not
            // wrapped in an IO dispatcher: this is UI orchestration, not blocking work,
            // and handing it a background thread is a deviation from the documented
            // contract for no gain. Nothing here does IO; the backend call happens later
            // in AuthRepository, which owns its own dispatcher.
            val response = withContext(dispatchers.main) {
                credentialManager.getCredential(
                    context = activity,
                    request = buildRequest(nonce),
                )
            }

            extractIdToken(response, nonce)
        } catch (cancellation: GetCredentialCancellationException) {
            VelnoxLog.d(TAG) { "User dismissed the Google sign-in sheet" }
            GoogleSignInOutcome.Cancelled
        } catch (providerUnavailable: GetCredentialProviderConfigurationException) {
            // No CredentialManager provider on the device: Google Play services is
            // missing or out of date. Distinct from "no credential", because the fix is
            // a Play services update, not an account.
            VelnoxLog.w(TAG) { "No credential provider is available on this device" }
            GoogleSignInOutcome.ProviderUnavailable
        } catch (noCredential: NoCredentialException) {
            VelnoxLog.w(TAG) { "Google returned no credential for this app (${noCredential.type})" }
            GoogleSignInOutcome.NoCredentialAvailable
        } catch (failure: GetCredentialException) {
            // Carries the provider's own diagnosis — this is where a DEVELOPER_ERROR for
            // an unregistered package/signing certificate surfaces, and the message is
            // the only accurate description of it, so it is surfaced rather than
            // replaced with a guess.
            VelnoxLog.e(TAG) { "Credential Manager failure: ${failure.type} — ${failure.errorMessage}" }
            GoogleSignInOutcome.Failed(
                AppError.Unexpected(
                    serverMessage = failure.errorMessage?.toString() ?: failure.message,
                    cause = failure,
                ),
            )
        } catch (cancellation: CancellationException) {
            // Cooperate with the caller's scope. `withContext` is documented to rethrow
            // cancellation, and swallowing it here would both break that contract and
            // turn "the user navigated away" into a visible failure.
            throw cancellation
        } catch (throwable: Throwable) {
            VelnoxLog.e(TAG) { "Unexpected Google sign-in failure" }
            GoogleSignInOutcome.Failed(
                AppError.Unexpected(serverMessage = throwable.message, cause = throwable),
            )
        }
    }

    /**
     * Builds the credential request.
     *
     * The two builder flags are load-bearing and must not be "simplified":
     *
     *  * `setFilterByAuthorizedAccounts(false)` — with the filter on, Google returns
     *    nothing for an account that has never signed in to this app, which is exactly
     *    the first sign-in. That is how a first-time user ends up seeing
     *    [GoogleSignInOutcome.NoCredentialAvailable]. Disabling it is what the current
     *    Credential Manager guidance prescribes so that any Google account on the device
     *    can be offered. **Never flip this to `true`.**
     *  * `setAutoSelectEnabled(false)` — auto-select is only meaningful together with the
     *    authorised-accounts filter, and it would silently sign the user in as whoever
     *    used the device last. The user always gets the chooser, which is also what makes
     *    choosing between several accounts possible.
     */
    private fun buildRequest(nonce: String): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    private fun extractIdToken(response: GetCredentialResponse, nonce: String): GoogleSignInOutcome {
        val credential = response.credential
        // The typed credential is a CustomCredential carrying the parsed token; the type
        // string is checked first so a Play-services version difference degrades to a
        // clear error instead of a cast failure.
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleSignInOutcome.Failed(
                AppError.Unexpected(serverMessage = "Unsupported credential type: ${credential.type}"),
            )
        }

        if (credential !is CustomCredential) {
            return GoogleSignInOutcome.TokenUnusable("credential carried no readable data")
        }

        return try {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInOutcome.IdTokenObtained(googleCredential.idToken, nonce)
        } catch (parseError: GoogleIdTokenParsingException) {
            VelnoxLog.e(TAG) { "Could not parse the Google ID token" }
            GoogleSignInOutcome.TokenUnusable(parseError.message)
        }
    }

    private val webClientId: String
        get() = BuildConfig.VELNOX_GOOGLE_WEB_CLIENT_ID.trim()

    private companion object {
        const val TAG = "NativeGoogleSignIn"
    }
}
