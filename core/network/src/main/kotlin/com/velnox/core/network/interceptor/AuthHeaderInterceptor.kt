package com.velnox.core.network.interceptor

import com.velnox.core.network.BuildConfig
import com.velnox.core.network.auth.AuthTokenProvider
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Velnox session to every authenticated request.
 *
 * ## Why both a cookie and a bearer header
 *
 * The Velnox backend authenticates from the `velnox_session` httpOnly cookie
 * (`backend/middleware/auth.ts`: `req.cookies?.velnox_session`). A native app has
 * no cookie jar held by a browser, so the app persists the same JWT itself.
 *
 * Sending it back in **both** forms makes the client correct against the
 * unmodified backend and against the bearer-aware backend described in
 * `ANDROID_AUTH.md`:
 *
 *  * `Cookie: velnox_session=<jwt>` — satisfies `requireAuth` exactly as a browser
 *    does, so no backend change is needed to *use* a session once one exists.
 *  * `Authorization: Bearer <jwt>` — used by the small backend addition that lets
 *    a native client obtain a session in the first place, and by any future
 *    token-only routes.
 *
 * Two further reasons the cookie matters:
 *
 *  * `backend/middleware/rate-limit.ts` keys its per-user bucket on
 *    `req.cookies.velnox_session`, falling back to IP. Without the cookie every
 *    Android request would share the (much lower) anonymous IP budget.
 *  * `backend/realtime/index.ts` authenticates the WebSocket handshake from the
 *    same cookie, so the same token must be presented there.
 *
 * No `Origin` header is ever added: `backend/middleware/origin-guard.ts` treats a
 * request without Origin as a non-browser client ("curl, Stripe webhook, mobile
 * app") and accepts it, while a foreign Origin would be rejected with 403.
 */
class AuthHeaderInterceptor(private val tokenProvider: AuthTokenProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.currentSessionToken()

        if (token.isNullOrBlank()) {
            return chain.proceed(request)
        }

        // Never attach Velnox credentials to a third-party host. Presigned R2
        // uploads live on Cloudflare and reject an AWS signature that carries an
        // unexpected Authorization header.
        if (!request.url.host.equals(apiHost, ignoreCase = true)) {
            return chain.proceed(request)
        }

        val authenticated = request.newBuilder()
            .header("Cookie", SESSION_COOKIE + "=" + token)
            .header("Authorization", "Bearer " + token)
            .build()

        val response = chain.proceed(authenticated)

        // A 401 means the JWT is expired or was revoked (logout on another
        // device, admin revocation). Tell the session layer once; it clears the
        // token and emits a signed-out state. The body is left untouched so the
        // caller still sees the backend's own error envelope.
        if (response.code == 401) {
            tokenProvider.onSessionRejected()
        }

        return response
    }

    companion object {
        const val SESSION_COOKIE = "velnox_session"

        /**
         * Host of the configured Velnox backend, derived from the build-time
         * base URL. If the base URL cannot be parsed the value stays empty and
         * the `equals` check above fails, so credentials are withheld rather
         * than sent to an unknown host — a misconfiguration must fail closed.
         */
        private val apiHost: String = runCatching {
            java.net.URI(BuildConfig.VELNOX_API_BASE_URL).host.orEmpty()
        }.getOrDefault("")
    }
}
