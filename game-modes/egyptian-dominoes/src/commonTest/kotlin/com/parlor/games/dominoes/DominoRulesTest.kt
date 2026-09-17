package com.parlor.games.dominoes

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoAuthority
import com.parlor.games.dominoes.domain.DominoEnd
import com.parlor.games.dominoes.domain.DominoHostOnly
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.domain.DominoTile
import com.parlor.games.dominoes.domain.DominoValidation
import com.parlor.games.dominoes.domain.DominoVariant
import com.parlor.games.dominoes.protocol.DominoCodec
import com.parlor.games.dominoes.protocol.DominoSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DominoRulesTest {
    private val reducer = DominoReducer()
    private fun players(count: Int) = List(count) { Player(PlayerId("p$it"), "Player $it", it) }

    @Test fun doubleSixSetAndEverySupportedDealAreDeterministic() {
        assertEquals(28, DominoTile.Set.distinct().size)
        assertEquals(168, DominoTile.Set.sumOf { it.pips })
        assertFails { DominoTile(6, 2) }
        for (count in 2..4) for (variant in DominoVariant.entries) for (seed in 0L..30L) {
            val state = reducer.initial(players(count), DominoSettings(variant), seed)
            assertEquals(state, reducer.initial(players(count), DominoSettings(variant), seed))
            assertEquals(List(count) { 7 }, state.public.handCounts.values.toList())
            val hands = state.privatePerPlayer.values.flatMap { it.hand }
            val highest = hands.filter { it.isDouble }.maxByOrNull { it.high }
                ?: hands.maxWith(compareBy<DominoTile> { it.pips }.thenBy { it.high })
            assertEquals(highest, state.privatePerPlayer.getValue(state.public.turn!!).requiredOpening)
            verify(state)
        }
    }

    @Test fun allPlayerCountsAndVariantsFinishLegalHandsWithConservedTiles() {
        for (count in 2..4) for (variant in DominoVariant.entries) for (seed in 0L..30L) {
            var state = reducer.initial(players(count), DominoSettings(variant, 50), seed)
            var moves = 0
            while (state.phase == DominoPhase.Playing) {
                assertTrue(moves++ < DominoRules.MAX_MOVE)
                val action = legal(state)
                val next = reducer.apply(state, action)
                assertNotEquals(state, next)
                assertEquals(next, reducer.apply(next, action), "An old move must not apply again")
                verify(next)
                state = next
            }
            val result = assertNotNull(state.public.result)
            if (!result.blocked) assertEquals(0, state.public.handCounts[result.winner])
            assertEquals(state, DominoSnapshotCodec().decode(DominoSnapshotCodec().encode(state)))
            if (state.phase == DominoPhase.RoundResult) {
                val next = reducer.apply(state, DominoAction.NextRound(state.public.token))
                assertEquals(state.public.token + 1, next.public.token)
                assertEquals(state.public.scores, next.public.scores)
                assertNull(next.hostOnly.requiredOpening)
                verify(next)
            }
        }
    }

    @Test fun authorityWrongTurnsDrawPassAndOldRoundsAreRejected() {
        val state = reducer.initial(players(3), DominoSettings(), 17L)
        val actor = state.public.turn!!
        val another = state.players.first { it.id != actor }.id
        val action = legal(state)
        assertFalse(DominoAuthority.allowed(action, another, another, state))
        assertFalse(DominoAuthority.allowed(DominoAction.NextRound(1), actor, another, state))
        assertEquals(state, reducer.apply(state, DominoAction.Place(another, 1, 0, state.hostOnly.requiredOpening!!.id, DominoEnd.Right)))
        assertEquals(state, reducer.apply(state, DominoAction.Draw(actor, 1, 0)))
        assertEquals(state, reducer.apply(state, DominoAction.Pass(actor, 1, 0)))
        assertEquals(state, reducer.apply(state, DominoAction.Place(actor, 2, 0, 48, DominoEnd.Right)))
        assertFails { DominoCodec.encodeAction(DominoAction.Disconnected(actor)) }
        assertFails { DominoCodec.encodeAction(DominoAction.Place(actor, 1, 0, 999, DominoEnd.Right)) }
        assertEquals(action, DominoCodec.decodeAction(DominoCodec.encodeAction(action)))
    }

    @Test fun recoveryPausesEverySeatAndAbortDoesNotExposeHandsOrAwardPoints() {
        val state = reducer.initial(players(4), DominoSettings(), 45)
        val missing = state.players.last().id
        val paused = reducer.apply(state, DominoAction.Disconnected(missing))
        assertEquals(paused, reducer.apply(paused, legal(state)))
        val resumed = reducer.apply(paused, DominoAction.Reconnected(missing))
        assertEquals(state.public, resumed.public)
        assertEquals(state.privatePerPlayer, resumed.privatePerPlayer)
        val ended = reducer.apply(paused, DominoAction.Abort)
        assertEquals(DominoPhase.Aborted, ended.phase)
        assertEquals(state.public.scores, ended.public.scores)
        verify(ended)
        assertEquals(ended, DominoSnapshotCodec().decode(DominoSnapshotCodec().encode(ended)))
    }

    @Test fun matchAndRematchAdvanceTokensAndRefuseForgedSnapshots() {
        var state = reducer.initial(players(2), DominoSettings(target = 50), 4)
        while (state.phase != DominoPhase.MatchResult) {
            state = reducer.apply(state, if (state.phase == DominoPhase.Playing) legal(state) else DominoAction.NextRound(state.public.token))
        }
        val next = reducer.apply(state, DominoAction.Rematch(state.public.token))
        assertEquals(state.public.token + 1, next.public.token)
        assertTrue(next.public.scores.values.all { it == 0 })
        assertEquals(next, reducer.apply(next, DominoAction.Rematch(state.public.token)))
        assertEquals(next, DominoSnapshotCodec().decode(DominoSnapshotCodec().encode(next)))
        assertFails { DominoSnapshotCodec().encode(next.copy(hostOnly = next.hostOnly.copy(seed = 99))) }
        val bytes = DominoCodec.encodePublic(next)
        assertFails { DominoCodec.decodePublic(bytes.decodeToString().replace("\"version\":1", "\"version\":2").encodeToByteArray()) }
        assertFails { DominoCodec.decodePublic(bytes.decodeToString().replace("\"version\":1", "\"version\":1,\"version\":1").encodeToByteArray()) }
        assertFails { DominoCodec.decodePublic(ByteArray(32769)) }
    }

    private fun legal(state: DominoState): DominoAction {
        val by = state.public.turn!!
        val tile = state.privatePerPlayer.getValue(by).hand.firstOrNull { DominoRules.playableEnds(state, by, it).isNotEmpty() }
        return when {
            tile != null -> DominoAction.Place(by, state.public.token, state.public.move, tile.id, DominoRules.playableEnds(state, by, tile).first())
            DominoRules.canDraw(state, by) -> DominoAction.Draw(by, state.public.token, state.public.move)
            else -> DominoAction.Pass(by, state.public.token, state.public.move)
        }
    }

    private fun verify(state: DominoState) {
        assertTrue(DominoValidation.publicState(state), "Invalid ${state.phase} / ${state.public.move}")
        val all = state.privatePerPlayer.values.flatMap { it.hand } + state.hostOnly.stock + state.public.chain.map { it.tile }
        assertEquals(DominoTile.Set.toSet(), all.toSet())
        assertEquals(28, all.size)
        val public = DominoCodec.decodePublic(DominoCodec.encodePublic(state))
        assertTrue(public.privatePerPlayer.isEmpty())
        assertEquals(DominoHostOnly(), public.hostOnly)
        state.players.forEach { player ->
            val own = DominoCodec.decodePlayer(public, DominoCodec.encodePrivate(state, player.id), player.id)
            assertEquals(setOf(player.id), own.privatePerPlayer.keys)
            assertEquals(DominoProjection.toPlayer(state, player.id).state, own)
        }
    }
}
