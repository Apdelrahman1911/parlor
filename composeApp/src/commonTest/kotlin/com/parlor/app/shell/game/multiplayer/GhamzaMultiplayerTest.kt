package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaRole
import com.parlor.games.ghamza.domain.GhamzaSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GhamzaMultiplayerTest {
    @Test
    fun private_roles_confirmed_attempts_final_guess_and_rematch_work_for_all_seat_and_attempt_counts() = runTest {
        for (count in 3..12) for (attempts in 1..3) {
            val fixture = MultiplayerGameFixture(
                this, GhamzaGameShellBinding(GhamzaDefinition()), gamePlayers(count), seed = (count * attempts).toLong(),
                caseId = GhamzaSettings(attempts, 1).caseId,
            )
            try {
                fixture.attachPeers()
                val initial = fixture.session.currentState()
                val token = initial.public.token
                val winker = checkNotNull(initial.hostOnly.winker)
                val guests = initial.players.map { it.id }.filter { it != winker }
                fixture.peers.forEach { (id, peer) ->
                    val own = peer.controller.privateStateFor(id).value.state
                    assertEquals(setOf(id), own.privatePerPlayer.keys)
                    assertEquals(if (id == winker) GhamzaRole.Winker else GhamzaRole.Guest, own.privatePerPlayer.getValue(id).role)
                    assertNull(own.hostOnly.winker)
                    assertNull(own.public.result)
                }
                initial.players.forEach { fixture.perform(GhamzaAction.Ready(it.id, token), it.id) }
                assertEquals(GhamzaPhase.Social, fixture.session.currentState().phase)
                guests.dropLast(1).forEach { by ->
                    for (attempt in 1..attempts) {
                        fixture.perform(GhamzaAction.Winked(by, token, attempt), by)
                        assertEquals(attempt, fixture.session.currentState().public.reports[by])
                        assertNull(fixture.session.currentState().public.result)
                    }
                }
                val guessing = fixture.session.currentState()
                assertEquals(GhamzaPhase.FinalGuess, guessing.phase)
                assertEquals(guests.last(), guessing.public.finalGuesser)
                // Cover both outcomes, including guesses at already eliminated seats.
                val target = if (count % 2 == 0) winker else guests.first()
                fixture.perform(GhamzaAction.Guess(guests.last(), token, target), guests.last())
                val finished = fixture.session.currentState()
                assertEquals(GhamzaPhase.MatchResult, finished.phase)
                assertEquals(if (target == winker) guests.last() else winker, finished.public.result?.winner)
                assertEquals(1, finished.public.scores.values.sum())
                fixture.perform(GhamzaAction.Rematch(token))
                val rematch = fixture.session.currentState()
                assertEquals(GhamzaPhase.Reveal, rematch.phase)
                assertEquals(token + 1, rematch.public.token)
                assertTrue(rematch.public.scores.values.all { it == 0 })
                assertTrue(rematch.public.ready.isEmpty())
                assertTrue(rematch.public.recentReports.isEmpty())
            } finally {
                fixture.close()
            }
        }
    }
}
