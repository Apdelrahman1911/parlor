package com.parlor.games.mafia.domain.settings

import kotlin.time.Duration
import kotlinx.serialization.Serializable

/**
 * Host-configurable rules for a Mafia session. Role counts carry only the
 * explicit roles; Civilians are computed as the remainder of the player
 * count, so total assigned roles always equal player count by construction.
 *
 * Validated via [validate] before the reducer commits to state.public.settings.
 */
@Serializable
data class MafiaSettings(
    val roleCounts: MafiaRoleCounts,
    val revealRoleOnDeath: Boolean = true,
    val doctorCanSelfHeal: Boolean = false,
    val doctorCanProtectSamePlayerConsecutively: Boolean = false,
    val detectiveCanInspectSelf: Boolean = false,
    val allowSelfVote: Boolean = false,
    val mafiaCanTargetMafia: Boolean = false,
    val voteTieBehavior: TieBehavior = TieBehavior.REVOTE_TIED_ONLY,
    val maxRevotes: Int = 1,
    val mafiaKillTieBehavior: MafiaKillTie = MafiaKillTie.REVOTE,
    val nightDurationSeconds: Int? = null,
    val discussionDurationSeconds: Int? = null,
    val voteDurationSeconds: Int? = null,
) {
    fun validate(playerCount: Int): MafiaSettingsValidation {
        val errors = mutableListOf<MafiaSettingsError>()
        if (playerCount < MIN_PLAYERS) errors += MafiaSettingsError.PlayerCountBelowMinimum(playerCount)
        if (roleCounts.mafia < 1) errors += MafiaSettingsError.MafiaCountBelowOne
        val civ = roleCounts.civilians(playerCount)
        if (civ < 1) errors += MafiaSettingsError.NotEnoughCivilians(civ)
        // Town must start with strict majority.
        if (roleCounts.mafia >= playerCount - roleCounts.mafia) {
            errors += MafiaSettingsError.MafiaNotMinority(roleCounts.mafia, playerCount)
        }
        if (maxRevotes < 0) errors += MafiaSettingsError.NegativeMaxRevotes(maxRevotes)
        nightDurationSeconds?.let { if (it < MIN_DURATION_SECONDS) errors += MafiaSettingsError.DurationTooShort("night", it) }
        discussionDurationSeconds?.let { if (it < MIN_DURATION_SECONDS) errors += MafiaSettingsError.DurationTooShort("discussion", it) }
        voteDurationSeconds?.let { if (it < MIN_DURATION_SECONDS) errors += MafiaSettingsError.DurationTooShort("vote", it) }
        return if (errors.isEmpty()) MafiaSettingsValidation.Valid else MafiaSettingsValidation.Invalid(errors)
    }

    companion object {
        const val MIN_PLAYERS = 5
        const val MIN_DURATION_SECONDS = 5
    }
}

@Serializable
data class MafiaRoleCounts(
    val mafia: Int,
    val detective: Int,
    val doctor: Int,
) {
    fun civilians(playerCount: Int): Int = playerCount - mafia - detective - doctor
    fun assignedTotal(playerCount: Int): Int = mafia + detective + doctor + civilians(playerCount)
}

@Serializable
enum class TieBehavior {
    /** Re-open the vote with the full ballot. */
    REVOTE_ALL,

    /** Re-open the vote with only the tied players as targets. */
    REVOTE_TIED_ONLY,

    /** Skip elimination this day; proceed to next night. */
    SKIP_ELIMINATION,
}

@Serializable
enum class MafiaKillTie {
    /** Trigger one Mafia coordination revote, then fall back to RANDOM_TIED. */
    REVOTE,

    /** Pick uniformly at random from the tied targets. */
    RANDOM_TIED,

    /** No one dies tonight. */
    NO_KILL,
}

sealed interface MafiaSettingsValidation {
    data object Valid : MafiaSettingsValidation
    data class Invalid(val errors: List<MafiaSettingsError>) : MafiaSettingsValidation
}

sealed interface MafiaSettingsError {
    data class PlayerCountBelowMinimum(val playerCount: Int) : MafiaSettingsError
    data object MafiaCountBelowOne : MafiaSettingsError
    data class NotEnoughCivilians(val computed: Int) : MafiaSettingsError
    data class MafiaNotMinority(val mafiaCount: Int, val playerCount: Int) : MafiaSettingsError
    data class NegativeMaxRevotes(val value: Int) : MafiaSettingsError
    data class DurationTooShort(val kind: String, val seconds: Int) : MafiaSettingsError
}

@Suppress("unused")
fun seconds(duration: Duration): Int = duration.inWholeSeconds.toInt()
