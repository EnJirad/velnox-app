package com.velnox.core.auth.signin

import java.security.SecureRandom

/**
 * The OIDC `nonce` for a single Credential Manager request.
 *
 * A nonce binds the Google ID token to *this* sign-in attempt: Google copies the value
 * into the token's `nonce` claim, and `POST /api/auth/native/google` rejects a token
 * whose claim does not match the nonce sent with it. Without one, any Google ID token
 * minted for the Velnox web client id — from any context — stays acceptable to the
 * backend for its whole lifetime, which is a replay window nobody needs.
 *
 * 32 bytes from [SecureRandom], hex-encoded. Hex rather than base64 deliberately:
 * `android.util.Base64` is a stub in JVM unit tests and `java.util.Base64` needs API 26
 * while this module supports API 24, so a pure-Kotlin encoding keeps the value testable
 * and lint-clean. `UUID.randomUUID()` is not used — it is not documented as
 * cryptographically secure, and this value is the only thing tying a token to one
 * attempt.
 *
 * Hex is safe in every context the value travels through: a JSON body, Google's
 * redirect/credential request, and the token's `nonce` claim.
 */
object GoogleNonce {

    /** 256 bits, comfortably above the 128-bit floor OIDC recommends for a nonce. */
    private const val NONCE_BYTES = 32

    private val secureRandom = SecureRandom()

    /** A fresh nonce. Never reused, and never derived from device or user data. */
    fun generate(): String {
        val bytes = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(digits[value ushr 4])
            out.append(digits[value and 0x0F])
        }
        return out.toString()
    }
}
