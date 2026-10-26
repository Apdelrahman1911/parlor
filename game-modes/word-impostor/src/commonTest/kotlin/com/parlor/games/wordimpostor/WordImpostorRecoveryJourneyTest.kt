package com.parlor.games.wordimpostor

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorHostOnly
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorProjection
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordTopic
import com.parlor.games.wordimpostor.protocol.WordImpostorCodec
import com.parlor.games.wordimpostor.protocol.WordImpostorSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Reducer/codec fault model, not live transport, credential recovery or native process restoration. */
class WordImpostorRecoveryJourneyTest {
    private val reducer = WordImpostorReducer()
    private val snapshots = WordImpostorSnapshotCodec()

    @Test
    fun forty_two_complete_matches_preserve_private_ballots_guesses_scores_and_identity() {
        var scenario = 0
        for (count in listOf(3, 7, 12)) for (impostors in 1..3) for (rounds in WordImpostorSettings.ROUND_COUNTS) {
            if (impostors * 2 >= count) continue
            for (seed in listOf(73L, 91L)) {
                val settings = WordImpostorSettings(WordTopic.entries[scenario++ % WordTopic.entries.size], impostors, rounds)
                val players = List(count) { Player(PlayerId("p$it"), "Player $it", it) }
                verifyMatch(reducer.initial(players, settings, seed))
            }
        }
        assertEquals(42, scenario)
    }

    private fun verifyMatch(initial: WordImpostorState) {
        var clean = initial
        var recovered = initial
        val delivered = mutableListOf<WordImpostorAction>()
        val visited = mutableSetOf<WordImpostorPhase>()
        var steps = 0
        while (clean.phase != WordImpostorPhase.MatchResult) {
            assertTrue(steps++ < 512, "A deterministic complete match must terminate")
            val action = nextAction(clean)
            val queued = WordImpostorCodec.encodeAction(action)
            if (visited.add(clean.phase) || steps % 7 == 0 || clean.phase == WordImpostorPhase.Guessing) {
                recovered = interruptAndResume(recovered, WordImpostorCodec.decodeAction(queued))
            }
            clean = reducer.apply(clean, action)
            val previous = recovered
            recovered = reducer.apply(recovered, WordImpostorCodec.decodeAction(queued))
            assertNotEquals(previous, recovered, "The queued command must actually be accepted")
            delivered += action
            // Model an acknowledgement lost after acceptance and older commands delivered out of order.
            delivered.forEach { assertEquals(recovered, reducer.apply(recovered, it), "Duplicate/stale command changed state") }
            assertEquivalent(clean, recovered)
            assertRecipientViews(recovered)
        }
        assertEquals(initial.public.settings.rounds, recovered.public.round)
        assertEquals(clean.public.scores, recovered.public.scores)
        assertEquals(clean.public.result, recovered.public.result)
        assertEquals(recovered, restore(recovered))
        val rematch = WordImpostorAction.Rematch(recovered.public.token)
        val next = reducer.apply(recovered, rematch)
        assertEquals(initial.players, next.players)
        assertTrue(next.public.scores.values.all { it == 0 })
        assertEquals(reducer.apply(clean, rematch), next)
        delivered.forEach { assertEquals(next, reducer.apply(next, it), "Old match action crossed the rematch token") }
        assertRecipientViews(next)
    }

    private fun interruptAndResume(state: WordImpostorState, queued: WordImpostorAction): WordImpostorState {
        val first = state.players.first().id
        val last = state.players.last().id
        var paused = reducer.apply(state, WordImpostorAction.Disconnected(first))
        assertEquals(paused, reducer.apply(paused, WordImpostorAction.Disconnected(first)))
        paused = reducer.apply(paused, WordImpostorAction.Disconnected(last))
        assertEquals(paused, reducer.apply(paused, queued))
        paused = restore(paused)
        assertRecipientViews(paused)
        paused = reducer.apply(paused, WordImpostorAction.Reconnected(last))
        assertEquals(paused, reducer.apply(paused, queued), "One return must not clear the other seat's pause")
        paused = restore(paused)
        val resumed = reducer.apply(paused, WordImpostorAction.Reconnected(first))
        assertEquals(resumed, reducer.apply(resumed, WordImpostorAction.Reconnected(first)))
        assertEquivalent(state, resumed)
        return resumed
    }

    private fun restore(state: WordImpostorState): WordImpostorState = snapshots.decode(snapshots.encode(state)).also {
        assertEquals(state, it, "Authority replay must preserve the exact saved state")
    }

    private fun assertEquivalent(expected: WordImpostorState, actual: WordImpostorState) {
        assertEquals(expected.public, actual.public)
        assertEquals(expected.privatePerPlayer, actual.privatePerPlayer)
        assertEquals(expected.players, actual.players)
        assertEquals(expected.phase, actual.phase)
        assertEquals(expected.hostOnly.copy(history = actual.hostOnly.history), actual.hostOnly)
    }

    private fun assertRecipientViews(state: WordImpostorState) {
        val public = WordImpostorCodec.decodePublic(WordImpostorCodec.encodePublic(state))
        assertEquals(WordImpostorHostOnly(), public.hostOnly)
        assertTrue(public.privatePerPlayer.isEmpty())
        state.players.forEach { player ->
            val bytes = WordImpostorCodec.encodePrivate(state, player.id)
            val own = WordImpostorCodec.decodePlayer(public, bytes, player.id)
            assertEquals(WordImpostorProjection.toPlayer(state, player.id).state, own)
            assertEquals(setOf(player.id), own.privatePerPlayer.keys)
            val other = state.players.first { it.id != player.id }.id
            assertFailsWith<IllegalArgumentException> { WordImpostorCodec.decodePlayer(public, bytes, other) }
        }
    }

    private fun nextAction(state: WordImpostorState): WordImpostorAction = when (state.phase) {
        WordImpostorPhase.Reveal -> WordImpostorAction.Ready(
            state.players.first { it.id !in state.public.ready }.id, state.public.token,
        )
        WordImpostorPhase.Questions -> WordImpostorAction.Answered(
            state.public.interactions[state.public.questionIndex].asker, state.public.token, state.public.questionIndex,
        )
        WordImpostorPhase.Discussion -> WordImpostorAction.OpenVoting(state.public.token)
        WordImpostorPhase.Voting -> {
            val voter = state.players.first { it.id !in state.public.voted }
            val targets = state.players.filter { it.id != voter.id }
            val target = targets[(voter.seat + state.public.round) % targets.size].id
            WordImpostorAction.Vote(voter.id, state.public.token, target)
        }
        WordImpostorPhase.Guessing -> {
            val guesser = state.players.first { it.id in state.hostOnly.impostors && it.id !in state.hostOnly.guesses }
            val word = checkNotNull(state.hostOnly.wordId)
            val choice = if ((guesser.seat + state.public.round) % 2 == 0) word
                else state.hostOnly.choices.getValue(guesser.id).first { it != word }
            WordImpostorAction.Guess(guesser.id, state.public.token, choice)
        }
        WordImpostorPhase.RoundResult -> WordImpostorAction.NextRound(state.public.token)
        else -> error("Unexpected non-playing phase ${state.phase}")
    }
}
