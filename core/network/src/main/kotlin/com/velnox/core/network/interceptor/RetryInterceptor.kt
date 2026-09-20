package com.velnox.core.network.interceptor

import com.velnox.core.logging.VelnoxLog
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

/**
 * Retries **idempotent** requests only.
 *
 * The project rule is explicit: never retry a mutation such as an order or a
 * payment without an idempotency strategy. Velnox's checkout is idempotent
 * server-side via the `checkout_requests` table and an idempotency key, but that
 * key belongs to the checkout flow — it is not a licence for this interceptor to
 * replay arbitrary writes. So:
 *
 *  * `GET` and `HEAD` may be retried.
 *  * `POST`, `PUT`, `PATCH`, `DELETE` are **never** retried here, regardless of
 *    status code. A failed write surfaces to the user, who retries deliberately.
 *  * Only transport failures and 502/503/504/429 are retried — a 4xx is a
 *    deterministic answer and repeating it wastes the user's connection.
 *  * Backoff is exponential with jitter, bounded by [MAX_RETRIES] and
 *    [MAX_BACKOFF_MS], so a flaky network cannot multiply traffic.
 *  * `Retry-After` is honoured when the server sends one.
 */
class RetryInterceptor(
    private val maxRetries: Int = MAX_RETRIES,
    private val sleeper: (Long) -> Unit = { millis -> Thread.sleep(millis) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.method.uppercase(Locale.US) !in RETRYABLE_METHODS) {
            return chain.proceed(request)
        }

        var attempt = 0
        var lastResponse: Response? = null

        while (true) {
            try {
                val response = chain.proceed(request)
                if (!shouldRetry(response.code) || attempt >= maxRetries) {
                    return response
                }

                // Drain and close so the connection returns to the pool.
                lastResponse?.close()
                lastResponse = response
                val delay = backoffMillis(attempt, parseRetryAfter(response))
                VelnoxLog.w(TAG) {
                    "HTTP ${response.code} on ${request.method} " +
                        "${VelnoxLogRedaction.path(request.url.toString())} — retry ${attempt + 1}/$maxRetries in ${delay}ms"
                }
                response.close()
                sleeper(delay)
                attempt++
            } catch (io: java.io.IOException) {
                if (attempt >= maxRetries) throw io
                val delay = backoffMillis(attempt, retryAfterMillis = null)
                VelnoxLog.w(TAG) {
                    "Transport failure on ${request.method} " +
                        "${VelnoxLogRedaction.path(request.url.toString())} — retry ${attempt + 1}/$maxRetries in ${delay}ms"
                }
                sleeper(delay)
                attempt++
            }
        }
    }

    private fun shouldRetry(status: Int): Boolean =
        status == 429 || status == 502 || status == 503 || status == 504

    private fun backoffMillis(attempt: Int, retryAfterMillis: Long?): Long {
        if (retryAfterMillis != null) return retryAfterMillis.coerceIn(0L, MAX_BACKOFF_MS)
        val exponential = BASE_BACKOFF_MS shl attempt
        val jitter = ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MS)
        return (exponential + jitter).coerceAtMost(MAX_BACKOFF_MS)
    }

    /** `Retry-After` as delta-seconds, when present and sane. */
    private fun parseRetryAfter(response: Response): Long? {
        val header = response.header("Retry-After") ?: return null
        val seconds = header.trim().toLongOrNull() ?: return null
        if (seconds <= 0) return null
        return seconds * 1000L
    }

    companion object {
        private const val TAG = "Retry"
        private const val MAX_RETRIES = 2
        private const val BASE_BACKOFF_MS = 400L
        private const val MAX_BACKOFF_MS = 5_000L

        private val RETRYABLE_METHODS = setOf("GET", "HEAD")
    }
}

/** Path-only helper so retry logs never contain a query string. */
internal object VelnoxLogRedaction {
    fun path(url: String): String = url.substringAfter("://", url).substringBefore('?')
}
