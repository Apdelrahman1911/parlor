package com.parlor.games.ghamza.domain

import com.parlor.core.ids.PlayerId

object GhamzaValidation {
    fun publicState(state: GhamzaState): Boolean {
        if (!GhamzaRoster.isValidRoster(state.players)) return false
        val p = state.public
        val ids = state.players.map { it.id }.toSet()
        if (!validBounds(p, ids) || !validReports(state)) return false
        if (p.result == null && state.phase != GhamzaPhase.Aborted && p.scores.values.sum() != p.round - 1) return false
        val active = p.reports.filterValues { it < p.settings.attempts }.keys
        if (state.phase != GhamzaPhase.Reveal && state.phase != GhamzaPhase.Aborted && p.ready != ids) return false
        return when (state.phase) {
            GhamzaPhase.Reveal -> p.reports.values.all { it == 0 } && p.result == null && p.finalGuesser == null && p.ready.size < ids.size
            GhamzaPhase.Social -> active.size >= MIN_SOCIAL_ACTIVE && p.result == null && p.finalGuesser == null
            GhamzaPhase.FinalGuess -> active.size == FINAL_ACTIVE && p.finalGuesser in active && p.result == null
            GhamzaPhase.RoundResult -> p.round < p.settings.rounds && validResult(state, active)
            GhamzaPhase.MatchResult -> p.round == p.settings.rounds && validResult(state, active)
            GhamzaPhase.Aborted -> p.result == null && p.finalGuesser == null && p.disconnected.isEmpty()
        }
    }

    private fun validBounds(p: GhamzaPublic, ids: Set<PlayerId>): Boolean {
        if (p.token !in 1..GhamzaReducer.MAX_TOKEN || p.round !in 1..p.settings.rounds) return false
        if (!ids.containsAll(p.ready) || !ids.containsAll(p.disconnected) || p.reports.keys != ids || p.scores.keys != ids) return false
        if (p.reports.values.any { it !in 0..p.settings.attempts } || p.scores.values.any { it !in 0..p.round }) return false
        return p.reports.values.any { it == 0 } && p.scores.values.sum() in (p.round - 1)..p.round
    }

    fun playerState(state: GhamzaState, id: PlayerId): Boolean {
        if (!publicState(state) || state.hostOnly != GhamzaHostOnly() || state.privatePerPlayer.keys != setOf(id)) return false
        if (state.players.none { it.id == id }) return false
        val own = state.privatePerPlayer.getValue(id)
        if (state.phase == GhamzaPhase.Aborted) return own == GhamzaPrivate()
        if (own.role == null) return false
        if (own.role == GhamzaRole.Winker && (state.public.reports[id] != 0 || state.public.finalGuesser == id)) return false
        val result = state.public.result
        return result == null || (own.role == GhamzaRole.Winker) == (result.winker == id)
    }

    private fun validReports(state: GhamzaState): Boolean {
        val reports = state.public.recentReports
        val total = state.public.reports.values.sum()
        if (reports.size != minOf(total, GhamzaReducer.RECENT_REPORT_LIMIT)) return false
        if (reports.map { it.number } != ((total - reports.size + 1)..total).toList()) return false
        if (!reports.all { it.player in state.public.reports && it.attempt in 1..state.public.settings.attempts }) return false
        return reports.groupBy { it.player }.all { (id, recent) ->
            val current = state.public.reports.getValue(id)
            recent.map { it.attempt } == ((current - recent.size + 1)..current).toList()
        }
    }

    private fun validResult(state: GhamzaState, active: Set<PlayerId>): Boolean {
        val p = state.public
        val result = p.result ?: return false
        if (active != setOf(result.winker, result.guesser) || result.winker == result.guesser) return false
        if (p.finalGuesser != result.guesser) return false
        if (result.guessed !in p.reports || result.guessed == result.guesser || p.reports[result.winker] != 0) return false
        val winner = if (result.guessed == result.winker) result.guesser else result.winker
        return result.winner == winner && p.scores.getValue(winner) > 0 && p.scores.values.sum() == p.round
    }

    private const val MIN_SOCIAL_ACTIVE = 3
    private const val FINAL_ACTIVE = 2
}

object GhamzaActionValidation {
    fun valid(action: GhamzaAction): Boolean = when (action) {
        is GhamzaAction.Ready -> actor(action.by, action.token)
        is GhamzaAction.Winked -> actor(action.by, action.token) && action.attempt in 1..3
        is GhamzaAction.Guess -> actor(action.by, action.token) && GhamzaRoster.isValidPlayerId(action.target.raw)
        is GhamzaAction.NextRound -> action.token in 1..GhamzaReducer.MAX_TOKEN
        is GhamzaAction.Rematch -> action.token in 1..GhamzaReducer.MAX_TOKEN
        else -> false
    }
    private fun actor(by: PlayerId, token: Long): Boolean = GhamzaRoster.isValidPlayerId(by.raw) && token in 1..GhamzaReducer.MAX_TOKEN
}
