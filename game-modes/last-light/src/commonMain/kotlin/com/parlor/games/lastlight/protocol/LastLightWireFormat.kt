package com.parlor.games.lastlight.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Strict canonical JSON refuses unknown fields, duplicate keys, and lenient caller settings. */
internal class LastLightWireFormat(json: Json = Json) {
    private val json = Json(json) {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
        explicitNulls = true
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
    }

    fun <T> encode(serializer: KSerializer<T>, value: T, maximumBytes: Int): ByteArray =
        json.encodeToString(serializer, value).encodeToByteArray().also { requireBounded(it, maximumBytes) }

    fun <T> decode(serializer: KSerializer<T>, payload: ByteArray, maximumBytes: Int): T {
        requireBounded(payload, maximumBytes)
        val decoded = json.decodeFromString(serializer, payload.decodeToString(throwOnInvalidSequence = true))
        val canonical = json.encodeToString(serializer, decoded).encodeToByteArray()
        require(payload.contentEquals(canonical)) { "Last Light payload is not canonical" }
        return decoded
    }

    private fun requireBounded(payload: ByteArray, maximumBytes: Int) {
        require(payload.isNotEmpty() && payload.size <= maximumBytes) { "Last Light payload size is invalid" }
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
