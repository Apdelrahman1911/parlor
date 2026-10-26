package com.parlor.games.ghamza

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaHostOnly
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaProjection
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.protocol.GhamzaCodec
import com.parlor.games.ghamza.protocol.GhamzaSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Reducer/codec fault model, not live transport, credential recovery or native process restoration. */
class GhamzaRecoveryJourneyTest {
    private val reducer = GhamzaReducer()
    private val snapshots = GhamzaSnapshotCodec()

    @Test
    fun ninety_complete_matches_preserve_roles_seats_and_outcomes_after_interruption() {
        for (count in 3..12) for (attempts in 1..3) for (rounds in GhamzaSettings.ROUND_COUNTS) {
            val players = List(count) { Player(PlayerId("p$it"), "Player $it", it) }
            verifyMatch(reducer.initial(players, GhamzaSettings(attempts, rounds), 73))
        }
    }

    private fun verifyMatch(initial: GhamzaState) {
        var clean = initial
        var recovered = initial
        val delivered = mutableListOf<GhamzaAction>()
        val visited = mutableSetOf<GhamzaPhase>()
        var steps = 0
        while (clean.phase != GhamzaPhase.MatchResult) {
            assertTrue(steps++ < 512, "A deterministic complete match must terminate")
            val action = nextAction(clean)
            // The queued bytes are held while seats are absent; no fresh action is invented on return.
            val queued = GhamzaCodec.encodeAction(action)
            if (visited.add(clean.phase) || steps % 7 == 0 || clean.phase == GhamzaPhase.FinalGuess) {
                recovered = interruptAndResume(recovered, GhamzaCodec.decodeAction(queued))
            }
            clean = reducer.apply(clean, action)
            val previous = recovered
            recovered = reducer.apply(recovered, GhamzaCodec.decodeAction(queued))
            assertNotEquals(previous, recovered, "The intended command must actually be accepted")
            delivered += action
            // A lost acknowledgement causes identical redelivery; delayed older commands stay inert too.
            delivered.forEach { assertEquals(recovered, reducer.apply(recovered, it), "Duplicate/stale command changed state") }
            assertEquivalent(clean, recovered)
            assertRecipientViews(recovered)
        }
        assertEquals(initial.public.settings.rounds, recovered.public.round)
        assertEquals(clean.public.result, recovered.public.result)
        assertEquals(recovered, restore(recovered))
        val rematch = GhamzaAction.Rematch(recovered.public.token)
        val next = reducer.apply(recovered, rematch)
        assertEquals(initial.players, next.players)
        assertEquals(reducer.apply(clean, rematch), next)
        delivered.forEach { assertEquals(next, reducer.apply(next, it), "Old match action crossed the rematch token") }
        assertRecipientViews(next)
    }

    private fun interruptAndResume(state: GhamzaState, queued: GhamzaAction): GhamzaState {
        val first = state.players.first().id
        val last = state.players.last().id
        var paused = reducer.apply(state, GhamzaAction.Disconnected(first))
        assertEquals(paused, reducer.apply(paused, GhamzaAction.Disconnected(first)))
        paused = reducer.apply(paused, GhamzaAction.Disconnected(last))
        assertEquals(paused, reducer.apply(paused, queued))
        paused = restore(paused)
        assertRecipientViews(paused)
        paused = reducer.apply(paused, GhamzaAction.Reconnected(last))
        assertEquals(paused, reducer.apply(paused, queued), "One return must not clear the other seat's pause")
        paused = restore(paused)
        val resumed = reducer.apply(paused, GhamzaAction.Reconnected(first))
        assertEquals(resumed, reducer.apply(resumed, GhamzaAction.Reconnected(first)))
        assertEquivalent(state, resumed)
        return resumed
    }

    private fun restore(state: GhamzaState): GhamzaState = snapshots.decode(snapshots.encode(state)).also {
        assertEquals(state, it, "Authority replay must preserve the exact saved state")
    }

    private fun assertEquivalent(expected: GhamzaState, actual: GhamzaState) {
        assertEquals(expected.public, actual.public)
        assertEquals(expected.privatePerPlayer, actual.privatePerPlayer)
        assertEquals(expected.players, actual.players)
        assertEquals(expected.phase, actual.phase)
        assertEquals(expected.hostOnly.copy(history = actual.hostOnly.history), actual.hostOnly)
    }

    private fun assertRecipientViews(state: GhamzaState) {
        val public = GhamzaCodec.decodePublic(GhamzaCodec.encodePublic(state))
        assertEquals(GhamzaHostOnly(), public.hostOnly)
        assertTrue(public.privatePerPlayer.isEmpty())
        state.players.forEach { player ->
            val bytes = GhamzaCodec.encodePrivate(state, player.id)
            val own = GhamzaCodec.decodePlayer(public, bytes, player.id)
            assertEquals(GhamzaProjection.toPlayer(state, player.id).state, own)
            assertEquals(setOf(player.id), own.privatePerPlayer.keys)
            val other = state.players.first { it.id != player.id }.id
            assertFailsWith<IllegalArgumentException> { GhamzaCodec.decodePlayer(public, bytes, other) }
        }
    }

    private fun nextAction(state: GhamzaState): GhamzaAction = when (state.phase) {
        GhamzaPhase.Reveal -> GhamzaAction.Ready(state.players.first { it.id !in state.public.ready }.id, state.public.token)
        GhamzaPhase.Social -> {
            val player = state.players.first {
                it.id != state.hostOnly.winker && state.public.reports.getValue(it.id) < state.public.settings.attempts
            }.id
            GhamzaAction.Winked(player, state.public.token, state.public.reports.getValue(player) + 1)
        }
        GhamzaPhase.FinalGuess -> {
            val guesser = checkNotNull(state.public.finalGuesser)
            val target = if ((state.players.size + state.public.round) % 2 == 0) checkNotNull(state.hostOnly.winker)
                else state.players.first { it.id != guesser && it.id != state.hostOnly.winker }.id
            GhamzaAction.Guess(guesser, state.public.token, target)
        }
        GhamzaPhase.RoundResult -> GhamzaAction.NextRound(state.public.token)
        else -> error("Unexpected non-playing phase ${state.phase}")
    }
}
