package com.parlor.games.lastlight.protocol

import com.parlor.engine.state.Player
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.immutableSnapshot
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.state.LastLightHostOnly
import com.parlor.games.lastlight.domain.state.LastLightObservableStateValidator
import com.parlor.games.lastlight.domain.state.LastLightPrivate
import com.parlor.games.lastlight.domain.state.LastLightPublic
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.domain.state.detached
import com.parlor.networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES
import kotlinx.serialization.Serializable

/**
 * Separate DTOs make it impossible for canonical host fields to be encoded by
 * the peer wire path. The private payload contains one already-selected hand.
 */
object LastLightProjectionCodec {
    private val wire = LastLightWireFormat()

    fun encodePublic(state: LastLightState): ByteArray {
        val redacted = LastLightProjectionPolicy.toPublic(state).state
        require(LastLightPeerSnapshotValidator.isValidPublic(redacted)) { "Invalid Last Light public projection" }
        return wire.encode(
            PublicEnvelope.serializer(),
            PublicEnvelope(public = redacted.public, phase = redacted.phase, players = redacted.players),
            MAX_SNAPSHOT_PAYLOAD_BYTES,
        )
    }

    fun decodePublic(payload: ByteArray): LastLightState {
        val envelope = wire.decode(PublicEnvelope.serializer(), payload, MAX_SNAPSHOT_PAYLOAD_BYTES)
        require(envelope.schemaVersion == LastLightWireFormat.SCHEMA_VERSION) { "Unsupported Last Light projection version" }
        val state = LastLightState(
            public = envelope.public.detached(),
            privatePerPlayer = emptyMap(),
            hostOnly = LastLightHostOnly.Redacted,
            phase = envelope.phase,
            players = envelope.players.immutableSnapshot(),
        )
        require(LastLightPeerSnapshotValidator.isValidPublic(state)) { "Invalid Last Light public projection" }
        return state
    }

    fun encodePrivate(private: LastLightPrivate): ByteArray {
        requireValidPrivate(private)
        return wire.encode(PrivateEnvelope.serializer(), PrivateEnvelope(private = private), MAX_SNAPSHOT_PAYLOAD_BYTES)
    }

    fun decodePrivate(payload: ByteArray): LastLightPrivate {
        val envelope = wire.decode(PrivateEnvelope.serializer(), payload, MAX_SNAPSHOT_PAYLOAD_BYTES)
        require(envelope.schemaVersion == LastLightWireFormat.SCHEMA_VERSION) { "Unsupported Last Light private version" }
        requireValidPrivate(envelope.private)
        return envelope.private.copy(hand = envelope.private.hand.immutableSnapshot())
    }

    private fun requireValidPrivate(private: LastLightPrivate) {
        require(private.hand.size <= LastLightRules.HAND_SIZE) { "Invalid Last Light private hand size" }
        require(private.hand.isEmpty() || (1..LastLightRules.MAX_ROUNDS).any { round ->
            LastLightObservableStateValidator.areValidCards(private.hand, round)
        }) { "Invalid Last Light private cards" }
    }

    @Serializable
    private data class PublicEnvelope(
        val schemaVersion: Int = LastLightWireFormat.SCHEMA_VERSION,
        val public: LastLightPublic,
        val phase: GamePhase,
        val players: List<Player>,
    )

    @Serializable
    private data class PrivateEnvelope(
        val schemaVersion: Int = LastLightWireFormat.SCHEMA_VERSION,
        val private: LastLightPrivate,
    )
}
