package com.velnox.core.realtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Lenient frame reader.
 *
 * Frames are parsed by hand rather than via `@Serializable` classes because the
 * `data` payload differs per event and this build may not know every event the
 * backend emits. An unknown event must be ignored, never fatal.
 */
internal object RealtimeFrameParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): RealtimeFrame? {
        val element: JsonElement = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null

        val type = obj.stringOrNull("type") ?: return null

        return RealtimeFrame(
            type = type,
            channel = obj.stringOrNull("channel"),
            payload = obj["data"]?.let { data ->
                if (data is JsonPrimitive && data.contentOrNull == null) null else data.toString()
            },
            receivedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
