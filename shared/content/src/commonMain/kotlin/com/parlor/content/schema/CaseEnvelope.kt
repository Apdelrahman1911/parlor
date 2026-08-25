package com.parlor.content.schema

import com.parlor.core.versioning.SemVer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Generic case envelope per docs/CONTENT_SCHEMA.md §2.
 *
 * The `payload` is game-module-specific — Whodunit defines its `WhodunitCase`
 * payload type and parses the JsonElement on validation.
 */
@Serializable
data class CaseEnvelope(
    val schemaVersion: Int,
    val caseId: String,
    val title: String,
    val subtitle: String? = null,
    val version: SemVer,
    val minimumAppVersion: SemVer,
    val gameId: String,
    val supportedPlayerCounts: IntRangePair,
    val supportedModes: List<String>,
    val language: String,
    val theme: String,
    val estimatedDuration: IntRangePair,
    val payload: JsonElement,
    val signature: String? = null,
    val metadata: JsonElement? = null,
)

/**
 * An int range with a JSON-array wire format — `[min, max]` — per
 * docs/CONTENT_SCHEMA.md §2.1. Stored internally as two Ints; converts to/from
 * Kotlin's [IntRange] via [toIntRange].
 *
 * The custom serializer is required because kotlinx.serialization's auto-
 * generated form would produce `{"min":4,"max":6}` — schema-incompatible.
 */
@Serializable(with = IntRangePair.Serializer::class)
data class IntRangePair(val min: Int, val max: Int) {
    init {
        require(min <= max) { "IntRangePair min ($min) must be <= max ($max)" }
    }
    fun toIntRange(): IntRange = min..max

    companion object {
        fun of(range: IntRange): IntRangePair = IntRangePair(range.first, range.last)
    }

    object Serializer : KSerializer<IntRangePair> {
        override val descriptor: SerialDescriptor =
            ListSerializer(Int.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: IntRangePair) {
            val jsonEncoder = encoder as? JsonEncoder
                ?: throw SerializationException("IntRangePair is JSON-only")
            jsonEncoder.encodeJsonElement(
                JsonArray(listOf(JsonPrimitive(value.min), JsonPrimitive(value.max))),
            )
        }

        override fun deserialize(decoder: Decoder): IntRangePair {
            val jsonDecoder = decoder as? JsonDecoder
                ?: throw SerializationException("IntRangePair is JSON-only")
            val element = jsonDecoder.decodeJsonElement()
            if (element !is JsonArray || element.size != 2) {
                throw SerializationException(
                    "IntRangePair expected a JSON array of two integers; got $element",
                )
            }
            val min = (element[0] as? JsonPrimitive)?.intOrNull
                ?: throw SerializationException("IntRangePair[0] is not an integer")
            val max = (element[1] as? JsonPrimitive)?.intOrNull
                ?: throw SerializationException("IntRangePair[1] is not an integer")
            return IntRangePair(min, max)
        }
    }
}

