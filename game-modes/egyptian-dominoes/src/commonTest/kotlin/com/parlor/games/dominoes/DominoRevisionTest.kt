package com.parlor.games.dominoes

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoCompetition
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoScoring
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.domain.DominoTile
import com.parlor.games.dominoes.domain.DominoValidation
import com.parlor.games.dominoes.domain.DominoVariant
import com.parlor.games.dominoes.protocol.DominoCodec
import com.parlor.games.dominoes.protocol.DominoSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DominoRevisionTest {
    private val reducer = DominoReducer()
    private fun players(count: Int) = List(count) { Player(PlayerId("p$it"), "Seat ${it + 1}", it) }

    @Test fun default_settings_and_all_targets_are_canonical_and_teams_require_four_default_seats() {
        assertEquals(DominoVariant.Default, DominoSettings().variant)
        assertEquals(listOf(51, 101, 151), DominoSettings.TARGETS)
        for (target in DominoSettings.TARGETS) for (variant in DominoVariant.entries) {
            val settings = DominoSettings(variant, target)
            assertEquals(settings, DominoSettings.fromCaseId(settings.caseId.raw))
        }
        for (target in listOf(0, 50, 100, 150, 201)) assertFails { DominoSettings(target = target) }
        assertNull(DominoSettings.fromCaseId("draw-100"))
        assertNull(DominoSettings.fromCaseId("default-101-teams-extra"))
        assertFails { DominoSettings(DominoVariant.Draw, competition = DominoCompetition.Teams) }
        assertFails { DominoSettings(DominoVariant.Block, competition = DominoCompetition.Teams) }
        val teams = DominoSettings(competition = DominoCompetition.Teams)
        assertEquals(teams, DominoSettings.fromCaseId(teams.caseId.raw))
        for (count in 2..3) assertFails { reducer.initial(players(count), teams, 1) }
        assertTrue(teams.supports(4))
    }

    @Test fun two_player_default_has_exactly_the_draw_deal_moves_stock_and_scoring() {
        for (seed in 0L..30L) {
            var normal = reducer.initial(players(2), DominoSettings(), seed)
            var draw = reducer.initial(players(2), DominoSettings(DominoVariant.Draw), seed)
            repeat(DominoRules.MAX_MOVE) {
                assertEquals(normal, draw.copy(public = draw.public.copy(settings = normal.public.settings)))
                if (normal.phase == DominoPhase.Playing) {
                    val action = legal(normal)
                    assertEquals(action, legal(draw))
                    normal = reducer.apply(normal, action)
                    draw = reducer.apply(draw, action)
                }
            }
            assertNotEquals(DominoPhase.Playing, normal.phase)
        }
    }

    @Test fun three_player_default_removes_only_double_blank_and_never_permits_drawing() {
        for (seed in 0L..30L) {
            var state = reducer.initial(players(3), DominoSettings(), seed)
            val dealt = state.privatePerPlayer.values.flatMap { it.hand }
            assertEquals(listOf(9, 9, 9), state.public.handCounts.values.toList())
            assertEquals(27, dealt.distinct().size)
            assertEquals(DominoTile.Set.toSet() - DominoTile(0, 0), dealt.toSet())
            assertEquals(6, dealt.count { it.low == 0 })
            assertTrue(state.hostOnly.stock.isEmpty())
            repeat(DominoRules.MAX_MOVE) {
                assertEquals(0, state.public.stockCount)
                if (state.phase == DominoPhase.Playing) {
                    val by = state.public.turn!!
                    assertFalse(DominoRules.canDraw(state, by))
                    assertEquals(state, reducer.apply(state, DominoAction.Draw(by, state.public.token, state.public.move)))
                    state = reducer.apply(state, legal(state))
                }
            }
            assertNotEquals(DominoPhase.Playing, state.phase)
            assertEquals(state, DominoSnapshotCodec().decode(DominoSnapshotCodec().encode(state)))
        }
        val state = reducer.initial(players(3), DominoSettings(), 1)
        val id = state.players.first().id
        val own = state.privatePerPlayer.getValue(id)
        val corrupt = state.copy(privatePerPlayer = state.privatePerPlayer + (id to own.copy(hand = own.hand.drop(1) + DominoTile(0, 0))))
        assertFails { DominoCodec.encodePrivate(corrupt, id) }
        assertFails { DominoSnapshotCodec().encode(corrupt) }
    }

    @Test fun opposite_seats_share_one_score_but_never_their_private_hands() {
        val state = reducer.initial(players(4), DominoSettings(competition = DominoCompetition.Teams), 11)
        val ids = state.players.map { it.id }
        assertEquals(mapOf(ids[0] to listOf(ids[0], ids[2]), ids[1] to listOf(ids[1], ids[3])),
            DominoScoring.sides(state.players, state.public.settings))
        assertEquals(setOf(ids[0], ids[1]), state.public.scores.keys)
        val scored = state.copy(public = state.public.copy(scores = mapOf(ids[0] to 25, ids[1] to 9)))
        assertEquals(25, DominoScoring.scoreFor(scored, ids[2]))
        assertEquals(9, DominoScoring.scoreFor(scored, ids[3]))
        val public = DominoCodec.decodePublic(DominoCodec.encodePublic(scored))
        for (id in ids) {
            val own = DominoCodec.decodePlayer(public, DominoCodec.encodePrivate(scored, id), id)
            assertEquals(setOf(id), own.privatePerPlayer.keys)
            assertEquals(DominoProjection.toPlayer(scored, id).state, own)
        }
        val duplicated = scored.copy(public = scored.public.copy(scores = scored.public.scores + (ids[2] to 25)))
        assertFalse(DominoValidation.publicState(duplicated))
        assertFails { DominoCodec.encodePublic(duplicated) }
    }

    @Test fun team_points_exclude_partner_and_blocked_totals_and_ties_are_combined() {
        val state = reducer.initial(players(4), DominoSettings(competition = DominoCompetition.Teams), 3)
        val ids = state.players.map { it.id }
        fun pips(a: Int, b: Int, c: Int, d: Int) = ids.zip(listOf(a, b, c, d)).toMap()
        assertEquals(35, DominoScoring.points(state, pips(0, 20, 40, 15), ids[0], false))
        assertEquals(35, DominoScoring.points(state, pips(40, 20, 0, 15), ids[2], false))
        val blocked = pips(4, 2, 6, 20)
        assertEquals(ids[0], DominoScoring.blockedWinner(state, blocked))
        assertEquals(12, DominoScoring.points(state, blocked, ids[0], true))
        assertNull(DominoScoring.blockedWinner(state, pips(4, 2, 6, 8)))
        assertEquals(0, DominoScoring.points(state, pips(4, 2, 6, 8), null, true))
        assertEquals(ids[0], DominoScoring.blockedWinner(state, pips(5, 7, 5, 6)), "Seat breaks partner pip ties")
    }

    @Test fun shutout_thresholds_apply_only_in_151_mode_against_all_zero_opponents() {
        for (count in 2..4) {
            val ids = players(count).map { it.id }
            for (target in DominoSettings.TARGETS) for (points in listOf(100, 101, 102, 150, 151)) {
                val settings = DominoSettings(target = target)
                val scores = ids.associateWith { if (it == ids[0]) points else 0 }
                assertEquals(target == 151 && points >= 101, DominoScoring.isShutout(settings, scores))
                assertEquals(points >= target || target == 151 && points >= 101, DominoScoring.reachedTarget(settings, scores))
                assertFalse(DominoScoring.isShutout(settings, scores + (ids[1] to 1)))
            }
        }
    }

    @Test fun reducer_automatically_ends_shutouts_and_targets_for_individuals_and_both_teammates() {
        for (competition in DominoCompetition.entries) {
            val settings = DominoSettings(target = 151, competition = competition)
            // Find a legal finishing hand for each seat, so either teammate must credit its shared side.
            for (winner in players(4).map { it.id }) {
                val ending = (0L..200L).asSequence().map { ending(settings, it) }.first {
                    val r = it.finished.public.result!!
                    r.winner == winner && r.points in 1..100
                }
                val side = DominoScoring.sideOf(ending.before, winner)
                val points = ending.finished.public.result!!.points
                val before = ending.before.copy(public = ending.before.public.copy(
                    scores = ending.before.public.scores.mapValues { (id, _) -> if (id == side) 101 - points else 0 },
                ))
                assertTrue(DominoValidation.publicState(before))
                val finished = reducer.apply(before, ending.action)
                assertEquals(DominoPhase.MatchResult, finished.phase)
                assertEquals(101, DominoScoring.scoreFor(finished, winner))
                assertEquals(DominoScoring.sides(finished.players, settings).getValue(side).toSet(), finished.public.matchWinners)
                assertEquals(finished, reducer.apply(finished, ending.action))
                assertEquals(finished, reducer.apply(finished, DominoAction.NextRound(finished.public.token)))
                assertNotNull(DominoCodec.decodePublic(DominoCodec.encodePublic(finished)).public.result)
                val opponent = before.public.scores.keys.first { it != side }
                val nonShutout = before.copy(public = before.public.copy(scores = before.public.scores + (opponent to 1)))
                assertEquals(DominoPhase.RoundResult, reducer.apply(nonShutout, ending.action).phase)
                for (target in DominoSettings.TARGETS) {
                    if (points > target) continue
                    val atTarget = nonShutout.copy(public = nonShutout.public.copy(
                        settings = settings.copy(target = target), scores = nonShutout.public.scores + (side to target - points),
                    ))
                    val ended = reducer.apply(atTarget, ending.action)
                    assertEquals(DominoPhase.MatchResult, ended.phase)
                    assertEquals(target, DominoScoring.scoreFor(ended, winner))
                }
            }
        }
    }

    private data class HandEnding(val before: DominoState, val action: DominoAction, val finished: DominoState)

    private fun ending(settings: DominoSettings, seed: Long): HandEnding {
        var state = reducer.initial(players(4), settings, seed)
        repeat(DominoRules.MAX_MOVE) {
            val action = legal(state)
            val next = reducer.apply(state, action)
            if (next.phase != DominoPhase.Playing) return HandEnding(state, action, next)
            state = next
        }
        error("Legal hand did not terminate")
    }

    private fun legal(state: DominoState): DominoAction {
        val by = state.public.turn!!
        val tile = state.privatePerPlayer.getValue(by).hand.firstOrNull { DominoRules.playableEnds(state, by, it).isNotEmpty() }
        return when {
            tile != null -> DominoAction.Place(by, state.public.token, state.public.move, tile.id, DominoRules.playableEnds(state, by, tile).first())
            DominoRules.canDraw(state, by) -> DominoAction.Draw(by, state.public.token, state.public.move)
            else -> DominoAction.Pass(by, state.public.token, state.public.move)
        }
    }
}
