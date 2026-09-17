package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordRole
import com.parlor.games.wordimpostor.domain.WordTopic
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordImpostorMultiplayerTest {
    @Test
    fun all_supported_seats_and_teams_complete_private_questions_votes_individual_guesses_and_rematch() = runTest {
        for (count in 3..12) for (impostors in 1..3) {
            val settings = WordImpostorSettings(WordTopic.entries[(count + impostors) % WordTopic.entries.size], impostors, 1)
            if (!settings.supports(count)) continue
            val fixture = MultiplayerGameFixture(
                this, WordImpostorGameShellBinding(WordImpostorDefinition()), gamePlayers(count),
                seed = (count * impostors).toLong(), caseId = settings.caseId,
            )
            try {
                fixture.attachPeers()
                val initial = fixture.session.currentState()
                val token = initial.public.token
                val team = initial.hostOnly.impostors
                val word = checkNotNull(initial.hostOnly.wordId)
                val ids = initial.players.map { it.id }
                fixture.peers.forEach { (id, peer) ->
                    val own = peer.controller.privateStateFor(id).value.state
                    assertNull(own.hostOnly.wordId)
                    assertTrue(own.hostOnly.impostors.isEmpty())
                    assertEquals(setOf(id), own.privatePerPlayer.keys)
                    val private = own.privatePerPlayer.getValue(id)
                    if (id in team) {
                        assertEquals(WordRole.Impostor, private.role)
                        assertEquals(team - id, private.teammates)
                        assertNull(private.wordId)
                    } else {
                        assertEquals(WordRole.Ordinary, private.role)
                        assertEquals(word, private.wordId)
                        assertTrue(private.teammates.isEmpty())
                    }
                }
                ids.forEach { fixture.perform(WordImpostorAction.Ready(it, token), it) }
                val questions = mutableSetOf<String>()
                repeat(count) { index ->
                    val state = fixture.session.currentState()
                    assertEquals(WordImpostorPhase.Questions, state.phase)
                    val asker = state.public.interactions[index].asker
                    state.privatePerPlayer.forEach { (id, private) ->
                        if (id == asker) assertTrue(questions.add(checkNotNull(private.questionId)))
                        else assertNull(private.questionId)
                    }
                    fixture.perform(WordImpostorAction.Answered(asker, token, index), asker)
                }
                assertEquals(count, questions.size)
                assertEquals(WordImpostorPhase.Discussion, fixture.session.currentState().phase)
                fixture.perform(WordImpostorAction.OpenVoting(token))
                ids.forEachIndexed { index, id ->
                    fixture.perform(WordImpostorAction.Vote(id, token, ids[(index + 1) % count]), id)
                    val state = fixture.session.currentState()
                    if (index < count - 1) assertNull(state.public.voteCounts)
                    assertNull(state.public.result)
                    fixture.peers.forEach { (peerId, peer) ->
                        val own = peer.controller.privateStateFor(peerId).value.state
                        assertTrue(own.hostOnly.votes.isEmpty())
                        assertEquals(setOf(peerId), own.privatePerPlayer.keys)
                    }
                }
                assertEquals(WordImpostorPhase.Guessing, fixture.session.currentState().phase)
                team.forEachIndexed { index, id ->
                    val state = fixture.session.currentState()
                    val choices = state.privatePerPlayer.getValue(id).choices
                    assertEquals(5, choices.toSet().size)
                    assertTrue(word in choices)
                    val choice = if (index % 2 == 0) word else choices.first { it != word }
                    fixture.perform(WordImpostorAction.Guess(id, token, choice), id)
                    if (index < team.size - 1) {
                        assertNull(fixture.session.currentState().public.result)
                        assertEquals(index + 1, fixture.session.currentState().public.guessCount)
                    }
                }
                val result = fixture.session.currentState()
                assertEquals(WordImpostorPhase.MatchResult, result.phase)
                assertEquals(word, result.public.result?.wordId)
                assertEquals(team, result.public.result?.impostors)
                // A cycle gives everybody one vote: the cutoff ties, so no ordinary-player point.
                assertEquals(false, result.public.result?.ordinaryTeamScored)
                assertEquals((impostors + 1) / 2, result.public.scores.values.sum())
                fixture.perform(WordImpostorAction.Rematch(token))
                val rematch = fixture.session.currentState()
                assertEquals(WordImpostorPhase.Reveal, rematch.phase)
                assertEquals(token + 1, rematch.public.token)
                assertTrue(rematch.public.scores.values.all { it == 0 })
                assertTrue(rematch.public.voted.isEmpty())
                assertNull(rematch.public.voteCounts)
                assertNull(rematch.public.result)
            } finally {
                fixture.close()
            }
        }
    }
}
