package com.parlor.games.ghamza

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaAuthority
import com.parlor.games.ghamza.domain.GhamzaHostOnly
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaProjection
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaRole
import com.parlor.games.ghamza.domain.GhamzaRules
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.domain.GhamzaValidation
import com.parlor.games.ghamza.protocol.GhamzaCodec
import com.parlor.games.ghamza.protocol.GhamzaSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GhamzaRulesTest {
    private val reducer = GhamzaReducer()
    private fun players(count: Int) = List(count) { Player(PlayerId("p$it"), "Player $it", it) }

    @Test fun everyCountAttemptsAndGuessOutcome() {
        for (count in 3..12) for (attempts in 1..3) for (correct in listOf(true, false)) {
            var state = reducer.initial(players(count), GhamzaSettings(attempts, 1), count.toLong())
            assertEquals(1, state.privatePerPlayer.values.count { it.role == GhamzaRole.Winker })
            assertEquals(state, reducer.initial(players(count), GhamzaSettings(attempts, 1), count.toLong()))
            for (p in state.players) state = reducer.apply(state, GhamzaAction.Ready(p.id, state.public.token))
            val guests = state.players.filter { it.id != state.hostOnly.winker }
            for (guest in guests.dropLast(1)) for (attempt in 1..attempts) {
                val action = GhamzaAction.Winked(guest.id, state.public.token, attempt)
                state = reducer.apply(state, action)
                assertEquals(state, reducer.apply(state, action))
                verify(state)
            }
            assertEquals(GhamzaPhase.FinalGuess, state.phase)
            assertEquals(guests.last().id, state.public.finalGuesser)
            val guesser = state.public.finalGuesser!!
            assertEquals(state, reducer.apply(state, GhamzaAction.Winked(guesser, state.public.token, 1)))
            val target = if (correct) state.hostOnly.winker!! else guests.first().id
            state = reducer.apply(state, GhamzaAction.Guess(guesser, state.public.token, target))
            assertEquals(GhamzaPhase.MatchResult, state.phase)
            assertEquals(if (correct) guesser else state.hostOnly.winker, state.public.result!!.winner)
            val result = state.public.result!!
            assertEquals(if (correct) result.winker else guesser, result.loser)
            assertTrue(GhamzaRules.isEliminated(state, result.loser))
            assertFalse(GhamzaRules.isEliminated(state, result.winner))
            assertEquals(state, reducer.apply(state, GhamzaAction.Guess(guesser, state.public.token, target)))
            verify(state)
            assertEquals(state, GhamzaSnapshotCodec().decode(GhamzaSnapshotCodec().encode(state)))
            val again = reducer.apply(state, GhamzaAction.Rematch(state.public.token))
            assertEquals(state.public.token + 1, again.public.token)
            assertTrue(again.players.all { GhamzaRules.livesRemaining(again, it.id) == attempts })
            assertNull(again.public.result)
            assertEquals(again, reducer.apply(again, GhamzaAction.Ready(guesser, state.public.token)))
            assertEquals(again, GhamzaSnapshotCodec().decode(GhamzaSnapshotCodec().encode(again)))
        }
    }

    @Test fun attemptsPhaseAuthorityAndLifecycleCannotBeForged() {
        var state = reducer.initial(players(5), GhamzaSettings(3), 29)
        val guest = state.players.first { it.id != state.hostOnly.winker }.id
        val action = GhamzaAction.Winked(guest, 1, 1)
        assertEquals(state, reducer.apply(state, action))
        for (p in state.players) state = reducer.apply(state, GhamzaAction.Ready(p.id, 1))
        assertEquals(state, reducer.apply(state, GhamzaAction.Winked(guest, 1, 2)))
        assertEquals(state, reducer.apply(state, GhamzaAction.Winked(state.hostOnly.winker!!, 1, 1)))
        assertFalse(GhamzaAuthority.allowed(action, state.hostOnly.winker!!, state.players.first().id, state))
        assertFails { GhamzaCodec.encodeAction(GhamzaAction.Abort) }
        assertEquals(action, GhamzaCodec.decodeAction(GhamzaCodec.encodeAction(action)))
        val paused = reducer.apply(state, GhamzaAction.Disconnected(guest))
        assertEquals(paused, reducer.apply(paused, action))
        val restored = reducer.apply(paused, GhamzaAction.Reconnected(guest))
        assertEquals(state.public, restored.public)
        assertEquals(state.privatePerPlayer, restored.privatePerPlayer)
        val aborted = reducer.apply(paused, GhamzaAction.Abort)
        verify(aborted)
        assertNull(aborted.public.result)
        assertEquals(state.public.reports, aborted.public.reports)
        assertEquals(aborted, GhamzaSnapshotCodec().decode(GhamzaSnapshotCodec().encode(aborted)))
    }

    @Test fun strictCodecsRejectImpossibleResultsAndLegacyVersions() {
        var state = reducer.initial(players(3), GhamzaSettings(rounds = 3), 47)
        repeat(3) { round ->
            for (p in state.players) state = reducer.apply(state, GhamzaAction.Ready(p.id, state.public.token))
            val guest = state.players.first { it.id != state.hostOnly.winker }.id
            state = reducer.apply(state, GhamzaAction.Winked(guest, state.public.token, 1))
            state = reducer.apply(state, GhamzaAction.Guess(state.public.finalGuesser!!, state.public.token, state.hostOnly.winker!!))
            verify(state)
            assertEquals(round + 1, state.public.round)
            if (round < 2) state = reducer.apply(state, GhamzaAction.NextRound(state.public.token))
        }
        assertEquals(GhamzaPhase.MatchResult, state.phase)
        val result = state.public.result!!
        val forged = state.copy(public = state.public.copy(result = result.copy(winner = result.loser)))
        assertFails { GhamzaSnapshotCodec().encode(forged) }
        assertFails { GhamzaCodec.encodePublic(forged) }
        val public = GhamzaCodec.encodePublic(state)
        assertFails { GhamzaCodec.decodePublic(public.decodeToString().replace("\"version\":2", "\"version\":1").encodeToByteArray()) }
        assertFails { GhamzaCodec.decodePublic(public.decodeToString().replace("\"version\":2", "\"version\":2,\"secret\":1").encodeToByteArray()) }
        assertFails { GhamzaCodec.decodePublic(ByteArray(32769)) }
        assertFails { reducer.initial(players(2), GhamzaSettings(), 1) }
        assertFails { GhamzaSettings(4) }
    }

    @Test fun allRoundAndLifeSettingsResetEliminationsWithoutCumulativeStateOrRoleExclusions() {
        var repeatedWinker = false
        for (rounds in GhamzaSettings.ROUND_COUNTS) for (attempts in 1..3) {
            var state = reducer.initial(players(5), GhamzaSettings(attempts, rounds), 47)
            var previousWinker: PlayerId? = null
            repeat(rounds) { index ->
                assertEquals(index + 1, state.public.round)
                assertEquals(GhamzaPhase.Reveal, state.phase)
                assertTrue(state.players.all { GhamzaRules.livesRemaining(state, it.id) == attempts })
                assertTrue(state.public.ready.isEmpty() && state.public.recentReports.isEmpty())
                assertNull(state.public.result)
                val winker = state.hostOnly.winker!!
                assertEquals(RandomSource.seeded(47L xor state.public.token).pick(state.players).id, winker)
                repeatedWinker = repeatedWinker || previousWinker == winker
                previousWinker = winker
                for (p in state.players) state = reducer.apply(state, GhamzaAction.Ready(p.id, state.public.token))
                val guests = state.players.filter { it.id != winker }
                for (guest in guests.dropLast(1)) for (attempt in 1..attempts) {
                    state = reducer.apply(state, GhamzaAction.Winked(guest.id, state.public.token, attempt))
                }
                val target = if (index % 2 == 0) winker else guests.first().id
                state = reducer.apply(state, GhamzaAction.Guess(guests.last().id, state.public.token, target))
                verify(state)
                assertEquals(state, GhamzaSnapshotCodec().decode(GhamzaSnapshotCodec().encode(state)))
                val oldToken = state.public.token
                if (index < rounds - 1) {
                    state = reducer.apply(state, GhamzaAction.NextRound(oldToken))
                    assertEquals(state, reducer.apply(state, GhamzaAction.NextRound(oldToken)))
                    assertEquals(state, reducer.apply(state, GhamzaAction.Winked(guests.first().id, oldToken, 1)))
                } else assertEquals(state, reducer.apply(state, GhamzaAction.NextRound(oldToken)))
            }
        }
        assertTrue(repeatedWinker, "Random role assignment must permit consecutive Winker rounds")
    }

    private fun verify(state: GhamzaState) {
        assertTrue(GhamzaValidation.publicState(state))
        val bytes = GhamzaCodec.encodePublic(state)
        assertFalse(bytes.decodeToString().contains("score", ignoreCase = true))
        assertFalse(GhamzaSnapshotCodec().encode(state).decodeToString().contains("score", ignoreCase = true))
        val public = GhamzaCodec.decodePublic(bytes)
        assertEquals(GhamzaHostOnly(), public.hostOnly)
        if (state.public.result == null) assertFalse(bytes.decodeToString().contains("winker", ignoreCase = true))
        state.players.forEach { p ->
            val own = GhamzaCodec.decodePlayer(public, GhamzaCodec.encodePrivate(state, p.id), p.id)
            assertEquals(setOf(p.id), own.privatePerPlayer.keys)
            assertEquals(GhamzaProjection.toPlayer(state, p.id).state, own)
        }
    }
}
