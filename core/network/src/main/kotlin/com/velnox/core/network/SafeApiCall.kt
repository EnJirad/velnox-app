package com.velnox.core.network

import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.common.error.appErrorFromHttp
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.network.serialization.VelnoxJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val TAG = "SafeApiCall"

/**
 * Executes a Retrofit call and normalises every outcome into [VelnoxResult].
 *
 * This is the only place network failures become domain failures, so these rules
 * hold for every screen in the project:
 *
 *  * A 2xx response with `success: false` is a failure — the backend does answer
 *    200 with an error envelope in places.
 *  * A 2xx response with `success: true` and a missing payload is a contract
 *    violation, never an empty success. Silently coercing it is how "you have no
 *    orders" gets shown for a broken API.
 *  * Cancellation is rethrown untouched so structured concurrency keeps working.
 *  * Nothing here retries. Retry lives in `RetryInterceptor` and applies to
 *    idempotent requests only.
 */
suspend fun <T> safeApiCall(json: Json = VelnoxJson, call: suspend () -> ApiEnvelope<T>): VelnoxResult<T> =
    execute(json, requireData = true, call = call)

/**
 * Like [safeApiCall], but `data: null` is a valid answer.
 *
 * Required for endpoints such as `GET /api/seller/status`, which answers
 * `{ success: true, data: null }` for an authenticated user who has not applied
 * to become a seller. Treating that as an error would replace the real
 * onboarding state with an error screen.
 */
suspend fun <T> safeApiCallAllowNull(
    json: Json = VelnoxJson,
    call: suspend () -> ApiEnvelope<T?>,
): VelnoxResult<T?> = execute(json, requireData = false, call = call)

private suspend fun <T> execute(
    json: Json,
    requireData: Boolean,
    call: suspend () -> ApiEnvelope<T>,
): VelnoxResult<T> = try {
    val envelope = call()

    when {
        !envelope.success -> {
            val error = envelopeToError(envelope.error)
            VelnoxLog.w(TAG) { "API refusal: ${error.serverCode} — ${error.serverMessage}" }
            VelnoxResult.Failure(error)
        }

        envelope.data == null && requireData -> {
            VelnoxLog.e(TAG) { "Contract violation: success=true with no data" }
            VelnoxResult.Failure(
                AppError.Serialization("The server acknowledged the request but returned no data."),
            )
        }

        else -> VelnoxResult.Success(envelope.data as T)
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    val error = throwable.toAppError(json)
    if (error is AppError.Unexpected) {
        VelnoxLog.e(TAG) { "Unhandled API failure" }
    }
    VelnoxResult.Failure(error)
}

/** `{ success: false, error: { code, message } }` → [AppError]. */
internal fun envelopeToError(body: ApiErrorBody?): AppError {
    val code = body?.code
    val message = body?.message
    return when (code) {
        "UNAUTHORIZED", "INVALID_TOKEN" -> AppError.Unauthorized(code, message)
        "FORBIDDEN" -> AppError.Forbidden(code, message)
        "NOT_FOUND" -> AppError.NotFound(code, message)
        "VALIDATION_ERROR", "INVALID_CREDENTIALS" -> AppError.Validation(code, message)
        "CONFLICT" -> AppError.Conflict(code, message)
        "RATE_LIMITED" -> AppError.RateLimited(code, message)
        null -> AppError.Server(httpStatus = 200, serverCode = null, serverMessage = message)
        else -> AppError.Server(httpStatus = 200, serverCode = code, serverMessage = message)
    }
}

/** Retrofit/OkHttp/IO throwable → [AppError]. */
fun Throwable.toAppError(json: Json = VelnoxJson): AppError = when (this) {
    is AppError -> this

    is HttpException -> {
        val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val envelope = raw?.let { body ->
            runCatching { json.decodeFromString<ApiEnvelope<Unit>>(body) }.getOrNull()
        }
        val status = code()
        // 401 must tear the session down regardless of what the body claims.
        if (status == 401) {
            AppError.Unauthorized(envelope?.error?.code, envelope?.error?.message)
        } else {
            appErrorFromHttp(
                httpStatus = status,
                serverCode = envelope?.error?.code,
                serverMessage = envelope?.error?.message,
            )
        }
    }

    is SocketTimeoutException, is InterruptedIOException ->
        AppError.Timeout(serverMessage = message, cause = this)

    is UnknownHostException, is ConnectException, is NoRouteToHostException, is SSLException ->
        AppError.Offline(serverMessage = message, cause = this)

    is SerializationException ->
        AppError.Serialization(serverMessage = message, cause = this)

    // Ambiguous here; connectivity is by far the most common cause of a plain
    // IOException escaping OkHttp, so it maps to Offline (retryable + honest UI).
    is IOException -> AppError.Offline(serverMessage = message, cause = this)

    else -> AppError.Unexpected(serverMessage = message, cause = this)
}
