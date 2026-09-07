package com.parlor.games.mafia.domain

import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.action.MafiaActionCodec
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.snapshot.isValidRecoveryState
import com.parlor.games.mafia.testing.MafiaDoctorFixture
import com.parlor.games.mafia.ui.screens.setup.MafiaSetupDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MafiaDoctorRulesTest {
    private val base = MafiaSettings(MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1))

    @Test
    fun repeat_is_default_off_for_every_supported_player_count(): Unit {
        assertFalse(base.doctorCanProtectSamePlayerConsecutively)
        for (count in MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS) {
            assertFalse(MafiaSettingsPresets.forPlayerCount(count).doctorCanProtectSamePlayerConsecutively)
        }
    }

    @Test
    fun enabled_setting_saves_same_living_target_for_four_consecutive_nights(): Unit {
        for (count in MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = true), count)
            game.firstNight()
            val target = game.civilianTargets.first()
            repeat(4) {
                game.resolveNight(protection = target, kill = target)
                assertTrue(requireNotNull(game.state.public.lastNight).wasSaved)
                assertEquals(target, game.state.hostOnly.nightLog.last().doctorProtect)
                assertEquals(target, game.state.privatePerPlayer.getValue(game.doctor).previousDoctorProtect)
                assertTrue(game.state.isValidRecoveryState())
                game.nextNight()
            }
            assertEquals(MafiaPhase.Night(5), game.state.phase)
        }
    }

    @Test
    fun disabled_setting_rejects_repeat_and_allows_alternating_targets(): Unit {
        val game = MafiaDoctorFixture(base)
        game.firstNight()
        val (first, second) = game.civilianTargets
        game.resolveNight(first, kill = first)
        game.nextNight()
        val before = game.state
        assertEquals(before, game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, first)))
        assertFalse(game.state.privatePerPlayer.getValue(game.doctor).nightChoiceSubmitted)
        game.resolveNight(second, kill = second)
        game.nextNight()
        game.resolveNight(first, kill = first)
        assertTrue(requireNotNull(game.state.public.lastNight).wasSaved)
        assertEquals(listOf(first, second, first), game.state.hostOnly.nightLog.map { it.doctorProtect })
        assertTrue(game.state.isValidRecoveryState())
    }

    @Test
    fun skipped_night_clears_previous_effective_target_but_consumes_submission(): Unit {
        for (repeatEnabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = repeatEnabled))
            game.firstNight()
            val target = game.civilianTargets.first()
            game.resolveNight(target)
            game.nextNight()
            game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, null))
            val skipped = game.state
            assertTrue(skipped.privatePerPlayer.getValue(game.doctor).nightChoiceSubmitted)
            assertEquals(skipped, game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, target)))
            game.resolveNight(null)
            assertEquals(null, game.state.hostOnly.nightLog.last().doctorProtect)
            assertEquals(null, game.state.privatePerPlayer.getValue(game.doctor).previousDoctorProtect)
            game.nextNight()
            game.resolveNight(target, kill = target)
            assertTrue(requireNotNull(game.state.public.lastNight).wasSaved)
            assertTrue(game.state.isValidRecoveryState())
        }
    }

    @Test
    fun repeat_and_self_protection_settings_are_independent(): Unit {
        for (repeatEnabled in listOf(false, true)) {
            for (selfEnabled in listOf(false, true)) {
                val settings = base.copy(
                    doctorCanProtectSamePlayerConsecutively = repeatEnabled,
                    doctorCanSelfHeal = selfEnabled,
                )
                val game = MafiaDoctorFixture(settings)
                game.firstNight()
                game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, game.doctor))
                assertEquals(selfEnabled, game.state.privatePerPlayer.getValue(game.doctor).nightChoiceSubmitted)
                if (selfEnabled) {
                    game.resolveNight(game.doctor)
                    game.nextNight()
                    game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, game.doctor))
                    assertEquals(
                        repeatEnabled,
                        game.state.privatePerPlayer.getValue(game.doctor).nightChoiceSubmitted,
                    )
                }
                assertTrue(game.state.isValidRecoveryState())
            }
        }
    }

    @Test
    fun first_valid_target_cannot_be_replaced_or_skipped_later_that_night(): Unit {
        for (repeatEnabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = repeatEnabled))
            game.firstNight()
            val (first, second) = game.civilianTargets
            val submitted = game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, first))
            assertTrue(submitted.privatePerPlayer.getValue(game.doctor).nightChoiceSubmitted)
            assertEquals(submitted, game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, first)))
            assertEquals(submitted, game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, second)))
            assertEquals(submitted, game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, null)))
        }
    }

    @Test
    fun enabling_repeats_does_not_allow_dead_targets_or_a_non_doctor_actor(): Unit {
        val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = true))
        game.firstNight()
        val (victim, protected) = game.civilianTargets
        game.resolveNight(protected, kill = victim)
        game.nextNight()
        val before = game.state
        assertFalse(before.public.roster.single { it.playerId == victim }.alive)
        assertEquals(before, game.apply(MafiaAction.SubmitDoctorProtect(game.doctor, victim)))
        assertEquals(before, game.apply(MafiaAction.SubmitDoctorProtect(protected, game.doctor)))
        assertFalse(game.state.privatePerPlayer.getValue(game.doctor).nightChoiceSubmitted)
    }

    @Test
    fun setup_draft_and_action_codec_commit_selection_once_without_changing_assignment(): Unit {
        val off = MafiaDoctorFixture(base)
        off.start()
        val chosen = MafiaSetupDraft.from(base).copy(doctorCanProtectSamePlayerConsecutively = true).applyTo(base)
        val game = MafiaDoctorFixture(base)
        val action = MafiaAction.ConfigureAndStart(chosen)
        val decoded = MafiaActionCodec.decode(MafiaActionCodec.encode(action))
        assertEquals(action, decoded)
        game.apply(decoded)
        assertEquals(chosen, game.state.public.settings)
        assertEquals(off.state.hostOnly.fullRoleMap, game.state.hostOnly.fullRoleMap)
        val started = game.state
        assertEquals(started, game.apply(MafiaAction.ConfigureAndStart(base)))
        assertEquals(started, game.apply(MafiaAction.ApplySettings(base)))
    }
}
