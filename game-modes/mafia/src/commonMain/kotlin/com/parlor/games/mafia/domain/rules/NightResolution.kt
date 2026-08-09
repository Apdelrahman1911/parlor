package com.parlor.games.mafia.domain.rules

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.state.DetectiveSeesAs
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.detectiveSeesAs

/**
 * Pure night resolution.
 *
 * Inputs are all derived from state at the moment ResolveNight fires:
 *   - mafia kill-vote tally for the current round (already plurality-resolved
 *     by the reducer when not tied; this helper just consumes the chosen
 *     target plus the tie flag for the random-tied / no-kill paths).
 *   - doctor protect
 *   - detective inspect
 * Output: a [Resolution] containing the killed player (if any), the detective
 * result (if any), and a "wasSaved" flag for the announcement chrome.
 */
object NightResolution {

    data class Inputs(
        val mafiaTargetTally: Map<PlayerId, Int>,
        val mafiaCoordinationRound: Int,
        val doctorTarget: PlayerId?,
        val doctorProtectedPreviousNight: PlayerId?,
        val detectiveTarget: PlayerId?,
        val rolesByPlayer: Map<PlayerId, Role>,
        val alive: Set<PlayerId>,
    )

    data class Resolution(
        val mafiaTarget: PlayerId?,
        val mafiaTargetTied: Boolean,
        val effectiveDoctorTarget: PlayerId?,
        val killed: PlayerId?,
        val wasSaved: Boolean,
        val detectiveResult: Pair<PlayerId, DetectiveSeesAs>?,
    )

    fun resolve(
        inputs: Inputs,
        settings: MafiaSettings,
        random: RandomSource,
    ): Resolution {
        val mafiaPick = resolveMafiaPick(inputs, settings, random)

        // Doctor target gating per settings.
        val docTarget = inputs.doctorTarget
            ?.takeIf { it in inputs.alive }
            ?.let { candidate ->
                val sameAsPrev = inputs.doctorProtectedPreviousNight == candidate
                if (sameAsPrev && !settings.doctorCanProtectSamePlayerConsecutively) null
                else candidate
            }

        val saved = mafiaPick.target != null && mafiaPick.target == docTarget
        val killed = if (saved) null else mafiaPick.target?.takeIf { it in inputs.alive }

        val detectiveResult = inputs.detectiveTarget
            ?.takeIf { it in inputs.alive }
            ?.let { target ->
                inputs.rolesByPlayer[target]?.let { role -> target to role.detectiveSeesAs() }
            }

        return Resolution(
            mafiaTarget = mafiaPick.target,
            mafiaTargetTied = mafiaPick.tiedFinal,
            effectiveDoctorTarget = docTarget,
            killed = killed,
            wasSaved = saved,
            detectiveResult = detectiveResult,
        )
    }

    private data class MafiaPick(val target: PlayerId?, val tiedFinal: Boolean)

    /**
     * Pick the Mafia kill target from the round's tally.
     * - Clear plurality → return it.
     * - Tied: if round 1 and `mafiaKillTieBehavior == REVOTE`, the reducer
     *   should have routed to a second round and never called us with a
     *   tied tally on round 1 — but we still handle it defensively.
     * - Round 2 (or REVOTE not configured) → apply the tie-break setting.
     */
    private fun resolveMafiaPick(
        inputs: Inputs,
        settings: MafiaSettings,
        random: RandomSource,
    ): MafiaPick {
        if (inputs.mafiaTargetTally.isEmpty()) return MafiaPick(null, tiedFinal = false)
        val maxCount = inputs.mafiaTargetTally.values.max()
        // Map iteration order is not part of the game state. Canonicalize tied
        // targets before consuming seeded randomness so equivalent tallies
        // always resolve to the same player on every platform and after a
        // snapshot round-trip.
        val top = inputs.mafiaTargetTally
            .filterValues { it == maxCount }
            .keys
            .sortedBy { it.raw }
        if (top.size == 1) return MafiaPick(top.first(), tiedFinal = false)

        // Tied. On round 1 with REVOTE configured, the reducer should route
        // to round 2 before calling resolve(). This branch covers round 2
        // (or any round where revote isn't configured).
        return when (settings.mafiaKillTieBehavior) {
            MafiaKillTie.REVOTE,
            MafiaKillTie.RANDOM_TIED -> MafiaPick(random.pick(top), tiedFinal = true)
            MafiaKillTie.NO_KILL -> MafiaPick(null, tiedFinal = true)
        }
    }
}
