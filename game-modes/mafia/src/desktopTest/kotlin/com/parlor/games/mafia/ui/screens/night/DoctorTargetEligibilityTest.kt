package com.parlor.games.mafia.ui.screens.night

import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.testing.MafiaDoctorFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the production predicate used by both routers, with canonical and own-only state. */
class DoctorTargetEligibilityTest {
    @Test
    fun canonical_local_and_projected_lan_choices_honor_both_independent_settings(): Unit {
        for (repeatEnabled in listOf(false, true)) {
            for (selfEnabled in listOf(false, true)) {
                val game = MafiaDoctorFixture(
                    MafiaSettings(
                        roleCounts = MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1),
                        doctorCanProtectSamePlayerConsecutively = repeatEnabled,
                        doctorCanSelfHeal = selfEnabled,
                    ),
                )
                game.firstNight()
                val target = game.civilianTargets.first()
                assertTrue(isEligible(game.state, game, target))
                game.resolveNight(target)
                game.nextNight()
                val projected = MafiaProjectionPolicy.toPlayer(game.state, game.doctor).state
                for (state in listOf(game.state, projected)) {
                    assertEquals(repeatEnabled, isEligible(state, game, target))
                    assertEquals(selfEnabled, isEligible(state, game, game.doctor))
                    assertTrue(isEligible(state, game, game.civilianTargets[1]))
                }
                game.resolveNight(null)
                game.nextNight()
                assertTrue(isEligible(game.state, game, target))
            }
        }
    }

    @Test
    fun eligibility_matches_reducer_acceptance_before_each_first_submission(): Unit {
        for (repeatEnabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(
                MafiaSettings(
                    roleCounts = MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1),
                    doctorCanProtectSamePlayerConsecutively = repeatEnabled,
                ),
            )
            game.firstNight()
            val target = game.civilianTargets.first()
            game.resolveNight(target)
            game.nextNight()
            val allowed = isEligible(game.state, game, target)
            game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, target))
            assertEquals(allowed, game.state.privatePerPlayer.getValue(game.doctor).nightChoiceSubmitted)
            assertFalse(isEligible(game.state, game, game.doctor))
        }
    }

    private fun isEligible(
        state: MafiaState,
        game: MafiaDoctorFixture,
        target: com.parlor.core.ids.PlayerId,
    ): Boolean = isDoctorTargetEligible(
        target = target,
        doctor = game.doctor,
        previousProtection = state.privatePerPlayer.getValue(game.doctor).previousDoctorProtect,
        settings = state.public.settings,
    )
}
