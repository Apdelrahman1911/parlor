package com.parlor.games.mafia.domain.settings

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test

/**
 * The validator is the gatekeeper for ApplySettings: the reducer refuses to
 * commit an invalid configuration. Every rejection branch needs its own
 * test so a future relaxation can be detected.
 */
class MafiaSettingsValidationTest {

    private fun base(
        mafia: Int = 1,
        detective: Int = 1,
        doctor: Int = 0,
        maxRevotes: Int = 1,
        nightSec: Int? = null,
        discussionSec: Int? = null,
        voteSec: Int? = null,
    ) = MafiaSettings(
        roleCounts = MafiaRoleCounts(mafia = mafia, detective = detective, doctor = doctor),
        maxRevotes = maxRevotes,
        nightDurationSeconds = nightSec,
        discussionDurationSeconds = discussionSec,
        voteDurationSeconds = voteSec,
    )

    @Test
    fun valid_for_five_player_preset() {
        val settings = MafiaSettingsPresets.forPlayerCount(5)
        assertThat(settings.validate(5)).isEqualTo(MafiaSettingsValidation.Valid)
    }

    @Test
    fun rejects_player_count_below_minimum() {
        val settings = base()
        val v = settings.validate(playerCount = 4) as MafiaSettingsValidation.Invalid
        assertThat(v.errors).contains(MafiaSettingsError.PlayerCountBelowMinimum(4))
    }

    @Test
    fun rejects_player_count_above_shipping_maximum() {
        val settings = MafiaSettingsPresets.forPlayerCount(17)
        val v = settings.validate(playerCount = 17) as MafiaSettingsValidation.Invalid
        assertThat(v.errors).contains(MafiaSettingsError.PlayerCountAboveMaximum(17))
    }

    @Test
    fun rejects_zero_mafia() {
        val settings = base(mafia = 0)
        val v = settings.validate(playerCount = 5) as MafiaSettingsValidation.Invalid
        assertThat(v.errors).contains(MafiaSettingsError.MafiaCountBelowOne)
    }

    @Test
    fun rejects_negative_optional_role_counts() {
        val detective = base(detective = -1).validate(5) as MafiaSettingsValidation.Invalid
        val doctor = base(doctor = -1).validate(5) as MafiaSettingsValidation.Invalid
        assertThat(detective.errors).contains(MafiaSettingsError.NegativeDetectiveCount(-1))
        assertThat(doctor.errors).contains(MafiaSettingsError.NegativeDoctorCount(-1))
    }

    @Test
    fun rejects_multiple_detectives_or_doctors_for_single_role_ruleset() {
        val detective = base(detective = 2).validate(7) as MafiaSettingsValidation.Invalid
        val doctor = base(doctor = 2).validate(7) as MafiaSettingsValidation.Invalid
        assertThat(detective.errors).contains(MafiaSettingsError.TooManyDetectives(2))
        assertThat(doctor.errors).contains(MafiaSettingsError.TooManyDoctors(2))
    }

    @Test
    fun rejects_negative_civilian_remainder() {
        // 5 players, 3 mafia + 1 detective + 2 doctor = 6 assigned → civ remainder = -1
        val settings = base(mafia = 3, detective = 1, doctor = 2)
        val v = settings.validate(playerCount = 5) as MafiaSettingsValidation.Invalid
        assertThat(v.errors).contains(MafiaSettingsError.NotEnoughCivilians(-1))
    }

    @Test
    fun rejects_zero_civilians() {
        // 5 players, 2 mafia + 1 detective + 2 doctor = 5 assigned → civ remainder = 0
        val settings = base(mafia = 2, detective = 1, doctor = 2)
        val v = settings.validate(playerCount = 5) as MafiaSettingsValidation.Invalid
        assertThat(v.errors).contains(MafiaSettingsError.NotEnoughCivilians(0))
    }

    @Test
    fun rejects_mafia_majority_at_start() {
        // 6 players, 3 mafia, 1 detective, 0 doctor → mafia(3) ≥ playerCount-mafia(3)
        val settings = base(mafia = 3, detective = 1, doctor = 0)
        val v = settings.validate(playerCount = 6) as MafiaSettingsValidation.Invalid
        val hasMajorityError = v.errors.any { it is MafiaSettingsError.MafiaNotMinority }
        assertThat(hasMajorityError).isEqualTo(true)
    }

    @Test
    fun rejects_mafia_equal_to_town_at_start() {
        // 6 players, 3 mafia, 1 detective, 0 doctor: 3 town. Mafia >= non-Mafia.
        val settings = base(mafia = 3, detective = 1, doctor = 0)
        val v = settings.validate(playerCount = 6) as MafiaSettingsValidation.Invalid
        assertThat(v).isInstanceOf(MafiaSettingsValidation.Invalid::class)
    }

    @Test
    fun accepts_mafia_strict_minority() {
        // 6 players, 2 mafia, 1 detective, 1 doctor → 2 civ, town = 4, mafia = 2 < 4.
        val settings = base(mafia = 2, detective = 1, doctor = 1)
        assertThat(settings.validate(playerCount = 6)).isEqualTo(MafiaSettingsValidation.Valid)
    }

    @Test
    fun rejects_negative_max_revotes() {
        val settings = base(maxRevotes = -1)
        val v = settings.validate(playerCount = 5) as MafiaSettingsValidation.Invalid
        assertThat(v.errors).contains(MafiaSettingsError.NegativeMaxRevotes(-1))
    }

    @Test
    fun accepts_zero_max_revotes() {
        val settings = base(maxRevotes = 0)
        assertThat(settings.validate(playerCount = 5)).isEqualTo(MafiaSettingsValidation.Valid)
    }

    @Test
    fun rejects_duration_below_minimum() {
        val settings = base(nightSec = 4)
        val v = settings.validate(playerCount = 5) as MafiaSettingsValidation.Invalid
        val hasDur = v.errors.any { it is MafiaSettingsError.DurationTooShort && it.kind == "night" }
        assertThat(hasDur).isEqualTo(true)
        assertThat(v.errors).contains(MafiaSettingsError.TimersNotSupported)
    }

    @Test
    fun rejects_non_null_timer_until_timer_transitions_are_implemented() {
        val settings = base(nightSec = 60)
        val v = settings.validate(playerCount = 5) as MafiaSettingsValidation.Invalid
        assertThat(v.errors).contains(MafiaSettingsError.TimersNotSupported)
    }

    @Test
    fun null_durations_are_valid() {
        val settings = base(nightSec = null, discussionSec = null, voteSec = null)
        assertThat(settings.validate(playerCount = 5)).isEqualTo(MafiaSettingsValidation.Valid)
    }

    @Test
    fun role_counts_civilians_is_remainder() {
        // The structural guarantee: total assigned = playerCount by definition.
        val counts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1)
        assertThat(counts.civilians(playerCount = 7)).isEqualTo(3)
        assertThat(counts.assignedTotal(playerCount = 7)).isEqualTo(7)
    }

    @Test
    fun presets_are_valid_across_supported_band() {
        for (n in 5..16) {
            val s = MafiaSettingsPresets.forPlayerCount(n)
            val v = s.validate(n)
            assertThat(v).isEqualTo(MafiaSettingsValidation.Valid)
        }
    }
}
