package com.velnox.core.network.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Timestamp codec that accepts **both** shapes the Velnox backend emits.
 *
 * `packages/shared/src/lib/commerce.ts` documents the situation explicitly:
 * "accept both ISO strings (backend-derived values) and Unix ms numbers
 * (Neon timestamptz serialized to ms)". A strict `Long` field would crash on the
 * other shape, which is exactly the kind of drift that makes a mobile client
 * brittle across backend deploys.
 *
 * Accepts, in order:
 *  * JSON number → epoch milliseconds (values below [SECONDS_THRESHOLD] are
 *    treated as epoch *seconds*, which only affects dates before 1973 and makes
 *    a seconds-vs-millis backend regression harmless instead of catastrophic).
 *  * JSON string that is numeric → same rule.
 *  * ISO-8601 / RFC-1123 string → parsed to epoch milliseconds.
 *  * `null` or anything unparseable → [FALLBACK] (never throws).
 *
 * The serializer is declared non-nullable on purpose: kotlinx.serialization wraps
 * it in a nullable serializer for `Long?` properties, and handles JSON `null`
 * before delegating here.
 */
object FlexibleEpochMillisSerializer : KSerializer<Long> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.velnox.FlexibleEpochMillis", PrimitiveKind.LONG)

    /** Below this, a numeric timestamp is far more likely to be seconds. */
    private const val SECONDS_THRESHOLD = 100_000_000_000L

    /**
     * Sentinel for "absent". Deliberately `0` rather than a guessed "now":
     * fabricating a timestamp would make an unparsable response look like a
     * freshly created record.
     */
    const val FALLBACK: Long = 0L

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> FALLBACK
            is JsonPrimitive -> parse(element)
            else -> FALLBACK
        }
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)

    private fun parse(primitive: JsonPrimitive): Long {
        val raw = primitive.content.trim()
        if (raw.isEmpty()) return FALLBACK

        raw.toLongOrNull()?.let { return normalizeEpoch(it) }
        raw.toDoubleOrNull()?.let { return normalizeEpoch(it.toLong()) }

        return parseIso(raw)
    }

    private fun normalizeEpoch(value: Long): Long {
        if (value <= 0L) return FALLBACK
        return if (value < SECONDS_THRESHOLD) value * 1000L else value
    }

    private fun parseIso(raw: String): Long = try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        // PostgreSQL `timestamptz` without a zone suffix, e.g. "2026-09-20 05:13:00"
        runCatching {
            java.time.LocalDateTime.parse(raw.replace(' ', 'T'))
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(FALLBACK)
    }
}

/**
 * Decimal codec for money and quantities.
 *
 * Required because `node-postgres` returns `NUMERIC`/`DECIMAL` columns as
 * **strings** (`"1234.50"`), while computed aggregates often come back as JSON
 * numbers. Without this, half the price fields in the catalogue would fail to
 * deserialize depending on which SQL path produced them.
 */
object FlexibleDoubleSerializer : KSerializer<Double> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.velnox.FlexibleDouble", PrimitiveKind.DOUBLE)

    const val FALLBACK: Double = 0.0

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> FALLBACK
            is JsonPrimitive -> element.content.trim().toDoubleOrNull() ?: FALLBACK
            else -> FALLBACK
        }
    }

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

/** Integer codec tolerating `"5"` and `5.0`, used for counts and quantities. */
object FlexibleIntSerializer : KSerializer<Int> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.velnox.FlexibleInt", PrimitiveKind.INT)

    const val FALLBACK: Int = 0

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> FALLBACK
            is JsonPrimitive -> element.content.trim().let { raw ->
                raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt() ?: FALLBACK
            }
            else -> FALLBACK
        }
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** Boolean codec tolerating `"true"`, `"1"`, `1`. */
object FlexibleBooleanSerializer : KSerializer<Boolean> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.velnox.FlexibleBoolean", PrimitiveKind.BOOLEAN)

    const val FALLBACK: Boolean = false

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> FALLBACK
            is JsonPrimitive -> when {
                element.isString -> element.content.trim().lowercase() in setOf("true", "1", "yes")
                else -> element.content.trim() != "0" && element.content.trim().toBoolean()
            }
            else -> FALLBACK
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}
