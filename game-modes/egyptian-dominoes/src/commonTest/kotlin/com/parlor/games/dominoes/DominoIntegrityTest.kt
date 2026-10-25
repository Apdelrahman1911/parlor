package com.parlor.games.dominoes

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoPrivate
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.protocol.DominoCodec
import com.parlor.games.dominoes.protocol.DominoSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DominoIntegrityTest {
    @Test
    fun history_exhaustion_ends_without_scoring_or_revealing_and_has_a_valid_replay_proof() {
        // Admission permits 64 UTF-16 units, not merely 64 ASCII bytes.
        val players = List(4) { Player(PlayerId("界".repeat(63) + it), "Player $it", it) }
        val reducer = DominoReducer()
        var state = reducer.initial(players, DominoSettings(), 937)
        val id = players.last().id
        repeat(DominoRules.MAX_HISTORY - 1) { index ->
            state = reducer.apply(state, if (index % 2 == 0) DominoAction.Disconnected(id) else DominoAction.Reconnected(id))
        }
        assertEquals(DominoPhase.Playing, state.phase)
        assertEquals(state, reducer.apply(state, DominoAction.Disconnected(id)))
        assertEquals(DominoRules.MAX_HISTORY - 1, state.hostOnly.history.size)
        state = reducer.apply(state, DominoAction.Reconnected(id))
        assertEquals(DominoPhase.Aborted, state.phase)
        assertEquals(DominoAction.Abort, state.hostOnly.history.last())
        assertTrue(state.public.scores.values.all { it == 0 })
        val codec = DominoSnapshotCodec()
        val encoded = codec.encode(state)
        assertTrue(encoded.size in 1_048_577..3_145_728)
        assertEquals(state, codec.decode(encoded))
        assertEquals(state, reducer.apply(state, DominoAction.Abort))
        val public = DominoCodec.decodePublic(DominoCodec.encodePublic(state))
        players.forEach { player ->
            val payload = DominoCodec.encodePrivate(state, player.id)
            val own = DominoCodec.decodePlayer(public, payload, player.id)
            assertEquals(DominoPrivate(), own.privatePerPlayer.getValue(player.id))
            assertFailsWith<IllegalArgumentException> { DominoCodec.decodePlayer(public, payload, PlayerId("outsider")) }
        }
        assertFailsWith<IllegalArgumentException> { DominoCodec.encodePrivate(state, PlayerId("outsider")) }
    }
}
