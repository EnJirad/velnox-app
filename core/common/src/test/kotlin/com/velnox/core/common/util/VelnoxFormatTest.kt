package com.velnox.core.common.util

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.Test

/**
 * Formatting is shared by every screen of all three apps, so a change here is visible
 * everywhere. These tests pin the rules the web clients already follow
 * (`formatBaht` in `packages/shared/src/lib/commerce.ts`).
 *
 * Grouping separators are asserted by containment rather than by an exact locale string:
 * the number is formatted with the Thai locale, and the test should fail if the *value*
 * is wrong, not because a JVM shipped slightly different locale data.
 */
class VelnoxFormatTest {

    @Test
    fun `baht prefixes the currency and drops pointless decimals`() {
        VelnoxFormat.baht(0.0) shouldBe "฿0"
        VelnoxFormat.baht(100.0) shouldBe "฿100"
        VelnoxFormat.baht(12.5) shouldBe "฿12.5"
    }

    @Test
    fun `baht rounds to at most two decimals`() {
        VelnoxFormat.baht(19.999) shouldBe "฿20"
        VelnoxFormat.baht(19.994) shouldBe "฿19.99"
    }

    @Test
    fun `baht groups thousands`() {
        val formatted = VelnoxFormat.baht(1234567.0)
        formatted shouldStartWith "฿1"
        formatted shouldEndWith "567"
        formatted shouldNotBe "฿1234567"
    }

    @Test
    fun `the Long overload formats the same amount as the Double one`() {
        VelnoxFormat.baht(1234L) shouldBe VelnoxFormat.baht(1234.0)
    }

    @Test
    fun `a missing timestamp renders the placeholder, never a wrong date`() {
        VelnoxFormat.date(null) shouldBe VelnoxFormat.PLACEHOLDER
        VelnoxFormat.dateTime(null) shouldBe VelnoxFormat.PLACEHOLDER
    }

    @Test
    fun `a real timestamp renders a formatted date and time`() {
        // 2024-03-01T00:00:00Z. The rendered day depends on the device time zone and the
        // month name on the locale, so the assertions are about shape: a real value must
        // produce a real date, and the time form must carry a clock.
        val epochMillis = 1_709_251_200_000L

        val date = VelnoxFormat.date(epochMillis)
        date shouldNotBe VelnoxFormat.PLACEHOLDER
        date.isBlank() shouldBe false

        val dateTime = VelnoxFormat.dateTime(epochMillis)
        dateTime shouldNotBe VelnoxFormat.PLACEHOLDER
        dateTime shouldContain ":"
    }

    @Test
    fun `order numbers are shortened by removing the ORD prefix`() {
        VelnoxFormat.shortOrderNumber("ORD-1234") shouldBe "1234"
        // A backend that stops prefixing must not have its number mangled.
        VelnoxFormat.shortOrderNumber("1234") shouldBe "1234"
    }

    @Test
    fun `discount percent is only reported for a real reduction`() {
        VelnoxFormat.discountPercent(price = 80.0, compareAtPrice = 100.0) shouldBe 20
        VelnoxFormat.discountPercent(price = 50.0, compareAtPrice = 200.0) shouldBe 75
    }

    @Test
    fun `discount percent refuses to invent a reduction`() {
        VelnoxFormat.discountPercent(price = 100.0, compareAtPrice = null) shouldBe null
        VelnoxFormat.discountPercent(price = 100.0, compareAtPrice = 100.0) shouldBe null
        // A compare-at price below the selling price is bad data, not a negative discount.
        VelnoxFormat.discountPercent(price = 100.0, compareAtPrice = 80.0) shouldBe null
        VelnoxFormat.discountPercent(price = 100.0, compareAtPrice = 0.0) shouldBe null
    }

    @Test
    fun `discount percent is clamped to a sane badge value`() {
        // A 99.9% reduction would round to 100 in an integer percent; the badge caps at
        // 99 so it can never read as "free" when the item is not.
        VelnoxFormat.discountPercent(price = 0.01, compareAtPrice = 100.0) shouldBe 99
    }
}
