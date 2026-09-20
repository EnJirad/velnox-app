package com.velnox.core.common.error

/**
 * Transport-agnostic failure model.
 *
 * This mirrors the error envelope the Velnox backend actually returns
 * (`{ success: false, error: { code, message } }`) without leaking Retrofit,
 * OkHttp or serialization types into the presentation layer.
 *
 * [serverCode] is the backend's machine-readable `error.code`
 * (e.g. `UNAUTHORIZED`, `VALIDATION_ERROR`, `FORBIDDEN`) and [serverMessage] is
 * its human-readable message, which the backend already localises. Neither is
 * ever fabricated: when the backend does not supply them they stay `null` and
 * the UI falls back to its own copy.
 */
sealed class AppError(
    open val serverCode: String? = null,
    open val serverMessage: String? = null,
    cause: Throwable? = null,
) : Exception(serverMessage ?: serverCode, cause) {

    /** Device has no usable connection, or DNS/connect failed outright. */
    data class Offline(
        override val serverMessage: String? = null,
        override val cause: Throwable? = null,
    ) : AppError(serverCode = "OFFLINE", serverMessage = serverMessage, cause = cause)

    /** Socket connected but the exchange exceeded the configured timeout. */
    data class Timeout(
        override val serverMessage: String? = null,
        override val cause: Throwable? = null,
    ) : AppError(serverCode = "TIMEOUT", serverMessage = serverMessage, cause = cause)

    /** HTTP 401 — the session is missing, expired or revoked. */
    data class Unauthorized(
        override val serverCode: String? = "UNAUTHORIZED",
        override val serverMessage: String? = null,
    ) : AppError(serverCode, serverMessage)

    /** HTTP 403 — authenticated but not allowed (role, ownership, moderation). */
    data class Forbidden(
        override val serverCode: String? = "FORBIDDEN",
        override val serverMessage: String? = null,
    ) : AppError(serverCode, serverMessage)

    /** HTTP 404. */
    data class NotFound(
        override val serverCode: String? = "NOT_FOUND",
        override val serverMessage: String? = null,
    ) : AppError(serverCode, serverMessage)

    /** HTTP 400/422 — the backend rejected the payload. */
    data class Validation(
        override val serverCode: String? = "VALIDATION_ERROR",
        override val serverMessage: String? = null,
    ) : AppError(serverCode, serverMessage)

    /** HTTP 409 — optimistic/concurrent write lost, or a duplicate exists. */
    data class Conflict(
        override val serverCode: String? = "CONFLICT",
        override val serverMessage: String? = null,
    ) : AppError(serverCode, serverMessage)

    /** HTTP 429 — rate limited. Retrying immediately makes it worse. */
    data class RateLimited(
        override val serverCode: String? = "RATE_LIMITED",
        override val serverMessage: String? = null,
    ) : AppError(serverCode, serverMessage)

    /** Any other non-2xx response. */
    data class Server(
        val httpStatus: Int,
        override val serverCode: String? = null,
        override val serverMessage: String? = null,
    ) : AppError(serverCode, serverMessage)

    /** The body did not match the contract we compiled against. */
    data class Serialization(
        override val serverMessage: String? = null,
        override val cause: Throwable? = null,
    ) : AppError(serverCode = "SERIALIZATION_ERROR", serverMessage = serverMessage, cause = cause)

    /** Last resort. Never carries credentials or raw bodies. */
    data class Unexpected(
        override val serverMessage: String? = null,
        override val cause: Throwable? = null,
    ) : AppError(serverCode = "INTERNAL_ERROR", serverMessage = serverMessage, cause = cause)

    /** True when retrying the same idempotent request has a real chance. */
    val isRetryable: Boolean
        get() = when (this) {
            is Offline, is Timeout, is RateLimited -> true
            is Server -> httpStatus >= 500
            else -> false
        }

    /** True when the session must be torn down and the user re-authenticated. */
    val isSessionInvalid: Boolean
        get() = this is Unauthorized
}

/** HTTP status → [AppError], keeping the backend's own code/message. */
fun appErrorFromHttp(
    httpStatus: Int,
    serverCode: String?,
    serverMessage: String?,
): AppError = when (httpStatus) {
    400, 422 -> AppError.Validation(serverCode, serverMessage)
    401 -> AppError.Unauthorized(serverCode, serverMessage)
    403 -> AppError.Forbidden(serverCode, serverMessage)
    404 -> AppError.NotFound(serverCode, serverMessage)
    409 -> AppError.Conflict(serverCode, serverMessage)
    429 -> AppError.RateLimited(serverCode, serverMessage)
    else -> AppError.Server(httpStatus, serverCode, serverMessage)
}
