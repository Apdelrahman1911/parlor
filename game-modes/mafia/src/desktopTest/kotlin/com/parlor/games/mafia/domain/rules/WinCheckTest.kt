package com.parlor.games.mafia.domain.rules

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import kotlin.test.Test

class WinCheckTest {

    private val m1 = PlayerId("m1")
    private val m2 = PlayerId("m2")
    private val t1 = PlayerId("t1")
    private val t2 = PlayerId("t2")
    private val t3 = PlayerId("t3")
    private val t4 = PlayerId("t4")

    private val rolesAllInitial = mapOf(
        m1 to Role.Mafia,
        m2 to Role.Mafia,
        t1 to Role.Detective,
        t2 to Role.Doctor,
        t3 to Role.Civilian,
        t4 to Role.Civilian,
    )

    @Test
    fun town_wins_when_no_mafia_alive() {
        val alive = setOf(t1, t2, t3) // no mafia
        assertThat(WinCheck.evaluate(alive, rolesAllInitial)).isEqualTo(Team.Town)
    }

    @Test
    fun mafia_wins_when_mafia_equals_town() {
        // 1 mafia + 1 town → mafia >= non-mafia → Mafia win
        val alive = setOf(m1, t1)
        assertThat(WinCheck.evaluate(alive, rolesAllInitial)).isEqualTo(Team.Mafia)
    }

    @Test
    fun mafia_wins_when_mafia_majority() {
        val alive = setOf(m1, m2, t1)
        assertThat(WinCheck.evaluate(alive, rolesAllInitial)).isEqualTo(Team.Mafia)
    }

    @Test
    fun game_continues_when_mafia_strict_minority() {
        // 1 mafia + 2 town → game ongoing
        val alive = setOf(m1, t1, t2)
        assertThat(WinCheck.evaluate(alive, rolesAllInitial)).isNull()
    }

    @Test
    fun all_dead_yields_town_win_boundary() {
        // alive = empty → mafiaCount = 0 → Town win (no mafia). This is a
        // boundary the reducer never actually reaches but the helper should
        // be total.
        assertThat(WinCheck.evaluate(emptySet(), rolesAllInitial)).isEqualTo(Team.Town)
    }
}
