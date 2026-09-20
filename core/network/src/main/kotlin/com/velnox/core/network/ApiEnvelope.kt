package com.velnox.core.network

import kotlinx.serialization.Serializable

/**
 * The response envelope every `/api/*` endpoint in the Velnox backend returns.
 *
 * ```
 * { "success": true,  "data": { ... } }
 * { "success": false, "error": { "code": "UNAUTHORIZED", "message": "…" } }
 * ```
 *
 * Two details verified against the real backend and honoured here:
 *
 *  * `data` may be legitimately `null` with `success: true`. `GET /api/seller/status`
 *    returns `data: null` for an authenticated user with no seller application —
 *    that is a meaningful answer, not an error. Use [safeApiCallAllowNull] for
 *    those endpoints; [safeApiCall] treats a null payload as a contract violation.
 *  * `error.message` is already localised by the backend where it matters, so it
 *    is surfaced as-is instead of being replaced by client-side copy.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: ApiErrorBody? = null,
)

@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
)

/** `{ "success": true }` ack with no payload. */
@Serializable
data class ApiAck(val success: Boolean = true)
