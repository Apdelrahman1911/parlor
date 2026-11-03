package com.parlor.games.ghamza

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaProjection
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.protocol.GhamzaCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Documents the established rules' information model, not a secrecy guarantee.
 * Public reports make the final accusation certain. A future rules variant
 * must deliberately revise these expectations rather than hide only the UI.
 */
class GhamzaPublicInformationTest {
    private val reducer = GhamzaReducer()

    @Test
    fun finalGuestCanDeduceWinkerFromPublicReportsWithoutPrivateRoles() {
        for (count in 3..12) for (attempts in 1..3) for (rounds in GhamzaSettings.ROUND_COUNTS) {
            var state = readyState(count, attempts, rounds)
            val guests = guests(state)
            for (guest in guests.dropLast(1)) for (attempt in 1..attempts) {
                state = reducer.apply(state, GhamzaAction.Winked(guest.id, state.public.token, attempt))
            }
            assertEquals(GhamzaPhase.FinalGuess, state.phase)
            val public = publicRoundTrip(state)
            val guesser = requireNotNull(public.public.finalGuesser)
            assertEquals(guests.last().id, guesser)
            val inferred = zeroReportCandidates(public, guesser).single()
            assertEquals(state.hostOnly.winker, inferred)
            val result = reducer.apply(state, GhamzaAction.Guess(guesser, public.public.token, inferred))
            assertEquals(guesser, requireNotNull(result.public.result).winner)
        }
    }

    @Test
    fun multipleLivesCanMakeWinkerDeducibleBeforeAnyElimination() {
        for (count in 3..12) for (attempts in 2..3) for (rounds in GhamzaSettings.ROUND_COUNTS) {
            var state = readyState(count, attempts, rounds)
            val guests = guests(state)
            for (guest in guests) {
                state = reducer.apply(state, GhamzaAction.Winked(guest.id, state.public.token, 1))
            }
            assertEquals(GhamzaPhase.Social, state.phase)
            val public = publicRoundTrip(state)
            assertTrue(public.public.reports.values.all { it < attempts })
            assertNull(public.public.finalGuesser)
            assertEquals(state.hostOnly.winker, zeroReportCandidates(public, guests.last().id).single())
        }
    }

    private fun readyState(count: Int, attempts: Int, rounds: Int): GhamzaState {
        val roster = List(count) { Player(PlayerId("p$it"), "Synthetic $it", it) }
        var state = reducer.initial(roster, GhamzaSettings(attempts, rounds), count.toLong())
        for (player in roster) state = reducer.apply(state, GhamzaAction.Ready(player.id, state.public.token))
        assertEquals(GhamzaPhase.Social, state.phase)
        return state
    }

    private fun guests(state: GhamzaState): List<Player> = RandomSource.seeded(state.players.size.toLong())
        .shuffled(state.players.filter { it.id != state.hostOnly.winker })

    private fun publicRoundTrip(state: GhamzaState): GhamzaState {
        val public = GhamzaProjection.toPublic(state).state
        val decoded = GhamzaCodec.decodePublic(GhamzaCodec.encodePublic(public))
        assertEquals(public, decoded)
        assertTrue(decoded.privatePerPlayer.isEmpty())
        assertNull(decoded.hostOnly.seed)
        assertNull(decoded.hostOnly.winker)
        assertTrue(decoded.hostOnly.history.isEmpty())
        assertNull(decoded.public.result)
        return decoded
    }

    /** Inference must accept only a public projection and a known Guest's own identity. */
    private fun zeroReportCandidates(public: GhamzaState, knownGuest: PlayerId): Set<PlayerId> {
        assertTrue(public.privatePerPlayer.isEmpty())
        assertNull(public.hostOnly.winker)
        return public.public.reports.filter { (id, reports) -> id != knownGuest && reports == 0 }.keys
    }
}
