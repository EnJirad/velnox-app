package com.velnox.core.auth.session

import com.velnox.core.network.BuildConfig
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the "session token" concept to the cookie the Velnox backend reads.
 *
 * The backend authenticates from `req.cookies.velnox_session`
 * (`backend/middleware/auth.ts`), a cookie it sets with `httpOnly`, `secure`,
 * `sameSite: "none"` because the API and the web apps are cross-site. A native
 * client has no browser to hold that cookie, so this jar *projects* the single
 * source of truth ([SessionManager]) into exactly that cookie for exactly the
 * backend host.
 *
 * Two practical consequences, both verified against the backend:
 *
 *  * `backend/middleware/rate-limit.ts` keys its per-user bucket on this cookie and
 *    falls back to IP without it. Presenting the cookie keeps the native client on
 *    the authenticated budget instead of the anonymous one.
 *  * `backend/realtime/index.ts` authenticates the `/ws` handshake from the same
 *    cookie, so the socket and the REST calls share one credential.
 *
 * The jar is host-scoped on purpose: no Velnox cookie may ever be offered to a
 * Cloudflare R2 or CDN host.
 */
@Singleton
class VelnoxSessionCookieJar @Inject constructor(
    private val sessionManager: SessionManager,
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!isVelnoxHost(url)) return
        val session = cookies.firstOrNull { it.name == SESSION_COOKIE } ?: return

        if (session.value.isBlank() || session.expiresAt < System.currentTimeMillis()) {
            // The backend cleared the cookie — it revoked this session server-side
            // (logout, or an admin revocation). Mirror that locally so the app does
            // not keep presenting a token the server has already killed.
            sessionManager.onSessionRejected()
        }
        // A non-blank cookie carries no extra information: the token we already
        // hold is the same value, and it was stored by us, not by the response.
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!isVelnoxHost(url)) return emptyList()
        val token = sessionManager.currentSessionToken() ?: return emptyList()
        return listOf(
            Cookie.Builder()
                .name(SESSION_COOKIE)
                .value(token)
                .hostOnlyDomain(url.host)
                .path("/")
                .secure()
                .httpOnly()
                .build(),
        )
    }

    private fun isVelnoxHost(url: HttpUrl): Boolean =
        url.host.equals(apiHost, ignoreCase = true)

    private companion object {
        const val SESSION_COOKIE = "velnox_session"

        /**
         * Read from the build-time base URL so a staging deployment works without
         * a code change. An unparseable value yields an empty host, which fails
         * closed: no cookie is offered anywhere.
         */
        val apiHost: String = runCatching {
            java.net.URI(BuildConfig.VELNOX_API_BASE_URL).host.orEmpty()
        }.getOrDefault("")
    }
}
