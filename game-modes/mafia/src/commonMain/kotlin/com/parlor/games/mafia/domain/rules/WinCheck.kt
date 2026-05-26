package com.parlor.games.mafia.domain.rules

import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.team

/**
 * Pure win-condition evaluation.
 *
 * - Town wins when no Mafia remains alive.
 * - Mafia wins when Mafia count ≥ non-Mafia count among living players.
 * - Otherwise: game continues.
 */
object WinCheck {

    fun evaluate(
        alive: Set<PlayerId>,
        rolesByPlayer: Map<PlayerId, Role>,
    ): Team? {
        val livingMafia = alive.count { rolesByPlayer[it]?.team == Team.Mafia }
        val livingTown = alive.size - livingMafia
        return when {
            livingMafia == 0 -> Team.Town
            livingMafia >= livingTown -> Team.Mafia
            else -> null
        }
    }
}
