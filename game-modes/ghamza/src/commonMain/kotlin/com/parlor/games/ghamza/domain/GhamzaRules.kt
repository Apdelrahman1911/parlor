package com.parlor.games.ghamza.domain

import com.parlor.core.ids.PlayerId

/** Lives and final-guess elimination exist only within the current round. */
object GhamzaRules {
    fun livesRemaining(state: GhamzaState, player: PlayerId): Int {
        val reports = state.public.reports[player] ?: return 0
        return if (state.public.result?.loser == player) 0 else state.public.settings.attempts - reports
    }

    fun isEliminated(state: GhamzaState, player: PlayerId): Boolean =
        player in state.public.reports && livesRemaining(state, player) == 0
}
