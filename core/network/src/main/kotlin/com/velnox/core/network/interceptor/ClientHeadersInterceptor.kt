package com.velnox.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Identifies the client to the backend without leaking anything sensitive.
 *
 * `X-Velnox-Client` exists so the backend can distinguish native traffic from
 * browser traffic when it needs to (rate-limit tuning, incident triage). No
 * device identifier is sent: `docs/SECURITY.md` forbids PII in outgoing metadata,
 * and a hardware id would be exactly that.
 *
 * `Accept-Language` mirrors the app's default locale so the backend's own
 * localised error `message` values come back in the language the user reads.
 */
class ClientHeadersInterceptor(
    private val appId: String,
    private val clientVersion: String,
    private val localeProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Accept-Language", localeProvider())
            .header("X-Velnox-Client", "$appId/$clientVersion (android)")
            .build()

        return chain.proceed(request)
    }
}
