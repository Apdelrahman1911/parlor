package com.parlor.games.mafia.snapshot

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.mafia.domain.state.MafiaObservableStateValidator
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.withBoundedHostLogs
import kotlinx.serialization.json.Json

/**
 * kotlinx.serialization-based codec for [MafiaState]. Module-local so adding
 * fields only versions the Mafia snapshot format, not the engine's.
 */
class MafiaSnapshotCodec(
    private val json: Json,
) : SnapshotCodec<MafiaState> {

    /**
     * Snapshot payloads are persisted input. A caller's permissive Json
     * configuration must not allow unknown fields or malformed current state
     * to cross the storage boundary unnoticed.
     */
    private val strictJson = Json(json) {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    override fun encode(state: MafiaState): ByteArray {
        val bounded = state.withBoundedHostLogs()
        MafiaObservableStateValidator.requireValid(bounded)
        return strictJson
            .encodeToString(MafiaState.serializer(), bounded)
            .encodeToByteArray()
    }

    override fun decode(payload: ByteArray): MafiaState {
        val decoded = strictJson
            .decodeFromString(MafiaState.serializer(), payload.decodeToString(throwOnInvalidSequence = true))
            .withBoundedHostLogs()
        MafiaObservableStateValidator.requireValid(decoded)
        return decoded
    }
}
