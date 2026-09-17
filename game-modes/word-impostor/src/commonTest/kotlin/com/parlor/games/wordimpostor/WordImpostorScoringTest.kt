package com.parlor.games.wordimpostor

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorProjection
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordImpostorValidation
import com.parlor.games.wordimpostor.protocol.WordImpostorCodec
import com.parlor.games.wordimpostor.protocol.WordImpostorSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordImpostorScoringTest {
    private val reducer = WordImpostorReducer()
    private fun players(count: Int) = List(count) { Player(PlayerId("p$it"), "Player $it", it) }

    @Test fun each_correct_voter_and_each_correct_guesser_get_independent_points_for_every_supported_size() {
        for (count in 3..12) for (impostors in 1..3) for (correctGuesses in listOf(true, false)) {
            val settings = WordImpostorSettings(impostors = impostors, rounds = 1)
            if (!settings.supports(count)) continue
            val voting = voting(reducer.initial(players(count), settings, count.toLong()))
            val team = voting.hostOnly.impostors.toList()
            val ordinary = voting.players.map { it.id }.filter { it !in team }
            val correctVoters = ordinary.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
            val votes = voting.players.associate { player ->
                player.id to if (player.id in correctVoters) team[ordinary.indexOf(player.id) % team.size] else
                    ordinary.first { it != player.id }
            }
            var state = cast(voting, votes)
            assertEquals(null, state.public.result)
            assertTrue(state.public.scores.values.all { it == 0 })
            team.forEach { id ->
                val word = if (correctGuesses) state.hostOnly.wordId!! else state.hostOnly.choices.getValue(id).first {
                    it != state.hostOnly.wordId
                }
                val action = WordImpostorAction.Guess(id, state.public.token, word)
                state = reducer.apply(state, action)
                assertEquals(state, reducer.apply(state, action))
                if (state.phase == WordImpostorPhase.Guessing) {
                    // Do not confirm a role or the word through early score updates.
                    assertEquals(null, state.public.result)
                    assertTrue(state.public.scores.values.all { it == 0 })
                }
            }
            assertEquals(WordImpostorPhase.MatchResult, state.phase)
            val result = state.public.result!!
            assertEquals(correctVoters, result.correctVoters)
            for (id in ordinary) assertEquals(if (id in correctVoters) 1 else 0, result.awarded[id])
            for (id in team) assertEquals(if (correctGuesses) 1 else 0, result.awarded[id])
            assertEquals(correctVoters.size + if (correctGuesses) impostors else 0, state.public.scores.values.sum())
            verify(state)
        }
    }

    @Test fun tied_tallies_do_not_cancel_a_correct_individual_vote_or_successful_word_guess() {
        var state = voting(reducer.initial(players(5), WordImpostorSettings(rounds = 1), 7))
        val ids = state.players.map { it.id }
        val votes = ids.mapIndexed { index, id -> id to ids[(index + 1) % ids.size] }.toMap()
        state = cast(state, votes)
        val impostor = state.hostOnly.impostors.single()
        val correctVoter = votes.entries.single { it.value == impostor }.key
        state = reducer.apply(state, WordImpostorAction.Guess(impostor, state.public.token, state.hostOnly.wordId!!))
        val result = state.public.result!!
        assertTrue(result.identified.isEmpty())
        assertEquals(setOf(correctVoter), result.correctVoters)
        assertEquals(1, state.public.scores[correctVoter])
        assertEquals(1, state.public.scores[impostor])
        assertEquals(2, state.public.scores.values.sum())
        verify(state)
        val wrongAward = state.copy(public = state.public.copy(result = result.copy(correctVoters = emptySet())))
        assertFalse(WordImpostorValidation.publicState(wrongAward))
        assertFails { WordImpostorSnapshotCodec().encode(wrongAward) }
    }

    @Test fun multiple_rounds_accumulate_only_personal_points_and_snapshots_validate_own_ballot() {
        var state = reducer.initial(players(7), WordImpostorSettings(impostors = 2, rounds = 3), 22)
        val expected = state.public.scores.toMutableMap()
        repeat(3) { round ->
            state = voting(state)
            val team = state.hostOnly.impostors
            val ordinary = state.players.map { it.id }.filter { it !in team }
            state = cast(state, state.players.associate { it.id to if (it.id in team) ordinary.first() else team.first() })
            for ((index, id) in team.withIndex()) {
                val word = if (index == 0) state.hostOnly.wordId!! else state.hostOnly.choices.getValue(id).first { it != state.hostOnly.wordId }
                state = reducer.apply(state, WordImpostorAction.Guess(id, state.public.token, word))
                if (index == 0) assertEquals(expected, state.public.scores)
                if (index == 0) expected[id] = expected.getValue(id) + 1
            }
            ordinary.forEach { expected[it] = expected.getValue(it) + 1 }
            assertEquals(expected, state.public.scores)
            assertEquals(ordinary.toSet(), state.public.result!!.correctVoters)
            verify(state)
            val ownId = ordinary.first()
            val own = WordImpostorProjection.toPlayer(state, ownId).state
            val forged = own.copy(privatePerPlayer = mapOf(ownId to own.privatePerPlayer.getValue(ownId).copy(vote = ordinary.last())))
            assertFalse(WordImpostorValidation.playerState(forged, ownId))
            if (round < 2) {
                val next = WordImpostorAction.NextRound(state.public.token)
                state = reducer.apply(state, next)
                assertEquals(state, reducer.apply(state, next))
                assertEquals(expected, state.public.scores)
                assertEquals(null, state.public.result)
            }
        }
        state = reducer.apply(state, WordImpostorAction.Rematch(state.public.token))
        assertTrue(state.public.scores.values.all { it == 0 })
        assertTrue(state.hostOnly.votes.isEmpty() && state.hostOnly.guesses.isEmpty())
        verify(state)
    }

    private fun voting(initial: WordImpostorState): WordImpostorState {
        var state = initial
        for (p in state.players) state = reducer.apply(state, WordImpostorAction.Ready(p.id, state.public.token))
        for ((index, pair) in state.public.interactions.withIndex()) {
            state = reducer.apply(state, WordImpostorAction.Answered(pair.asker, state.public.token, index))
        }
        return reducer.apply(state, WordImpostorAction.OpenVoting(state.public.token))
    }

    private fun cast(initial: WordImpostorState, votes: Map<PlayerId, PlayerId>): WordImpostorState {
        var state = initial
        for ((by, target) in votes) {
            val action = WordImpostorAction.Vote(by, state.public.token, target)
            state = reducer.apply(state, action)
            assertEquals(state, reducer.apply(state, action))
        }
        return state
    }

    private fun verify(state: WordImpostorState) {
        val public = WordImpostorCodec.decodePublic(WordImpostorCodec.encodePublic(state))
        assertTrue(public.hostOnly.votes.isEmpty())
        state.players.forEach { player ->
            val own = WordImpostorCodec.decodePlayer(public, WordImpostorCodec.encodePrivate(state, player.id), player.id)
            assertEquals(WordImpostorProjection.toPlayer(state, player.id).state, own)
        }
        assertEquals(state, WordImpostorSnapshotCodec().decode(WordImpostorSnapshotCodec().encode(state)))
    }
}
