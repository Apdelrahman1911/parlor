package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.dominoes.domain.DominoCompetition
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoVariant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DominoMultiplayerTest {
    @Test
    fun complete_default_draw_block_and_team_matches_sync_every_move_score_and_rematch() = runTest {
        val configurations = DominoVariant.entries.map { DominoSettings(it, 51) } +
            DominoSettings(target = 151, competition = DominoCompetition.Teams)
        for (count in 2..4) for (settings in configurations.filter { it.supports(count) }) {
            val fixture = MultiplayerGameFixture(
                this, DominoGameShellBinding(DominoDefinition()), gamePlayers(count), seed = count.toLong(),
                caseId = settings.caseId,
            )
            try {
                fixture.attachPeers()
                var actions = 0
                while (fixture.session.currentState().phase != DominoPhase.MatchResult) {
                    assertTrue(++actions < 8192, "A bounded match must terminate")
                    val state = fixture.session.currentState()
                    if (state.phase == DominoPhase.RoundResult) {
                        fixture.perform(DominoAction.NextRound(state.public.token))
                    } else {
                        val by = checkNotNull(state.public.turn)
                        val own = fixture.spec.definition.projectionPolicy().toPlayer(state, by).state
                        val tile = own.privatePerPlayer.getValue(by).hand.firstOrNull {
                            DominoRules.playableEnds(own, by, it).isNotEmpty()
                        }
                        val action = when {
                            tile != null -> DominoAction.Place(
                                by, state.public.token, state.public.move, tile.id,
                                DominoRules.playableEnds(own, by, tile).first(),
                            )
                            DominoRules.canDraw(own, by) -> DominoAction.Draw(by, state.public.token, state.public.move)
                            else -> DominoAction.Pass(by, state.public.token, state.public.move)
                        }
                        fixture.perform(action, by)
                    }
                }
                val finished = fixture.session.currentState()
                assertTrue(finished.public.matchWinners.isNotEmpty())
                if (settings.competition == DominoCompetition.Teams) {
                    assertEquals(2, finished.public.scores.size)
                    assertEquals(2, finished.public.matchWinners.size)
                }
                fixture.perform(DominoAction.Rematch(finished.public.token))
                val rematch = fixture.session.currentState()
                assertEquals(DominoPhase.Playing, rematch.phase)
                assertTrue(rematch.public.scores.values.all { it == 0 })
                assertEquals(finished.public.token + 1, rematch.public.token)
                assertTrue(rematch.public.chain.isEmpty())
                assertEquals(DominoRules.tiles(rematch.public.settings, count).size,
                    rematch.public.stockCount + rematch.public.handCounts.values.sum())
            } finally {
                fixture.close()
            }
        }
    }
}
