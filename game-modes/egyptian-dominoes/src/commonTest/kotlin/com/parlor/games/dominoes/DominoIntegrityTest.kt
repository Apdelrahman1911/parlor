package com.parlor.games.dominoes

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoPrivate
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoValidation
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.protocol.DominoCodec
import com.parlor.games.dominoes.protocol.DominoSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DominoIntegrityTest {
    @Test
    fun deeply_nested_input_is_rejected_before_recursive_decoding() {
        val payload = ("[".repeat(400) + "0" + "]".repeat(400)).encodeToByteArray()
        val failure = assertFailsWith<IllegalArgumentException> { DominoCodec.decodeAction(payload) }
        assertEquals("JSON nesting exceeds the supported limit", failure.message)
        assertFailsWith<IllegalArgumentException> { DominoCodec.decodePublic(payload) }
        assertFailsWith<IllegalArgumentException> { DominoSnapshotCodec().decode(payload) }
    }

    @Test
    fun opening_snapshot_must_require_the_highest_double_in_the_leaders_own_hand() {
        val players = List(4) { Player(PlayerId("p$it"), "Player $it", it) }
        val reducer = DominoReducer()
        val state = (1L..100L).map { reducer.initial(players, DominoSettings(), it) }.first { state ->
            state.privatePerPlayer.getValue(checkNotNull(state.public.turn)).hand.count { it.isDouble } > 1
        }
        val leader = checkNotNull(state.public.turn)
        val own = DominoProjection.toPlayer(state, leader).state
        assertTrue(DominoValidation.playerState(own, leader))
        val private = own.privatePerPlayer.getValue(leader)
        val wrong = private.hand.filter { it.isDouble }.minBy { it.high }
        val forged = own.copy(privatePerPlayer = mapOf(leader to private.copy(requiredOpening = wrong)))
        assertFalse(DominoValidation.playerState(forged, leader))
        assertFailsWith<IllegalArgumentException> { DominoCodec.encodePrivate(forged, leader) }
    }

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
