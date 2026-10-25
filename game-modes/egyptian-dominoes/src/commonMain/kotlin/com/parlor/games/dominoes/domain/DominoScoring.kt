package com.parlor.games.dominoes.domain

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player

/** Stable seat order determines partners; only one score is stored for each side. */
object DominoScoring {
    private const val TEAM_COUNT = 2
    private const val LONG_TARGET = 151
    private const val SHUTOUT_TARGET = 101

    fun sides(players: List<Player>, settings: DominoSettings): Map<PlayerId, List<PlayerId>> =
        if (settings.competition == DominoCompetition.Teams) {
            players.groupBy { it.seat % TEAM_COUNT }.values.associate { team -> team.first().id to team.map { it.id } }
        } else players.associate { it.id to listOf(it.id) }

    fun sideOf(state: DominoState, player: PlayerId): PlayerId =
        sides(state.players, state.public.settings).entries.first { player in it.value }.key

    fun scoreFor(state: DominoState, player: PlayerId): Int = state.public.scores.getValue(sideOf(state, player))

    fun teamNumber(state: DominoState, player: PlayerId): Int? =
        if (state.public.settings.competition == DominoCompetition.Teams) {
            state.players.first { it.id == player }.seat % TEAM_COUNT + 1
        } else null

    fun sidePips(state: DominoState, pips: Map<PlayerId, Int>): Map<PlayerId, Int> =
        sides(state.players, state.public.settings).mapValues { (_, members) -> members.sumOf { pips.getValue(it) } }

    /** A team tie scores nobody. The lowest-pip winning teammate leads the next hand (seat breaks ties). */
    fun blockedWinner(state: DominoState, pips: Map<PlayerId, Int>): PlayerId? {
        val totals = sidePips(state, pips)
        val side = totals.filterValues { it == totals.values.min() }.keys.singleOrNull() ?: return null
        return sides(state.players, state.public.settings).getValue(side).minBy { pips.getValue(it) }
    }

    fun points(state: DominoState, pips: Map<PlayerId, Int>, winner: PlayerId?, blocked: Boolean): Int {
        if (winner == null) return 0
        val side = sideOf(state, winner)
        val totals = sidePips(state, pips)
        return totals.filterKeys { it != side }.values.sum() - if (blocked) totals.getValue(side) else 0
    }

    fun isShutout(settings: DominoSettings, scores: Map<PlayerId, Int>): Boolean = settings.target == LONG_TARGET &&
        scores.count { it.value > 0 } == 1 && scores.values.any { it >= SHUTOUT_TARGET }

    fun reachedTarget(settings: DominoSettings, scores: Map<PlayerId, Int>): Boolean =
        scores.values.any { it >= settings.target } || isShutout(settings, scores)

    fun matchWinners(state: DominoState, scores: Map<PlayerId, Int>): Set<PlayerId> {
        val best = scores.values.max()
        val sides = sides(state.players, state.public.settings)
        return scores.filterValues { it == best }.keys.flatMap { sides.getValue(it) }.toSet()
    }
}
