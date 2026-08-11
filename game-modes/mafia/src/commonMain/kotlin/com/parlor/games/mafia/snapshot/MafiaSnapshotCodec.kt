package com.parlor.games.mafia.snapshot

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.mafia.domain.state.MafiaObservableStateValidator
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES
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
        MafiaObservableStateValidator.requireValid(state)
        require(state.isValidRecoveryState()) { "Mafia snapshot state is not reducer-reachable" }
        return strictJson
            .encodeToString(MafiaState.serializer(), state)
            .encodeToByteArray()
            .also(::requireValidPayloadSize)
    }

    override fun decode(payload: ByteArray): MafiaState {
        requireValidPayloadSize(payload)
        val decoded = strictJson
            .decodeFromString(MafiaState.serializer(), payload.decodeToString(throwOnInvalidSequence = true))
        val canonical = strictJson
            .encodeToString(MafiaState.serializer(), decoded)
            .encodeToByteArray()
        require(payload.contentEquals(canonical)) { "Mafia snapshot payload is not canonical" }
        MafiaObservableStateValidator.requireValid(decoded)
        require(decoded.isValidRecoveryState()) { "Mafia snapshot state is not reducer-reachable" }
        return decoded
    }

    private fun requireValidPayloadSize(payload: ByteArray) {
        require(payload.size <= MAX_SNAPSHOT_PAYLOAD_BYTES) {
            "Mafia snapshot exceeds $MAX_SNAPSHOT_PAYLOAD_BYTES bytes"
        }
    }
}
