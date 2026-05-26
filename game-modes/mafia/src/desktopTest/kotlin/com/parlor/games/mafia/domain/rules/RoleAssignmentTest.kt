package com.parlor.games.mafia.domain.rules

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.team
import kotlin.test.Test

class RoleAssignmentTest {

    private fun players(n: Int): List<Player> =
        (0 until n).map { Player(PlayerId("p$it"), "P$it", seat = it) }

    @Test
    fun every_player_gets_exactly_one_role() {
        val ps = players(7)
        val counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1) // 3 civ
        val result = RoleAssignment.assign(ps, counts, RandomSource.seeded(42L))

        assertThat(result.roles.keys).containsExactlyInAnyOrder(*ps.map { it.id }.toTypedArray())
        assertThat(result.roles.size).isEqualTo(7)
    }

    @Test
    fun counts_match_settings_with_civilians_as_remainder() {
        val ps = players(9)
        val counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1) // 5 civ
        val result = RoleAssignment.assign(ps, counts, RandomSource.seeded(1L))

        val tally = result.roles.values.groupingBy { it }.eachCount()
        assertThat(tally[Role.Mafia]).isEqualTo(2)
        assertThat(tally[Role.Detective]).isEqualTo(1)
        assertThat(tally[Role.Doctor]).isEqualTo(1)
        assertThat(tally[Role.Civilian]).isEqualTo(5)
    }

    @Test
    fun mafia_members_know_their_teammates_exactly() {
        val ps = players(7)
        val counts = MafiaRoleCounts(mafia = 3, detective = 1, doctor = 1)
        val result = RoleAssignment.assign(ps, counts, RandomSource.seeded(7L))

        val mafia = result.roles.filterValues { it == Role.Mafia }.keys
        for (m in mafia) {
            val teammates = result.knownTeammates.getValue(m)
            assertThat(teammates).isEqualTo(mafia - m)
        }
    }

    @Test
    fun non_mafia_have_empty_known_teammates() {
        val ps = players(8)
        val counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
        val result = RoleAssignment.assign(ps, counts, RandomSource.seeded(13L))

        for ((id, role) in result.roles) {
            if (role.team != Team.Mafia) {
                assertThat(result.knownTeammates.getValue(id)).isEqualTo(emptySet<PlayerId>())
            }
        }
    }

    @Test
    fun deterministic_for_same_seed() {
        val ps = players(7)
        val counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
        val a = RoleAssignment.assign(ps, counts, RandomSource.seeded(99L))
        val b = RoleAssignment.assign(ps, counts, RandomSource.seeded(99L))
        assertThat(a.roles).isEqualTo(b.roles)
    }

    @Test
    fun differs_for_different_seeds_in_general() {
        // Best-effort: try a few seeds and check that at least one differs.
        val ps = players(8)
        val counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
        val base = RoleAssignment.assign(ps, counts, RandomSource.seeded(1L))
        val differs = (2L..6L).any { seed ->
            RoleAssignment.assign(ps, counts, RandomSource.seeded(seed)).roles != base.roles
        }
        assertThat(differs).isEqualTo(true)
    }
}
