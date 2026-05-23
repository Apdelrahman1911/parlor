package com.parlor.content.schema

import com.parlor.core.versioning.SemVer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
 * Serializable representation of an int range — JSON-friendly `[min, max]`.
 * Converts to/from Kotlin's [IntRange] via [toIntRange].
 */
@Serializable
data class IntRangePair(val min: Int, val max: Int) {
    init {
        require(min <= max) { "IntRangePair min ($min) must be <= max ($max)" }
    }
    fun toIntRange(): IntRange = min..max

    companion object {
        fun of(range: IntRange): IntRangePair = IntRangePair(range.first, range.last)
    }
}
