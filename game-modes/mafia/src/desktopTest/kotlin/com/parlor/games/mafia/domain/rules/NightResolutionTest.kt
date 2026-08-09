package com.parlor.games.mafia.domain.rules

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.state.DetectiveSeesAs
import com.parlor.games.mafia.domain.state.Role
import kotlin.test.Test

class NightResolutionTest {

    private val m1 = PlayerId("m1")
    private val m2 = PlayerId("m2")
    private val det = PlayerId("det")
    private val doc = PlayerId("doc")
    private val c1 = PlayerId("c1")
    private val c2 = PlayerId("c2")

    private val roles = mapOf(
        m1 to Role.Mafia,
        m2 to Role.Mafia,
        det to Role.Detective,
        doc to Role.Doctor,
        c1 to Role.Civilian,
        c2 to Role.Civilian,
    )
    private val alive: Set<PlayerId> = roles.keys

    private fun settings(
        doctorCanSelfHeal: Boolean = false,
        doctorRepeat: Boolean = false,
        mafiaCanTargetMafia: Boolean = false,
        killTie: MafiaKillTie = MafiaKillTie.REVOTE,
    ) = MafiaSettings(
        roleCounts = MafiaRoleCounts(mafia = 2, detective = 1, doctor = 1),
        doctorCanSelfHeal = doctorCanSelfHeal,
        doctorCanProtectSamePlayerConsecutively = doctorRepeat,
        mafiaCanTargetMafia = mafiaCanTargetMafia,
        mafiaKillTieBehavior = killTie,
    )

    @Test
    fun mafia_plurality_kills_target_when_doctor_not_protecting() {
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = c2,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.killed).isEqualTo(c1)
        assertThat(res.wasSaved).isFalse()
    }

    @Test
    fun doctor_saves_target_when_protecting_same_player_as_mafia_target() {
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = c1,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.killed).isNull()
        assertThat(res.wasSaved).isTrue()
    }

    @Test
    fun doctor_cannot_repeat_same_target_when_setting_off() {
        // Doctor's pick equals previous-night protect AND consecutive flag off:
        // doctor target is dropped → save does not happen.
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = c1,
                doctorProtectedPreviousNight = c1,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(doctorRepeat = false),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.effectiveDoctorTarget).isNull()
        assertThat(res.killed).isEqualTo(c1)
        assertThat(res.wasSaved).isFalse()
    }

    @Test
    fun doctor_can_repeat_same_target_when_setting_on() {
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = c1,
                doctorProtectedPreviousNight = c1,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(doctorRepeat = true),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.effectiveDoctorTarget).isEqualTo(c1)
        assertThat(res.killed).isNull()
        assertThat(res.wasSaved).isTrue()
    }

    @Test
    fun doctor_self_heal_gated_by_setting() {
        // The reducer is the layer that rejects a doctor self-pick when the
        // setting is off, so the rules helper just respects whatever target
        // it was passed. We use this test to lock in that NightResolution
        // does NOT silently drop a self-target when only this helper runs —
        // the gating is the reducer's job. So if doctorTarget == doc and
        // doc is alive, the save fires.
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(doc to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = doc,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(doctorCanSelfHeal = true),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.killed).isNull()
        assertThat(res.wasSaved).isTrue()
    }

    @Test
    fun detective_inspection_returns_correct_team_seen() {
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = null,
                doctorProtectedPreviousNight = null,
                detectiveTarget = m1,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(),
            random = RandomSource.seeded(1L),
        )
        val det = res.detectiveResult
        assertThat(det).isNotNull()
        assertThat(det!!.first).isEqualTo(m1)
        assertThat(det.second).isEqualTo(DetectiveSeesAs.Mafia)
    }

    @Test
    fun detective_inspection_of_town_player() {
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = null,
                doctorProtectedPreviousNight = null,
                detectiveTarget = doc,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.detectiveResult?.second).isEqualTo(DetectiveSeesAs.Town)
    }

    @Test
    fun no_detective_target_yields_no_result() {
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = null,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.detectiveResult).isNull()
    }

    @Test
    fun tied_kill_with_random_tied_picks_from_tied_set() {
        // The helper exposes a tiedFinal flag when called on a tied tally.
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 1, c2 to 1),
                mafiaCoordinationRound = 2, // round 2 = already tried revote
                doctorTarget = null,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(killTie = MafiaKillTie.RANDOM_TIED),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.mafiaTargetTied).isTrue()
        assertThat(res.mafiaTarget == c1 || res.mafiaTarget == c2).isTrue()
    }

    @Test
    fun random_tied_pick_is_invariant_to_tally_insertion_order_for_every_sampled_seed() {
        fun resolve(tally: Map<PlayerId, Int>, seed: Long) = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = tally,
                mafiaCoordinationRound = 2,
                doctorTarget = null,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(killTie = MafiaKillTie.RANDOM_TIED),
            random = RandomSource.seeded(seed),
        )

        val forward = linkedMapOf(c1 to 1, c2 to 1, doc to 1)
        val reverse = linkedMapOf(doc to 1, c2 to 1, c1 to 1)

        for (seed in 0L until 256L) {
            assertThat(resolve(forward, seed)).isEqualTo(resolve(reverse, seed))
        }
    }

    @Test
    fun tied_kill_with_no_kill_kills_no_one() {
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(c1 to 1, c2 to 1),
                mafiaCoordinationRound = 2,
                doctorTarget = null,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(killTie = MafiaKillTie.NO_KILL),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.mafiaTargetTied).isTrue()
        assertThat(res.mafiaTarget).isNull()
        assertThat(res.killed).isNull()
    }

    @Test
    fun empty_mafia_tally_yields_no_kill() {
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = emptyMap(),
                mafiaCoordinationRound = 1,
                doctorTarget = null,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles,
                alive = alive,
            ),
            settings = settings(),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.mafiaTarget).isNull()
        assertThat(res.killed).isNull()
    }

    @Test
    fun mafia_target_not_alive_results_in_no_kill() {
        // Defensive case: tally references a player not in `alive`.
        val ghost = PlayerId("ghost")
        val res = NightResolution.resolve(
            NightResolution.Inputs(
                mafiaTargetTally = mapOf(ghost to 2),
                mafiaCoordinationRound = 1,
                doctorTarget = null,
                doctorProtectedPreviousNight = null,
                detectiveTarget = null,
                rolesByPlayer = roles + (ghost to Role.Civilian),
                alive = alive, // ghost not included
            ),
            settings = settings(),
            random = RandomSource.seeded(1L),
        )
        assertThat(res.killed).isNull()
    }
}
