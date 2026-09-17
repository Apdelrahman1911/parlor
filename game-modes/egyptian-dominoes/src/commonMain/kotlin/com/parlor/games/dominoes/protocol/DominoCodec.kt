package com.parlor.games.dominoes.protocol

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoActionValidation
import com.parlor.games.dominoes.domain.DominoAuthority
import com.parlor.games.dominoes.domain.DominoHostOnly
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoPrivate
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoPublic
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.domain.DominoValidation
import kotlinx.serialization.Serializable

/** Separate DTOs cannot accidentally serialize host buckets. Every payload is bounded and canonical. */
object DominoCodec {
    private val wire = DominoWireFormat()
    private const val MAX_COMMAND_BYTES = 2048
    private const val MAX_PROJECTION_BYTES = 32768

    fun encodeAction(action: DominoAction): ByteArray {
        require(DominoAuthority.isPlayerAction(action) && DominoActionValidation.valid(action))
        return wire.encode(ActionEnvelope.serializer(), ActionEnvelope(action = action), MAX_COMMAND_BYTES)
    }

    fun decodeAction(bytes: ByteArray): DominoAction {
        val envelope = wire.decode(ActionEnvelope.serializer(), bytes, MAX_COMMAND_BYTES)
        require(envelope.version == 1 && DominoAuthority.isPlayerAction(envelope.action) && DominoActionValidation.valid(envelope.action))
        return envelope.action
    }

    fun encodePublic(state: DominoState): ByteArray {
        val safe = DominoProjection.toPublic(state).state
        require(DominoValidation.publicState(safe)) { "Invalid public game state" }
        return wire.encode(PublicEnvelope.serializer(), PublicEnvelope(public = safe.public, phase = safe.phase, players = safe.players),
            MAX_PROJECTION_BYTES)
    }

    fun decodePublic(bytes: ByteArray): DominoState {
        val envelope = wire.decode(PublicEnvelope.serializer(), bytes, MAX_PROJECTION_BYTES)
        require(envelope.version == 1)
        val state = DominoState(envelope.public, emptyMap(), DominoHostOnly(), envelope.phase, envelope.players)
        require(DominoValidation.publicState(state)) { "Invalid public game state" }
        return state
    }

    fun encodePrivate(state: DominoState, id: PlayerId): ByteArray {
        val own = DominoProjection.toPlayer(state, id).state
        require(DominoValidation.playerState(own, id)) { "Invalid private game state" }
        return wire.encode(PrivateEnvelope.serializer(),
            PrivateEnvelope(playerId = id, private = own.privatePerPlayer.getValue(id)), MAX_PROJECTION_BYTES)
    }

    fun decodePlayer(public: DominoState, bytes: ByteArray, id: PlayerId): DominoState {
        require(public == DominoProjection.toPublic(public).state) { "Public projection required" }
        val envelope = wire.decode(PrivateEnvelope.serializer(), bytes, MAX_PROJECTION_BYTES)
        require(envelope.version == 1 && envelope.playerId == id) { "Private snapshot recipient mismatch" }
        val state = public.copy(privatePerPlayer = mapOf(id to envelope.private))
        require(DominoValidation.playerState(state, id)) { "Invalid private game state" }
        return state
    }

    @Serializable private data class ActionEnvelope(val version: Int = 1, val action: DominoAction)
    @Serializable private data class PublicEnvelope(
        val version: Int = 1, val public: DominoPublic, val phase: DominoPhase, val players: List<Player>,
    )
    @Serializable private data class PrivateEnvelope(val version: Int = 1, val playerId: PlayerId, val private: DominoPrivate)
}
