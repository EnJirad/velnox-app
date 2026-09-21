package com.velnox.core.auth.signin

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.Test

/**
 * The nonce is the only thing binding a Google ID token to one sign-in attempt, so its
 * shape and its uniqueness are worth asserting rather than assuming.
 */
class GoogleNonceTest {

    @Test
    fun `is 32 bytes of lowercase hex`() {
        GoogleNonce.generate() shouldMatch Regex("^[0-9a-f]{64}$")
    }

    @Test
    fun `is safe to carry in JSON and in a URL`() {
        val nonce = GoogleNonce.generate()
        // No padding, no '+', no '/': the value goes into a JSON body and into Google's
        // credential request, and must not need escaping in either.
        nonce shouldMatch Regex("^[A-Za-z0-9_-]+$")
    }

    @Test
    fun `is never reused`() {
        val nonces = List(500) { GoogleNonce.generate() }
        nonces.toSet().size shouldBe nonces.size
    }

    @Test
    fun `does not collapse to a constant prefix`() {
        // A generator that returned a constant would still pass a uniqueness test on a
        // single call, so compare the first bytes of two draws as well.
        val first = GoogleNonce.generate().take(8)
        val second = GoogleNonce.generate().take(8)
        (first == second) shouldBe false
    }
}
