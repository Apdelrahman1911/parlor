package com.parlor.games.wordimpostor

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorPrivate
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
