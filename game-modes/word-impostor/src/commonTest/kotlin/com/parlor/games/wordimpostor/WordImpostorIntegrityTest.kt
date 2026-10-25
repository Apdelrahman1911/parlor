package com.parlor.games.wordimpostor

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorPrivate
import com.parlor.games.wordimpostor.domain.WordImpostorProjection
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordImpostorValidation
import com.parlor.games.wordimpostor.protocol.WordImpostorCodec
import com.parlor.games.wordimpostor.protocol.WordImpostorSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordImpostorIntegrityTest {
    private val reducer = WordImpostorReducer()
    private val players = List(12) { Player(PlayerId("界".repeat(63) + it.toString(16)), "Player $it", it) }

    @Test
    fun deeply_nested_input_is_rejected_before_recursive_decoding() {
        val payload = ("[".repeat(400) + "0" + "]".repeat(400)).encodeToByteArray()
        val failure = assertFailsWith<IllegalArgumentException> { WordImpostorCodec.decodeAction(payload) }
        assertEquals("JSON nesting exceeds the supported limit", failure.message)
        assertFailsWith<IllegalArgumentException> { WordImpostorCodec.decodePublic(payload) }
        assertFailsWith<IllegalArgumentException> { WordImpostorSnapshotCodec().decode(payload) }
    }

    @Test
    fun private_guess_cannot_arrive_before_its_public_submission_count() {
        var state = reducer.initial(players.take(5), WordImpostorSettings(impostors = 2), 91)
        state.players.forEach { state = reducer.apply(state, WordImpostorAction.Ready(it.id, state.public.token)) }
        state.public.interactions.forEachIndexed { index, pair ->
            state = reducer.apply(state, WordImpostorAction.Answered(pair.asker, state.public.token, index))
        }
        state = reducer.apply(state, WordImpostorAction.OpenVoting(state.public.token))
        state.players.forEachIndexed { index, player ->
            state = reducer.apply(state, WordImpostorAction.Vote(player.id, state.public.token,
                state.players[(index + 1) % state.players.size].id))
        }
        val before = WordImpostorProjection.toPublic(state).state
        val impostor = state.hostOnly.impostors.first()
        state = reducer.apply(state, WordImpostorAction.Guess(impostor, state.public.token,
            state.hostOnly.choices.getValue(impostor).first()))
        val payload = WordImpostorCodec.encodePrivate(state, impostor)
        val current = WordImpostorProjection.toPublic(state).state
        assertEquals(1, current.public.guessCount)
        assertTrue(WordImpostorValidation.playerState(WordImpostorCodec.decodePlayer(current, payload, impostor), impostor))
        assertFailsWith<IllegalArgumentException> { WordImpostorCodec.decodePlayer(before, payload, impostor) }
    }

    @Test
    fun bounded_history_aborts_without_revealing_the_word_votes_or_roles_and_still_restores() {
        var state = reducer.initial(players, WordImpostorSettings(impostors = 3), 937)
        val id = players.last().id
        repeat(WordImpostorReducer.MAX_HISTORY - 1) { index ->
            state = reducer.apply(state, if (index % 2 == 0) WordImpostorAction.Disconnected(id) else WordImpostorAction.Reconnected(id))
        }
        assertEquals(WordImpostorPhase.Reveal, state.phase)
        assertEquals(state, reducer.apply(state, WordImpostorAction.Disconnected(id)))
        state = reducer.apply(state, WordImpostorAction.Reconnected(id))
        assertEquals(WordImpostorPhase.Aborted, state.phase)
        assertEquals(WordImpostorReducer.MAX_HISTORY, state.hostOnly.history.size)
        assertEquals(WordImpostorAction.Abort, state.hostOnly.history.last())
        assertTrue(state.public.scores.values.all { it == 0 })
        val codec = WordImpostorSnapshotCodec()
        assertEquals(state, codec.decode(codec.encode(state)))
        assertEquals(state, reducer.apply(state, WordImpostorAction.Abort))
        val public = WordImpostorCodec.decodePublic(WordImpostorCodec.encodePublic(state))
        players.forEach { player ->
            val payload = WordImpostorCodec.encodePrivate(state, player.id)
            assertEquals(WordImpostorPrivate(),
                WordImpostorCodec.decodePlayer(public, payload, player.id).privatePerPlayer.getValue(player.id))
            assertFailsWith<IllegalArgumentException> { WordImpostorCodec.decodePlayer(public, payload, PlayerId("outsider")) }
        }
    }

    @Test
    fun abort_cannot_be_used_to_smuggle_impossible_vote_totals_or_progress() {
        val initial = reducer.initial(players, WordImpostorSettings(impostors = 3), 11)
        val aborted = reducer.apply(initial, WordImpostorAction.Abort)
        val invalid = listOf(
            aborted.copy(public = aborted.public.copy(voteCounts = mapOf(players.first().id to 12))),
            aborted.copy(public = aborted.public.copy(guessCount = 1)),
            aborted.copy(public = aborted.public.copy(questionIndex = 1)),
            aborted.copy(public = aborted.public.copy(voted = setOf(players.first().id))),
            initial.copy(public = initial.public.copy(scores = initial.public.scores + (players.first().id to 1))),
        )
        invalid.forEach { state ->
            assertFalse(WordImpostorValidation.publicState(state))
            assertFailsWith<IllegalArgumentException> { WordImpostorCodec.encodePublic(state) }
        }
    }
}
