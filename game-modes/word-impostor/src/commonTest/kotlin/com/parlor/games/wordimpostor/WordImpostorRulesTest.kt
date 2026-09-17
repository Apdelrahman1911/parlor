package com.parlor.games.wordimpostor

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorAuthority
import com.parlor.games.wordimpostor.domain.WordImpostorHostOnly
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorPrivate
import com.parlor.games.wordimpostor.domain.WordImpostorProjection
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordImpostorValidation
import com.parlor.games.wordimpostor.domain.WordRole
import com.parlor.games.wordimpostor.domain.WordTopic
import com.parlor.games.wordimpostor.domain.WordTopicBank
import com.parlor.games.wordimpostor.protocol.WordImpostorCodec
import com.parlor.games.wordimpostor.protocol.WordImpostorSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordImpostorRulesTest {
    private val reducer = WordImpostorReducer()
    private fun players(count: Int) = List(count) { Player(PlayerId("p$it"), "Player $it", it) }

    @Test fun completeBanksHaveUniqueWordsQuestionsAndPlausibleChoices() {
        assertEquals(12, WordTopic.entries.size)
        for (topic in WordTopic.entries) {
            val words = WordTopicBank.words(topic)
            val questions = WordTopicBank.questions(topic)
            assertTrue(words.size >= 20)
            assertEquals(words.size, words.map { it.id }.toSet().size)
            assertTrue(questions.size > 12)
            assertEquals(questions.size, questions.toSet().size)
            assertTrue(words.groupBy { it.group }.values.all { it.size >= 5 })
            val sampledWords = mutableSetOf<String>()
            val sampledQuestions = mutableSetOf<List<String>>()
            val answerPositions = mutableSetOf<Int>()
            for (seed in 1L..40L) {
                val state = reducer.initial(players(12), WordImpostorSettings(topic, 3), seed)
                sampledWords += state.hostOnly.wordId!!
                sampledQuestions += state.hostOnly.questions
                assertEquals(12, state.hostOnly.questions.distinct().size)
                for (options in state.hostOnly.choices.values) {
                    assertEquals(5, options.distinct().size)
                    assertTrue(state.hostOnly.wordId in options)
                    assertTrue(options.all { id -> words.any { it.id == id } })
                    assertEquals(1, options.map { id -> words.single { it.id == id }.group }.distinct().size)
                    answerPositions += options.indexOf(state.hostOnly.wordId)
                }
                verify(state)
            }
            assertTrue(sampledWords.size > 5)
            assertTrue(sampledQuestions.size > 5)
            assertEquals((0..4).toSet(), answerPositions)
        }
    }

    @Test fun everyPlayerAndImpostorCountCompletesAllPhasesPrivately() {
        for (count in 3..12) for (impostors in 1..3) {
            val settings = WordImpostorSettings(impostors = impostors, rounds = 1)
            if (!settings.supports(count)) {
                assertFails { reducer.initial(players(count), settings, 4) }
                continue
            }
            var state = reducer.initial(players(count), settings, count.toLong())
            assertEquals(state, reducer.initial(players(count), settings, count.toLong()))
            assertEquals(impostors, state.hostOnly.impostors.size)
            val ids = state.players.map { it.id }.toSet()
            assertEquals(ids, state.public.interactions.map { it.asker }.toSet())
            assertEquals(ids, state.public.interactions.map { it.answerer }.toSet())
            assertTrue(state.public.interactions.all { it.asker != it.answerer })
            for (player in state.players) state = reducer.apply(state, WordImpostorAction.Ready(player.id, state.public.token))
            repeat(count) { index ->
                val pair = state.public.interactions[index]
                val action = WordImpostorAction.Answered(pair.asker, state.public.token, index)
                assertEquals(state, reducer.apply(state, action.copy(by = pair.answerer)))
                state = reducer.apply(state, action)
                assertEquals(state, reducer.apply(state, action))
                verify(state)
            }
            assertEquals(WordImpostorPhase.Discussion, state.phase)
            state = reducer.apply(state, WordImpostorAction.OpenVoting(state.public.token))
            for ((index, player) in state.players.withIndex()) {
                val target = state.hostOnly.impostors.firstOrNull { it != player.id }
                    ?: state.players.first { it.id != player.id }.id
                val action = WordImpostorAction.Vote(player.id, state.public.token, target)
                assertEquals(state, reducer.apply(state, action.copy(target = player.id)))
                state = reducer.apply(state, action)
                assertEquals(state, reducer.apply(state, action))
                if (index < count - 1) assertNull(state.public.voteCounts)
                verify(state)
            }
            assertEquals(WordImpostorPhase.Guessing, state.phase)
            assertEquals(count, state.public.voteCounts!!.values.sum())
            val outsiders = state.players.filter { it.id !in state.hostOnly.impostors }
            assertEquals(state, reducer.apply(state, WordImpostorAction.Guess(outsiders.first().id, state.public.token, state.hostOnly.wordId!!)))
            val wrong = state.hostOnly.choices.values.first().first { it != state.hostOnly.wordId }
            assertEquals(state, reducer.apply(state, WordImpostorAction.Guess(state.hostOnly.impostors.first(), state.public.token, "not-a-word")))
            for ((index, id) in state.hostOnly.impostors.withIndex()) {
                val answer = if (index == 0) state.hostOnly.wordId!! else wrong
                state = reducer.apply(state, WordImpostorAction.Guess(id, state.public.token, answer))
                verify(state)
            }
            assertEquals(WordImpostorPhase.MatchResult, state.phase)
            val result = state.public.result!!
            assertEquals(1, result.awarded[state.hostOnly.impostors.first()])
            for (id in state.hostOnly.impostors.drop(1)) assertEquals(0, result.awarded[id])
            assertEquals(state, WordImpostorSnapshotCodec().decode(WordImpostorSnapshotCodec().encode(state)))
            val again = reducer.apply(state, WordImpostorAction.Rematch(state.public.token))
            assertEquals(state.public.token + 1, again.public.token)
            assertTrue(again.public.scores.values.all { it == 0 })
            assertEquals(again, reducer.apply(again, WordImpostorAction.Ready(again.players.first().id, state.public.token)))
        }
    }

    @Test fun tiesFailIdentificationAndScoresFollowBothIndependentObjectives() {
        val ids = players(5).map { it.id }
        assertEquals(emptySet(), WordImpostorReducer.identifiedByVotes(ids.associateWith { 1 }, 1))
        assertEquals(setOf(ids[0]), WordImpostorReducer.identifiedByVotes(mapOf(ids[0] to 3, ids[1] to 1, ids[2] to 1), 1))
        assertEquals(emptySet(), WordImpostorReducer.identifiedByVotes(mapOf(ids[0] to 3, ids[1] to 1, ids[2] to 1), 2))
        assertEquals(setOf(ids[0], ids[1]), WordImpostorReducer.identifiedByVotes(mapOf(ids[0] to 3, ids[1] to 2, ids[2] to 1), 2))
    }

    @Test fun disconnectedOrForgedActionsCannotAdvanceOrExposeASecret() {
        val state = reducer.initial(players(7), WordImpostorSettings(impostors = 2), 999)
        val peer = state.players.last().id
        val action = WordImpostorAction.Ready(peer, 1)
        assertFalse(WordImpostorAuthority.allowed(action, state.players.first().id, state.players.first().id, state))
        assertFalse(WordImpostorAuthority.allowed(WordImpostorAction.OpenVoting(1), peer, state.players.first().id, state))
        assertEquals(action, WordImpostorCodec.decodeAction(WordImpostorCodec.encodeAction(action)))
        assertFails { WordImpostorCodec.encodeAction(WordImpostorAction.Disconnected(peer)) }
        val paused = reducer.apply(state, WordImpostorAction.Disconnected(peer))
        assertEquals(paused, reducer.apply(paused, action))
        val resumed = reducer.apply(paused, WordImpostorAction.Reconnected(peer))
        assertEquals(state.public, resumed.public)
        assertEquals(state.privatePerPlayer, resumed.privatePerPlayer)
        val aborted = reducer.apply(paused, WordImpostorAction.Abort)
        verify(aborted)
        assertNull(aborted.public.result)
        assertTrue(aborted.public.scores.values.all { it == 0 })
        assertEquals(aborted, WordImpostorSnapshotCodec().decode(WordImpostorSnapshotCodec().encode(aborted)))
        assertFails { WordImpostorSnapshotCodec().encode(state.copy(hostOnly = state.hostOnly.copy(wordId = "food_foul"))) }
        val bytes = WordImpostorCodec.encodePublic(state)
        assertFails { WordImpostorCodec.decodePublic(bytes.decodeToString().replace("\"version\":1", "\"version\":2").encodeToByteArray()) }
        assertFails { WordImpostorCodec.decodePublic(bytes.decodeToString().replace("\"version\":1", "\"version\":1,\"wordId\":\"food_foul\"").encodeToByteArray()) }
        assertFails { WordImpostorCodec.decodePublic(ByteArray(32769)) }
    }

    private fun verify(state: WordImpostorState) {
        assertTrue(WordImpostorValidation.publicState(state), state.phase.id)
        val bytes = WordImpostorCodec.encodePublic(state)
        val public = WordImpostorCodec.decodePublic(bytes)
        assertEquals(WordImpostorHostOnly(), public.hostOnly)
        if (state.public.result == null) assertFalse(bytes.decodeToString().contains(state.hostOnly.wordId!!))
        for (p in state.players) {
            val own = WordImpostorCodec.decodePlayer(public, WordImpostorCodec.encodePrivate(state, p.id), p.id)
            assertEquals(WordImpostorProjection.toPlayer(state, p.id).state, own)
            assertEquals(setOf(p.id), own.privatePerPlayer.keys)
            val slice = own.privatePerPlayer.getValue(p.id)
            if (slice.role == WordRole.Impostor) assertNull(slice.wordId)
            if (state.phase == WordImpostorPhase.Aborted) assertEquals(WordImpostorPrivate(), slice)
        }
    }
}
