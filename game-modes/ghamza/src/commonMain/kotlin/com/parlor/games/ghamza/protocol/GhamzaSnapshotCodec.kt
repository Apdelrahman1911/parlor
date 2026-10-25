package com.parlor.games.ghamza.protocol

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.domain.GhamzaValidation
import com.parlor.games.ghamza.protocol.GhamzaWireFormat.Companion.SCHEMA_VERSION
import kotlinx.serialization.Serializable

/** Authority-only persistence. A complete deterministic replay refuses reducer-impossible states. */
class GhamzaSnapshotCodec : SnapshotCodec<GhamzaState> {
    private val wire = GhamzaWireFormat()
    override fun encode(state: GhamzaState): ByteArray {
        requireCanonical(state)
        return wire.encode(Envelope.serializer(), Envelope(state = state), MAX_BYTES)
    }
    override fun decode(payload: ByteArray): GhamzaState {
        val decoded = wire.decode(Envelope.serializer(), payload, MAX_BYTES)
        require(decoded.version == SCHEMA_VERSION)
        requireCanonical(decoded.state)
        return decoded.state
    }
    private fun requireCanonical(state: GhamzaState) {
        require(GhamzaValidation.publicState(state)) { "Invalid authority state" }
        require(state.hostOnly.history.size <= GhamzaReducer.MAX_HISTORY)
        val reducer = GhamzaReducer()
        var replay = reducer.initial(state.players, state.public.settings, requireNotNull(state.hostOnly.seed), state.hostOnly.firstToken)
        state.hostOnly.history.forEach { action ->
            val next = reducer.apply(replay, action)
            require(next != replay) { "Invalid authority history" }
            replay = next
        }
        require(replay == state) { "Authority state does not match its replay proof" }
    }
    @Serializable private data class Envelope(val version: Int = SCHEMA_VERSION, val state: GhamzaState)
    companion object { private const val MAX_BYTES = 1_048_576 }
}
