package com.parlor.games.dominoes.protocol

import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.domain.DominoValidation
import kotlinx.serialization.Serializable

/** Authority-only persistence. A complete deterministic replay refuses reducer-impossible states. */
class DominoSnapshotCodec : SnapshotCodec<DominoState> {
    private val wire = DominoWireFormat()
    override fun encode(state: DominoState): ByteArray {
        requireCanonical(state)
        return wire.encode(Envelope.serializer(), Envelope(state = state), MAX_BYTES)
    }
    override fun decode(payload: ByteArray): DominoState {
        val decoded = wire.decode(Envelope.serializer(), payload, MAX_BYTES)
        require(decoded.version == 1)
        requireCanonical(decoded.state)
        return decoded.state
    }
    private fun requireCanonical(state: DominoState) {
        require(DominoValidation.publicState(state)) { "Invalid authority state" }
        require(state.hostOnly.history.size <= DominoRules.MAX_HISTORY)
        val reducer = DominoReducer()
        var replay = reducer.initial(state.players, state.public.settings, requireNotNull(state.hostOnly.seed), state.hostOnly.firstToken)
        state.hostOnly.history.forEach { action ->
            val next = reducer.apply(replay, action)
            require(next != replay) { "Invalid authority history" }
            replay = next
        }
        require(replay == state) { "Authority state does not match its replay proof" }
    }
    @Serializable private data class Envelope(val version: Int = 1, val state: DominoState)
    companion object {
        // 8,192 bounded replay entries may contain 64-character multibyte IDs.
        // This authority-only ceiling is separate from the 32 KiB peer projection limit.
        private const val MAX_BYTES = 3_145_728
    }
}
