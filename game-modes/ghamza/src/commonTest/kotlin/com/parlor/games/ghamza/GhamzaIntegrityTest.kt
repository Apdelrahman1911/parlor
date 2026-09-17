package com.parlor.games.ghamza

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaPrivate
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.domain.GhamzaValidation
import com.parlor.games.ghamza.protocol.GhamzaCodec
import com.parlor.games.ghamza.protocol.GhamzaSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhamzaIntegrityTest {
    private val reducer = GhamzaReducer()
    private val players = List(12) { Player(PlayerId("界".repeat(63) + it.toString(16)), "Player $it", it) }

    @Test
    fun bounded_history_has_an_explicit_replayable_abort_instead_of_a_frozen_game() {
        var state = reducer.initial(players, GhamzaSettings(), 937)
        val id = players.last().id
        repeat(GhamzaReducer.MAX_HISTORY - 1) { index ->
            state = reducer.apply(state, if (index % 2 == 0) GhamzaAction.Disconnected(id) else GhamzaAction.Reconnected(id))
        }
        assertEquals(GhamzaPhase.Reveal, state.phase)
        assertEquals(state, reducer.apply(state, GhamzaAction.Disconnected(id)))
        state = reducer.apply(state, GhamzaAction.Reconnected(id))
        assertEquals(GhamzaPhase.Aborted, state.phase)
        assertEquals(GhamzaReducer.MAX_HISTORY, state.hostOnly.history.size)
        assertEquals(GhamzaAction.Abort, state.hostOnly.history.last())
        assertTrue(state.public.scores.values.all { it == 0 })
        val codec = GhamzaSnapshotCodec()
        assertEquals(state, codec.decode(codec.encode(state)))
        assertEquals(state, reducer.apply(state, GhamzaAction.Abort))
        val public = GhamzaCodec.decodePublic(GhamzaCodec.encodePublic(state))
        players.forEach { player ->
            val payload = GhamzaCodec.encodePrivate(state, player.id)
            assertEquals(GhamzaPrivate(), GhamzaCodec.decodePlayer(public, payload, player.id).privatePerPlayer.getValue(player.id))
            assertFailsWith<IllegalArgumentException> { GhamzaCodec.decodePlayer(public, payload, PlayerId("outsider")) }
        }
    }

    @Test
    fun fabricated_attempt_ledgers_and_early_scores_are_not_valid_public_snapshots() {
        var state = reducer.initial(players, GhamzaSettings(attempts = 3), 11)
        for (player in players) state = reducer.apply(state, GhamzaAction.Ready(player.id, state.public.token))
        val guest = players.first { it.id != state.hostOnly.winker }.id
        state = reducer.apply(state, GhamzaAction.Winked(guest, state.public.token, 1))
        val report = state.public.recentReports.single()
        val invalid = listOf(
            state.copy(public = state.public.copy(recentReports = listOf(report.copy(attempt = 2)))),
            state.copy(public = state.public.copy(recentReports = listOf(report.copy(number = 2)))),
            state.copy(public = state.public.copy(recentReports = emptyList())),
            state.copy(public = state.public.copy(scores = state.public.scores + (guest to 1))),
        )
        invalid.forEach { corrupt ->
            assertFalse(GhamzaValidation.publicState(corrupt))
            assertFailsWith<IllegalArgumentException> { GhamzaCodec.encodePublic(corrupt) }
        }
        assertEquals(state, GhamzaSnapshotCodec().decode(GhamzaSnapshotCodec().encode(state)))
    }
}
