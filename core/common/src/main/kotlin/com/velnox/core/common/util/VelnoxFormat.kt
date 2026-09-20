package com.velnox.core.common.util

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatting that matches the Velnox web clients exactly.
 *
 * Web formats money with `toLocaleString("th-TH")` and prefixes `฿`
 * (`formatBaht` in `packages/shared/src/lib/commerce.ts`). Dates render in the
 * device time zone. Amounts are always THB — Velnox has a single currency, and
 * inventing per-shop currency handling here would diverge from the backend.
 */
object VelnoxFormat {

    private val bahtFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("th", "TH")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th", "TH"))
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale("th", "TH"))

    /** `1234.5` → `฿1,234.5`. */
    fun baht(amount: Double): String = "฿" + bahtFormat.format(amount)

    fun baht(amount: Long): String = baht(amount.toDouble())

    /** Epoch milliseconds (Neon `timestamptz` as sent by the backend) → local date. */
    fun date(epochMillis: Long?): String = epochMillis
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateFormatter) }
        ?: PLACEHOLDER

    fun dateTime(epochMillis: Long?): String = epochMillis
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateTimeFormatter) }
        ?: PLACEHOLDER

    /** `ORD-1234` → `1234`, mirroring `shortOrderNumber` on web. */
    fun shortOrderNumber(orderNumber: String): String = orderNumber.removePrefix("ORD-")

    /**
     * Percent off badge value, or `null` when there is no real discount.
     * Guards against a `compareAtPrice` at or below the selling price.
     */
    fun discountPercent(price: Double, compareAtPrice: Double?): Int? {
        val compare = compareAtPrice ?: return null
        if (compare <= price || compare <= 0.0) return null
        return (((compare - price) / compare) * 100).toInt().coerceIn(1, 99)
    }

    const val PLACEHOLDER = "—"
}
