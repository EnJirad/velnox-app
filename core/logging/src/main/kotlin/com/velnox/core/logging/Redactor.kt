package com.velnox.core.logging

/**
 * Pattern-based masking for anything that could reach logcat.
 *
 * Kept deliberately dumb and dependency-free: it runs on every log line, and a
 * redactor that can throw is worse than no redactor at all.
 */
object Redactor {

    private const val MASK = "***"

    private val patterns: List<Pair<Regex, String>> = listOf(
        // Authorization: Bearer <jwt>  /  Cookie: velnox_session=<jwt>
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}") to "$1$MASK",
        Regex("(?i)(velnox_session=)[^;\\s\"']+") to "$1$MASK",
        Regex("(?i)((?:access_token|refresh_token|id_token|token|password|client_secret|api[_-]?key)\"?\\s*[:=]\\s*\"?)[^\",\\s&}]{3,}") to "$1$MASK",
        // JWTs anywhere in a line (three base64url segments)
        Regex("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}") to MASK,
        // Signed R2 / presigned URLs
        Regex("(?i)([?&](?:X-Amz-Signature|X-Amz-Credential|Signature|sig|token)=)[^&\\s\"']+") to "$1$MASK",
        // E-mail addresses (users.email is PII)
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to MASK,
        // Thai mobile numbers
        Regex("(?<!\\d)(?:0[689]\\d{8}|0[2-7]\\d{7})(?!\\d)") to MASK,
    )

    fun redact(input: String): String {
        var output = input
        for ((pattern, replacement) in patterns) {
            output = pattern.replace(output, replacement)
        }
        return output
    }

    /** Redacts the query string but keeps host + path, which are safe and useful. */
    fun redactUrl(url: String): String {
        val queryStart = url.indexOf('?')
        return if (queryStart < 0) url else url.take(queryStart) + "?$MASK"
    }
}
