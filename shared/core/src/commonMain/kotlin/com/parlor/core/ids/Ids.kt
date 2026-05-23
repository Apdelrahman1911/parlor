package com.parlor.core.ids

import kotlinx.serialization.Serializable

/**
 * Typed identifier value classes. Prevents accidental mixing — passing a PlayerId
 * where a CaseId is expected fails at compile time.
 */

@JvmInline
@Serializable
value class GameId(val raw: String) {
    init { require(raw.isNotBlank()) { "GameId must not be blank" } }
}

@JvmInline
@Serializable
value class ModeId(val raw: String) {
    init { require(raw.isNotBlank()) { "ModeId must not be blank" } }
}

@JvmInline
@Serializable
value class PlayerId(val raw: String) {
    init { require(raw.isNotBlank()) { "PlayerId must not be blank" } }
}

@JvmInline
@Serializable
value class SessionId(val raw: String) {
    init { require(raw.isNotBlank()) { "SessionId must not be blank" } }
}

@JvmInline
@Serializable
value class CaseId(val raw: String) {
    init { require(raw.isNotBlank()) { "CaseId must not be blank" } }
}

@JvmInline
@Serializable
value class CharacterId(val raw: String) {
    init { require(raw.isNotBlank()) { "CharacterId must not be blank" } }
}

@JvmInline
@Serializable
value class ClueId(val raw: String) {
    init { require(raw.isNotBlank()) { "ClueId must not be blank" } }
}
