package com.parlor.games.ghamza.protocol

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaActionValidation
import com.parlor.games.ghamza.domain.GhamzaAuthority
import com.parlor.games.ghamza.domain.GhamzaHostOnly
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaPrivate
import com.parlor.games.ghamza.domain.GhamzaProjection
import com.parlor.games.ghamza.domain.GhamzaPublic
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.domain.GhamzaValidation
import com.parlor.games.ghamza.protocol.GhamzaWireFormat.Companion.SCHEMA_VERSION
import kotlinx.serialization.Serializable

/** Separate DTOs cannot accidentally serialize host buckets. Every payload is bounded and canonical. */
object GhamzaCodec {
    private val wire = GhamzaWireFormat()
    private const val MAX_COMMAND_BYTES = 2048
    private const val MAX_PROJECTION_BYTES = 32768

    fun encodeAction(action: GhamzaAction): ByteArray {
        require(GhamzaAuthority.isPlayerAction(action) && GhamzaActionValidation.valid(action))
        return wire.encode(ActionEnvelope.serializer(), ActionEnvelope(action = action), MAX_COMMAND_BYTES)
    }

    fun decodeAction(bytes: ByteArray): GhamzaAction {
        val envelope = wire.decode(ActionEnvelope.serializer(), bytes, MAX_COMMAND_BYTES)
        require(envelope.version == SCHEMA_VERSION && GhamzaAuthority.isPlayerAction(envelope.action) &&
            GhamzaActionValidation.valid(envelope.action))
        return envelope.action
    }

    fun encodePublic(state: GhamzaState): ByteArray {
        val safe = GhamzaProjection.toPublic(state).state
        require(GhamzaValidation.publicState(safe)) { "Invalid public game state" }
        return wire.encode(PublicEnvelope.serializer(), PublicEnvelope(public = safe.public, phase = safe.phase, players = safe.players),
            MAX_PROJECTION_BYTES)
    }

    fun decodePublic(bytes: ByteArray): GhamzaState {
        val envelope = wire.decode(PublicEnvelope.serializer(), bytes, MAX_PROJECTION_BYTES)
        require(envelope.version == SCHEMA_VERSION)
        val state = GhamzaState(envelope.public, emptyMap(), GhamzaHostOnly(), envelope.phase, envelope.players)
        require(GhamzaValidation.publicState(state)) { "Invalid public game state" }
        return state
    }

    fun encodePrivate(state: GhamzaState, id: PlayerId): ByteArray {
        val own = GhamzaProjection.toPlayer(state, id).state
        require(GhamzaValidation.playerState(own, id)) { "Invalid private game state" }
        return wire.encode(PrivateEnvelope.serializer(),
            PrivateEnvelope(playerId = id, private = own.privatePerPlayer.getValue(id)), MAX_PROJECTION_BYTES)
    }

    fun decodePlayer(public: GhamzaState, bytes: ByteArray, id: PlayerId): GhamzaState {
        require(public == GhamzaProjection.toPublic(public).state) { "Public projection required" }
        val envelope = wire.decode(PrivateEnvelope.serializer(), bytes, MAX_PROJECTION_BYTES)
        require(envelope.version == SCHEMA_VERSION && envelope.playerId == id) { "Private snapshot recipient mismatch" }
        val state = public.copy(privatePerPlayer = mapOf(id to envelope.private))
        require(GhamzaValidation.playerState(state, id)) { "Invalid private game state" }
        return state
    }

    @Serializable private data class ActionEnvelope(val version: Int = SCHEMA_VERSION, val action: GhamzaAction)
    @Serializable private data class PublicEnvelope(
        val version: Int = SCHEMA_VERSION, val public: GhamzaPublic, val phase: GhamzaPhase, val players: List<Player>,
    )
    @Serializable private data class PrivateEnvelope(
        val version: Int = SCHEMA_VERSION, val playerId: PlayerId, val private: GhamzaPrivate,
    )
}
