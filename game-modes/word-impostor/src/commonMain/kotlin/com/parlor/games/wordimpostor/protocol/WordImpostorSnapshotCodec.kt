package com.parlor.games.wordimpostor.protocol

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordImpostorValidation
import kotlinx.serialization.Serializable

/** Authority-only persistence. A complete deterministic replay refuses reducer-impossible states. */
class WordImpostorSnapshotCodec : SnapshotCodec<WordImpostorState> {
    private val wire = WordImpostorWireFormat()
    override fun encode(state: WordImpostorState): ByteArray {
        requireCanonical(state)
        return wire.encode(Envelope.serializer(), Envelope(state = state), MAX_BYTES)
    }
    override fun decode(payload: ByteArray): WordImpostorState {
        val decoded = wire.decode(Envelope.serializer(), payload, MAX_BYTES)
        require(decoded.version == 1)
        requireCanonical(decoded.state)
        return decoded.state
    }
    private fun requireCanonical(state: WordImpostorState) {
        require(WordImpostorValidation.publicState(state)) { "Invalid authority state" }
        require(state.hostOnly.history.size <= WordImpostorReducer.MAX_HISTORY)
        val reducer = WordImpostorReducer()
        var replay = reducer.initial(state.players, state.public.settings, requireNotNull(state.hostOnly.seed), state.hostOnly.firstToken)
        state.hostOnly.history.forEach { action ->
            val next = reducer.apply(replay, action)
            require(next != replay) { "Invalid authority history" }
            replay = next
        }
        require(replay == state) { "Authority state does not match its replay proof" }
    }
    @Serializable private data class Envelope(val version: Int = 1, val state: WordImpostorState)
    companion object { private const val MAX_BYTES = 1_048_576 }
}
