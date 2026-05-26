package com.parlor.games.mafia.domain.rules

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.team

/**
 * Deterministically assigns roles to players. Civilians fill the remainder so
 * total assigned roles equal player count.
 *
 * Returns a map from PlayerId → Role plus a per-player set of known teammates
 * (non-empty only for Mafia, which knows the other Mafia).
 */
object RoleAssignment {

    data class Result(
        val roles: Map<PlayerId, Role>,
        val knownTeammates: Map<PlayerId, Set<PlayerId>>,
    )

    fun assign(
        players: List<Player>,
        counts: MafiaRoleCounts,
        random: RandomSource,
    ): Result {
        require(counts.mafia >= 1) { "Need at least 1 Mafia" }
        val total = counts.mafia + counts.detective + counts.doctor
        require(total <= players.size) { "Role counts exceed player count" }

        val shuffled = random.shuffled(players)
        val pool: ArrayDeque<Player> = ArrayDeque(shuffled)

        val mafia = (1..counts.mafia).map { pool.removeFirst().id }
        val detective = (1..counts.detective).map { pool.removeFirst().id }
        val doctor = (1..counts.doctor).map { pool.removeFirst().id }
        val civilian = pool.map { it.id }

        val roles: Map<PlayerId, Role> = buildMap {
            mafia.forEach { put(it, Role.Mafia) }
            detective.forEach { put(it, Role.Detective) }
            doctor.forEach { put(it, Role.Doctor) }
            civilian.forEach { put(it, Role.Civilian) }
        }

        val mafiaSet = mafia.toSet()
        val knownTeammates: Map<PlayerId, Set<PlayerId>> = roles.mapValues { (id, role) ->
            if (role.team == com.parlor.games.mafia.domain.state.Team.Mafia) mafiaSet - id else emptySet()
        }

        return Result(roles = roles, knownTeammates = knownTeammates)
    }
}
