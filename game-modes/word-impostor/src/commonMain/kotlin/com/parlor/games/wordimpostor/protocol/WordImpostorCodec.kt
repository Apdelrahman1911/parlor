package com.parlor.games.wordimpostor.protocol

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorActionValidation
import com.parlor.games.wordimpostor.domain.WordImpostorAuthority
import com.parlor.games.wordimpostor.domain.WordImpostorHostOnly
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorPrivate
import com.parlor.games.wordimpostor.domain.WordImpostorProjection
import com.parlor.games.wordimpostor.domain.WordImpostorPublic
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordImpostorValidation
import kotlinx.serialization.Serializable

/** Separate DTOs cannot accidentally serialize host buckets. Every payload is bounded and canonical. */
object WordImpostorCodec {
    private val wire = WordImpostorWireFormat()
    private const val MAX_COMMAND_BYTES = 2048
    private const val MAX_PROJECTION_BYTES = 32768

    fun encodeAction(action: WordImpostorAction): ByteArray {
        require(WordImpostorAuthority.isPlayerAction(action) && WordImpostorActionValidation.valid(action))
        return wire.encode(ActionEnvelope.serializer(), ActionEnvelope(action = action), MAX_COMMAND_BYTES)
    }

    fun decodeAction(bytes: ByteArray): WordImpostorAction {
        val envelope = wire.decode(ActionEnvelope.serializer(), bytes, MAX_COMMAND_BYTES)
        require(envelope.version == 1 && WordImpostorAuthority.isPlayerAction(envelope.action) &&
            WordImpostorActionValidation.valid(envelope.action))
        return envelope.action
    }

    fun encodePublic(state: WordImpostorState): ByteArray {
        val safe = WordImpostorProjection.toPublic(state).state
        require(WordImpostorValidation.publicState(safe)) { "Invalid public game state" }
        return wire.encode(PublicEnvelope.serializer(), PublicEnvelope(public = safe.public, phase = safe.phase, players = safe.players),
            MAX_PROJECTION_BYTES)
    }

    fun decodePublic(bytes: ByteArray): WordImpostorState {
        val envelope = wire.decode(PublicEnvelope.serializer(), bytes, MAX_PROJECTION_BYTES)
        require(envelope.version == 1)
        val state = WordImpostorState(envelope.public, emptyMap(), WordImpostorHostOnly(), envelope.phase, envelope.players)
        require(WordImpostorValidation.publicState(state)) { "Invalid public game state" }
        return state
    }

    fun encodePrivate(state: WordImpostorState, id: PlayerId): ByteArray {
        val own = WordImpostorProjection.toPlayer(state, id).state
        require(WordImpostorValidation.playerState(own, id)) { "Invalid private game state" }
        return wire.encode(PrivateEnvelope.serializer(),
            PrivateEnvelope(playerId = id, private = own.privatePerPlayer.getValue(id)), MAX_PROJECTION_BYTES)
    }

    fun decodePlayer(public: WordImpostorState, bytes: ByteArray, id: PlayerId): WordImpostorState {
        require(public == WordImpostorProjection.toPublic(public).state) { "Public projection required" }
        val envelope = wire.decode(PrivateEnvelope.serializer(), bytes, MAX_PROJECTION_BYTES)
        require(envelope.version == 1 && envelope.playerId == id) { "Private snapshot recipient mismatch" }
        val state = public.copy(privatePerPlayer = mapOf(id to envelope.private))
        require(WordImpostorValidation.playerState(state, id)) { "Invalid private game state" }
        return state
    }

    @Serializable private data class ActionEnvelope(val version: Int = 1, val action: WordImpostorAction)
    @Serializable private data class PublicEnvelope(
        val version: Int = 1, val public: WordImpostorPublic, val phase: WordImpostorPhase, val players: List<Player>,
    )
    @Serializable private data class PrivateEnvelope(val version: Int = 1, val playerId: PlayerId, val private: WordImpostorPrivate)
}
