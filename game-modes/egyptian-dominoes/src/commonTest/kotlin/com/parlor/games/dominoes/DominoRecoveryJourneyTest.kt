package com.parlor.games.dominoes

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.domain.DominoVariant
import com.parlor.games.dominoes.protocol.DominoCodec
import com.parlor.games.dominoes.protocol.DominoSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DominoRecoveryJourneyTest {
    private val reducer = DominoReducer()

    @Test
    fun interrupted_matches_have_the_same_tiles_scores_and_winners_as_uninterrupted_matches() {
        for (count in 2..4) for (variant in DominoVariant.entries) {
            val players = List(count) { Player(PlayerId("p$it"), "Player $it", it) }
            var clean = reducer.initial(players, DominoSettings(variant, target = 51), 73)
            var recovered = clean
            var steps = 0
            while (clean.phase != DominoPhase.MatchResult) {
                assertTrue(steps++ < DominoRules.MAX_HANDS * DominoRules.MAX_MOVE)
                val action = nextAction(clean)
                if (steps % 7 == 0) recovered = interruptAndResume(recovered, action)
                clean = reducer.apply(clean, action)
                recovered = reducer.apply(recovered, action)
                assertEquals(recovered, reducer.apply(recovered, action), "Duplicate delivery must be inert")
                assertEquals(clean.public, recovered.public)
                assertEquals(clean.privatePerPlayer, recovered.privatePerPlayer)
                assertEquals(clean.phase, recovered.phase)
            }
            val codec = DominoSnapshotCodec()
            assertEquals(recovered, codec.decode(codec.encode(recovered)))
            assertEquals(clean.public.matchWinners, recovered.public.matchWinners)
        }
    }

    private fun interruptAndResume(state: DominoState, blockedAction: DominoAction): DominoState {
        val first = state.players.first().id
        val last = state.players.last().id
        var paused = reducer.apply(state, DominoAction.Disconnected(first))
        paused = reducer.apply(paused, DominoAction.Disconnected(last))
        assertEquals(paused, reducer.apply(paused, blockedAction))
        paused = reducer.apply(paused, DominoAction.Reconnected(last))
        assertEquals(paused, reducer.apply(paused, blockedAction), "One returning seat must not release another seat's pause")
        val resumed = reducer.apply(paused, DominoAction.Reconnected(first))
        assertEquals(state.public, resumed.public)
        assertEquals(state.privatePerPlayer, resumed.privatePerPlayer)
        val public = DominoCodec.decodePublic(DominoCodec.encodePublic(resumed))
        state.players.forEach { player ->
            val own = DominoCodec.decodePlayer(public, DominoCodec.encodePrivate(resumed, player.id), player.id)
            assertEquals(setOf(player.id), own.privatePerPlayer.keys)
            assertEquals(state.privatePerPlayer.getValue(player.id), own.privatePerPlayer.getValue(player.id))
        }
        return resumed
    }

    private fun nextAction(state: DominoState): DominoAction {
        if (state.phase == DominoPhase.RoundResult) return DominoAction.NextRound(state.public.token)
        val by = checkNotNull(state.public.turn)
        val tile = state.privatePerPlayer.getValue(by).hand.firstOrNull { DominoRules.playableEnds(state, by, it).isNotEmpty() }
        return when {
            tile != null -> DominoAction.Place(by, state.public.token, state.public.move, tile.id,
                DominoRules.playableEnds(state, by, tile).first())
            DominoRules.canDraw(state, by) -> DominoAction.Draw(by, state.public.token, state.public.move)
            else -> DominoAction.Pass(by, state.public.token, state.public.move)
        }
    }
}
