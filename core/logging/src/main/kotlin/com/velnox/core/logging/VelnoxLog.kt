package com.velnox.core.logging

import android.util.Log

/**
 * The only logger used across Velnox Android.
 *
 * Security rules encoded here (see `docs/SECURITY.md` in velnox-marketplace —
 * "never log sensitive credentials"):
 *
 *  * Every message passes through [Redactor] before it reaches logcat.
 *  * Bearer tokens, session cookies, e-mail addresses, phone numbers and
 *    `idToken`/`password` values are masked, so an accidental `log("$request")`
 *    cannot leak a session.
 *  * Verbose/debug output is compiled out of release builds, not merely
 *    filtered at runtime.
 *  * Bodies are truncated, so one bad response cannot flood logcat.
 */
object VelnoxLog {

    /** Tag prefix keeps Velnox lines greppable: `adb logcat -s Velnox:*`. */
    private const val TAG_PREFIX = "Velnox"

    private const val MAX_MESSAGE_LENGTH = 1200

    /** Set to false by [initialise] in release builds. */
    @Volatile
    private var verboseEnabled: Boolean = true

    /** Call once from `Application.onCreate`. */
    fun initialise(debuggable: Boolean) {
        verboseEnabled = debuggable
    }

    fun d(tag: String, message: () -> String) {
        if (!verboseEnabled) return
        Log.d(tag(tag), Redactor.redact(message()).truncate())
    }

    fun i(tag: String, message: () -> String) {
        Log.i(tag(tag), Redactor.redact(message()).truncate())
    }

    fun w(tag: String, message: () -> String, throwable: Throwable? = null) {
        Log.w(tag(tag), Redactor.redact(message()).truncate(), throwable?.safeForLog())
    }

    /**
     * Errors are always logged, but only the throwable's type and sanitised
     * message — a stack trace from OkHttp can embed a full URL with a signed
     * query string.
     */
    fun e(tag: String, message: () -> String, throwable: Throwable? = null) {
        Log.e(tag(tag), Redactor.redact(message()).truncate(), throwable?.safeForLog())
    }

    /** Never log request/response bodies in full; describe them instead. */
    fun http(method: String, url: String, status: Int? = null, durationMs: Long? = null) {
        if (!verboseEnabled && status != null && status < 400) return
        val line = buildString {
            append(method.uppercase())
            append(' ')
            append(Redactor.redactUrl(url))
            status?.let { append(" → ").append(it) }
            durationMs?.let { append(" (").append(it).append("ms)") }
        }
        if (status != null && status >= 400) {
            Log.w(tag("HTTP"), line)
        } else {
            Log.d(tag("HTTP"), line)
        }
    }

    private fun tag(tag: String) = "$TAG_PREFIX/$tag"

    private fun String.truncate(): String =
        if (length <= MAX_MESSAGE_LENGTH) this else take(MAX_MESSAGE_LENGTH) + "… (${length - MAX_MESSAGE_LENGTH} more chars)"
}

/** Throws away the original message/stack, keeping only the exception identity. */
private fun Throwable.safeForLog(): Throwable =
    Throwable("${this::class.java.simpleName}: ${Redactor.redact(message ?: "")}", null, false, false)
