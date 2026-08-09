package com.parlor.games.mafia.domain.reducer

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.settings.TieBehavior
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.domain.state.VoteOutcome
import com.parlor.games.mafia.domain.state.team
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Production-readiness edge cases for [MafiaReducer]. The existing
 * [MafiaReducerTest] covers the happy path; this file covers the rejection
 * gates that the reducer relies on as its first line of defense (the
 * authority policy is the second, and the projection policy is the third).
 *
 * Each test is named after the invariant it locks in. If any of these flips
 * from rejecting to accepting, it represents a privacy or rules violation
 * that the projection policy alone cannot recover from.
 */
class MafiaReducerEdgeCasesTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private fun ctx(seed: Long = 42L): ReducerContext = DefaultReducerContext(
        clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
        random = RandomSource.seeded(seed),
    )

    private fun players(n: Int): List<Player> =
        (0 until n).map { Player(PlayerId("p$it"), "P$it", seat = it) }

    private fun initialState(n: Int, seed: Long = 42L): MafiaState {
        val def = MafiaDefinition(json)
        return def.createInitialState(
            SessionConfig(
                sessionId = SessionId("s-$seed"),
                caseId = CaseId("default"),
                modeId = MafiaIds.ClassicModeId,
                players = players(n),
                randomSeed = seed,
            ),
        )
    }

    private fun atNight(n: Int = 7, seed: Long = 42L): MafiaState {
        var state = MafiaReducer.reduce(initialState(n, seed), MafiaAction.StartGame, ctx(seed)).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx(seed)).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx(seed)).newState
        return state
    }

    private fun atVoting(n: Int = 7, seed: Long = 42L): MafiaState {
        var state = atNight(n, seed)
        val mafia = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys
        for (m in mafia) {
            state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(m, target = null), ctx(seed)).newState
        }
        state = submitUnsubmittedNightActions(state, seed)
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx(seed)).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeNightAnnouncement(p.id), ctx(seed)).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.OpenDiscussion, ctx(seed)).newState
        state = MafiaReducer.reduce(state, MafiaAction.OpenVote, ctx(seed)).newState
        return state
    }

    // -------------------------------------------------------------------- Night submissions

    @Test
    fun mafia_targeting_another_mafia_is_rejected_when_setting_disabled() {
        val state = atNight(7)
        val mafiaIds = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys.toList()
        if (mafiaIds.size < 2) return
        val a = mafiaIds[0]
        val b = mafiaIds[1]
        // Default settings: mafiaCanTargetMafia = false.
        assertThat(state.public.settings.mafiaCanTargetMafia).isFalse()
        val after = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(by = a, target = b), ctx()).newState
        // Submission rejected: pendingNightChoice unchanged (null), snapshot does not record b.
        assertThat(after.privatePerPlayer[a]!!.pendingNightChoice).isNull()
        val coord = after.privatePerPlayer[a]!!.mafiaCoordination
        assertThat(coord!!.submissions[a]).isNull()
    }

    @Test
    fun mafia_targeting_mafia_accepted_when_setting_enabled() {
        var state = initialState(7)
        // Toggle setting in Setup, then advance.
        val enabled = state.public.settings.copy(mafiaCanTargetMafia = true)
        state = MafiaReducer.reduce(state, MafiaAction.ApplySettings(enabled), ctx()).newState
        state = MafiaReducer.reduce(state, MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState

        val mafiaIds = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys.toList()
        if (mafiaIds.size < 2) return
        val a = mafiaIds[0]
        val b = mafiaIds[1]
        val after = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(by = a, target = b), ctx()).newState
        assertThat(after.privatePerPlayer[a]!!.pendingNightChoice).isEqualTo(b)
    }

    @Test
    fun doctor_self_protect_rejected_when_setting_disabled() {
        val state = atNight(7)
        val doctor = state.privatePerPlayer.entries.firstOrNull { it.value.role == Role.Doctor }?.key ?: return
        assertThat(state.public.settings.doctorCanSelfHeal).isFalse()
        val after = MafiaReducer.reduce(state, MafiaAction.SubmitDoctorProtect(by = doctor, target = doctor), ctx()).newState
        assertThat(after.privatePerPlayer[doctor]!!.pendingNightChoice).isNull()
    }

    @Test
    fun doctor_self_protect_accepted_when_setting_enabled() {
        var state = initialState(7)
        val enabled = state.public.settings.copy(doctorCanSelfHeal = true)
        state = MafiaReducer.reduce(state, MafiaAction.ApplySettings(enabled), ctx()).newState
        state = MafiaReducer.reduce(state, MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState
        val doctor = state.privatePerPlayer.entries.firstOrNull { it.value.role == Role.Doctor }?.key ?: return
        val after = MafiaReducer.reduce(state, MafiaAction.SubmitDoctorProtect(by = doctor, target = doctor), ctx()).newState
        assertThat(after.privatePerPlayer[doctor]!!.pendingNightChoice).isEqualTo(doctor)
    }

    @Test
    fun detective_self_inspect_rejected_when_setting_disabled() {
        val state = atNight(7)
        val detective = state.privatePerPlayer.entries.firstOrNull { it.value.role == Role.Detective }?.key ?: return
        assertThat(state.public.settings.detectiveCanInspectSelf).isFalse()
        val after = MafiaReducer.reduce(state, MafiaAction.SubmitDetectiveInspect(by = detective, target = detective), ctx()).newState
        assertThat(after.privatePerPlayer[detective]!!.pendingNightChoice).isNull()
    }

    @Test
    fun night_cannot_resolve_until_every_active_living_player_submits() {
        var state = atNight(7)
        val mafia = state.privatePerPlayer.entries.first { it.value.role == Role.Mafia }.key
        state = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitMafiaKillVote(mafia, target = null),
            ctx(),
        ).newState

        val attempt = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx())

        assertThat(attempt.newState).isEqualTo(state)
        assertThat(attempt.events).isEmpty()
    }

    @Test
    fun detective_result_is_available_immediately_and_blocks_resolution_until_acknowledged() {
        var state = atNight(7)
        val detective = state.privatePerPlayer.entries.first { it.value.role == Role.Detective }.key
        val mafia = state.privatePerPlayer.entries.first { it.value.role == Role.Mafia }.key
        state = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitDetectiveInspect(detective, mafia),
            ctx(),
        ).newState

        val result = state.privatePerPlayer.getValue(detective).pendingDetectiveResult
        assertThat(result).isNotNull()
        assertThat(result!!.target).isEqualTo(mafia)
        assertThat(result.seesAs).isEqualTo(
            com.parlor.games.mafia.domain.state.DetectiveSeesAs.Mafia,
        )

        state = submitUnsubmittedNightActions(state)
        val blocked = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx())
        assertThat(blocked.newState).isEqualTo(state)

        state = MafiaReducer.reduce(
            state,
            MafiaAction.AcknowledgeDetectiveResult(detective),
            ctx(),
        ).newState
        val resolved = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx()).newState
        assertThat(resolved.phase is MafiaPhase.NightAnnouncement || resolved.phase == MafiaPhase.PostGame)
            .isTrue()
    }

    @Test
    fun duplicate_night_submission_is_idempotent_and_cannot_replace_first_choice() {
        val state = atNight(7)
        val civilian = state.privatePerPlayer.entries.first { it.value.role == Role.Civilian }.key
        val targets = state.public.roster.map { it.playerId }.filter { it != civilian }
        val first = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitCivilianSuspicion(civilian, targets[0]),
            ctx(),
        ).newState

        val duplicate = MafiaReducer.reduce(
            first,
            MafiaAction.SubmitCivilianSuspicion(civilian, targets[1]),
            ctx(),
        )

        assertThat(duplicate.newState).isEqualTo(first)
        assertThat(duplicate.events).isEmpty()
    }

    @Test
    fun doctor_consecutive_target_is_rejected_at_submission_boundary() {
        var state = atNight(7)
        val doctor = state.privatePerPlayer.entries.firstOrNull { it.value.role == Role.Doctor }?.key ?: return
        val target = state.public.roster.first { it.playerId != doctor }.playerId
        val private = state.privatePerPlayer.getValue(doctor)
        state = state.copy(
            privatePerPlayer = state.privatePerPlayer +
                (doctor to private.copy(previousDoctorProtect = target)),
        )

        val after = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitDoctorProtect(doctor, target),
            ctx(),
        )

        assertThat(after.newState).isEqualTo(state)
        assertThat(after.events).isEmpty()
    }

    // -------------------------------------------------------------------- Voting

    @Test
    fun cast_vote_by_non_ballot_voter_is_rejected() {
        var state = atVoting(7)
        // Mark a player as dropped → they're not in the ballot we just computed.
        // Actually, easier: artificially edit the activeVote to exclude a real player from ballot.
        val activeVote = state.public.activeVote!!
        val excluded = state.players.first().id
        state = state.copy(
            public = state.public.copy(
                activeVote = activeVote.copy(ballot = activeVote.ballot - excluded),
            ),
        )
        val target = state.players.last().id
        val after = MafiaReducer.reduce(state, MafiaAction.CastVote(by = excluded, target = target), ctx()).newState
        // Excluded voter's cast must NOT appear.
        assertThat(after.public.activeVote!!.castSoFar[excluded]).isNull()
    }

    @Test
    fun cast_vote_for_non_candidate_target_is_rejected() {
        var state = atVoting(7)
        val activeVote = state.public.activeVote!!
        // Remove one player from the candidate list, then try to vote for them.
        val nonCandidate = state.players.last().id
        state = state.copy(
            public = state.public.copy(
                activeVote = activeVote.copy(candidates = activeVote.candidates - nonCandidate),
            ),
        )
        val voter = state.players.first().id
        val after = MafiaReducer.reduce(state, MafiaAction.CastVote(by = voter, target = nonCandidate), ctx()).newState
        assertThat(after.public.activeVote!!.castSoFar[voter]).isNull()
    }

    @Test
    fun cast_self_vote_rejected_when_setting_disabled() {
        val state = atVoting(7)
        assertThat(state.public.settings.allowSelfVote).isFalse()
        val voter = state.players.first().id
        val after = MafiaReducer.reduce(state, MafiaAction.CastVote(by = voter, target = voter), ctx()).newState
        assertThat(after.public.activeVote!!.castSoFar[voter]).isNull()
    }

    @Test
    fun cast_self_vote_accepted_when_setting_enabled() {
        var state = initialState(7)
        val enabled = state.public.settings.copy(allowSelfVote = true)
        state = MafiaReducer.reduce(state, MafiaAction.ApplySettings(enabled), ctx()).newState
        // Now drive to voting.
        state = MafiaReducer.reduce(state, MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState
        val mafia = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys
        for (m in mafia) {
            state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(m, target = null), ctx()).newState
        }
        state = submitUnsubmittedNightActions(state)
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeNightAnnouncement(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.OpenDiscussion, ctx()).newState
        state = MafiaReducer.reduce(state, MafiaAction.OpenVote, ctx()).newState

        val voter = state.players.first().id
        val after = MafiaReducer.reduce(state, MafiaAction.CastVote(by = voter, target = voter), ctx()).newState
        assertThat(after.public.activeVote!!.castSoFar[voter]).isEqualTo(voter)
    }

    @Test
    fun ballot_is_final_after_cast_and_later_abstain_is_ignored() {
        var state = atVoting(7)
        val voter = state.players.first().id
        val target = state.players.last().id
        // First cast a real vote.
        state = MafiaReducer.reduce(state, MafiaAction.CastVote(by = voter, target = target), ctx()).newState
        assertThat(state.public.activeVote!!.castSoFar[voter]).isEqualTo(target)
        // A later abstain is a duplicate ballot command and must be ignored.
        state = MafiaReducer.reduce(state, MafiaAction.AbstainVote(voter), ctx()).newState
        val v = state.public.activeVote!!
        assertThat(v.castSoFar[voter]).isEqualTo(target)
        assertThat(voter in v.abstained).isFalse()
    }

    @Test
    fun ballot_is_final_after_abstain_and_later_cast_is_ignored() {
        var state = atVoting(7)
        val voter = state.players.first().id
        val target = state.players.last().id
        state = MafiaReducer.reduce(state, MafiaAction.AbstainVote(voter), ctx()).newState
        assertThat(voter in state.public.activeVote!!.abstained).isTrue()
        state = MafiaReducer.reduce(state, MafiaAction.CastVote(by = voter, target = target), ctx()).newState
        val v = state.public.activeVote!!
        assertThat(voter in v.abstained).isTrue()
        assertThat(v.castSoFar[voter]).isNull()
    }

    @Test
    fun close_vote_with_clear_plurality_records_correct_tally() {
        var state = atVoting(7)
        val voters = state.players.map { it.id }
        val target = voters.last()
        // All voters vote for the last player.
        for (v in voters.dropLast(1)) {
            state = MafiaReducer.reduce(state, MafiaAction.CastVote(by = v, target = target), ctx()).newState
        }
        // Target abstains (cannot self-vote by default).
        state = MafiaReducer.reduce(state, MafiaAction.AbstainVote(target), ctx()).newState
        state = MafiaReducer.reduce(state, MafiaAction.CloseVote, ctx()).newState
        val lastVote = state.public.lastVote!!
        assertThat(lastVote.outcome).isEqualTo(VoteOutcome.Eliminated)
        assertThat(lastVote.eliminatedPlayerId).isEqualTo(target)
        // Tally pins the count we constructed.
        assertThat(lastVote.tally[target]).isEqualTo(voters.size - 1)
    }

    @Test
    fun close_vote_is_noop_until_every_ballot_member_has_acted() {
        var state = atVoting(7)
        val first = state.public.activeVote!!.ballot.first()
        state = MafiaReducer.reduce(state, MafiaAction.AbstainVote(first), ctx()).newState

        val attempt = MafiaReducer.reduce(state, MafiaAction.CloseVote, ctx())

        assertThat(attempt.newState).isEqualTo(state)
        assertThat(attempt.events).isEmpty()
    }

    // -------------------------------------------------------------------- Win check & advance

    @Test
    fun advance_from_vote_announcement_jumps_to_post_game_when_winner_set() {
        // Build a contrived VoteAnnouncement state with a winner already set —
        // and players still unacked. The reducer must short-circuit to PostGame.
        val state = initialState(7).copy(
            phase = MafiaPhase.VoteAnnouncement(day = 1),
            public = initialState(7).public.copy(winner = Team.Town),
        )
        val after = MafiaReducer.reduce(state, MafiaAction.AdvanceFromVoteAnnouncement, ctx()).newState
        assertThat(after.phase).isEqualTo(MafiaPhase.PostGame)
    }

    @Test
    fun end_game_from_post_game_is_idempotent() {
        var state = initialState(7)
        state = MafiaReducer.reduce(state, MafiaAction.EndGame, ctx()).newState
        assertThat(state.phase).isEqualTo(MafiaPhase.PostGame)
        // Second EndGame must not crash and must keep us in PostGame.
        state = MafiaReducer.reduce(state, MafiaAction.EndGame, ctx()).newState
        assertThat(state.phase).isEqualTo(MafiaPhase.PostGame)
    }

    @Test
    fun readmit_player_rejected_outside_setup_or_role_assignment() {
        val state = atNight(7)
        val p = state.players.first().id
        // First drop them via the host action (allowed any phase).
        val dropped = MafiaReducer.reduce(state, MafiaAction.ContinueWithoutPlayer(p), ctx()).newState
        assertThat(p in dropped.public.droppedPlayers).isTrue()
        // Try to readmit during Night — must be rejected.
        val attempt = MafiaReducer.reduce(dropped, MafiaAction.ReadmitPlayer(p), ctx()).newState
        assertThat(p in attempt.public.droppedPlayers).isTrue()
    }

    @Test
    fun readmit_player_accepted_in_setup() {
        var state = initialState(7)
        val p = state.players.first().id
        // Defensive compatibility for an older serialized Setup state, where
        // ContinueWithoutPlayer could leave a dropped lobby seat.
        state = state.copy(public = state.public.copy(droppedPlayers = setOf(p)))
        assertThat(p in state.public.droppedPlayers).isTrue()
        state = MafiaReducer.reduce(state, MafiaAction.ReadmitPlayer(p), ctx()).newState
        assertThat(p in state.public.droppedPlayers).isFalse()
    }

    @Test
    fun continue_without_player_during_active_game_ends_and_reveals_every_role() {
        val state = atNight(7)
        val dropped = state.players.first().id

        val ended = MafiaReducer.reduce(
            state,
            MafiaAction.ContinueWithoutPlayer(dropped),
            ctx(),
        ).newState

        assertThat(ended.phase).isEqualTo(MafiaPhase.PostGame)
        assertThat(dropped in ended.public.droppedPlayers).isTrue()
        for (slot in ended.public.roster) {
            assertThat(slot.revealedRole).isEqualTo(ended.hostOnly.fullRoleMap[slot.playerId])
        }
    }

    // -------------------------------------------------------------------- Detective result delivery

    @Test
    fun resolve_night_with_no_detective_pick_delivers_no_pending_result() {
        var state = atNight(7)
        // No one submits a detective inspect. Have the Mafia submit null kills.
        val mafia = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys
        for (m in mafia) {
            state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(m, target = null), ctx()).newState
        }
        state = submitUnsubmittedNightActions(state)
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx()).newState
        val detective = state.privatePerPlayer.entries.firstOrNull { it.value.role == Role.Detective }?.key ?: return
        assertThat(state.privatePerPlayer[detective]!!.pendingDetectiveResult).isNull()
    }

    // -------------------------------------------------------------------- Mafia coordination cleanup

    @Test
    fun mafia_coordination_snapshot_is_null_for_everyone_after_night_resolution() {
        var state = atNight(7)
        val mafia = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys.toList()
        // Submit a kill so the snapshot is populated, then resolve.
        state = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitMafiaKillVote(mafia.first(), state.privatePerPlayer.entries.first { it.value.role == Role.Civilian }.key),
            ctx(),
        ).newState
        assertThat(state.privatePerPlayer[mafia.first()]!!.mafiaCoordination).isNotNull()
        state = submitUnsubmittedNightActions(state)
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx()).newState
        // After resolution: every private's mafiaCoordination must be null.
        for ((_, priv) in state.privatePerPlayer) {
            assertThat(priv.mafiaCoordination).isNull()
        }
    }

    @Test
    fun mafia_coordination_snapshot_reinitialised_for_living_mafia_on_next_night() {
        var state = atNight(7)
        // No kill night 1.
        val mafia = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys
        for (m in mafia) {
            state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(m, target = null), ctx()).newState
        }
        state = submitUnsubmittedNightActions(state)
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeNightAnnouncement(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.OpenDiscussion, ctx()).newState
        state = MafiaReducer.reduce(state, MafiaAction.OpenVote, ctx()).newState
        // All abstain → no elimination.
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AbstainVote(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.CloseVote, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeVoteAnnouncement(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromVoteAnnouncement, ctx()).newState
        // Night 2 phase entered. Mafia members have a fresh snapshot; Town remain null.
        assertThat(state.phase).isInstanceOf(MafiaPhase.Night::class)
        for ((id, priv) in state.privatePerPlayer) {
            if (priv.role == Role.Mafia) {
                assertThat(priv.mafiaCoordination).isNotNull()
                assertThat(priv.mafiaCoordination!!.round).isEqualTo(1)
            } else {
                assertThat(priv.mafiaCoordination).isNull()
            }
        }
    }

    private fun submitUnsubmittedNightActions(
        initial: MafiaState,
        seed: Long = 42L,
    ): MafiaState {
        var state = initial
        val activeAlive = state.public.roster
            .filter { it.alive && it.playerId !in state.public.droppedPlayers }
            .map { it.playerId }
        for (id in activeAlive) {
            val private = state.privatePerPlayer[id] ?: continue
            if (private.nightChoiceSubmitted) continue
            val action = when (private.role) {
                Role.Mafia -> MafiaAction.SubmitMafiaKillVote(id, null)
                Role.Doctor -> MafiaAction.SubmitDoctorProtect(id, null)
                Role.Detective -> MafiaAction.SubmitDetectiveInspect(id, null)
                Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(id, null)
            }
            state = MafiaReducer.reduce(state, action, ctx(seed)).newState
        }
        return state
    }
}
