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
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.settings.TieBehavior
import com.parlor.games.mafia.domain.state.MafiaPeerSnapshotValidator
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.snapshot.isValidRecoveryState
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Reducer-level behavior: phase transitions, settings application, action
 * authority gating inside the reducer (a Town player submitting a Mafia kill
 * vote is a no-op; a Mafia member submitting a Doctor protect is a no-op).
 */
class MafiaReducerTest {

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

    @Test
    fun initial_state_is_setup_with_preset_settings() {
        val state = initialState(7)
        assertThat(state.phase).isEqualTo(MafiaPhase.Setup)
        assertThat(state.public.settings).isEqualTo(MafiaSettingsPresets.forPlayerCount(7))
        assertThat(state.public.roster.size).isEqualTo(7)
    }

    @Test
    fun apply_settings_in_setup_commits_to_public() {
        val state = initialState(7)
        val newSettings = state.public.settings.copy(
            allowSelfVote = true,
            mafiaCanTargetMafia = true,
        )
        val result = MafiaReducer.reduce(state, MafiaAction.ApplySettings(newSettings), ctx())
        assertThat(result.newState.public.settings.allowSelfVote).isTrue()
        assertThat(result.newState.public.settings.mafiaCanTargetMafia).isTrue()
    }

    @Test
    fun apply_settings_outside_setup_is_a_no_op() {
        // Move past Setup, then attempt ApplySettings — reducer ignores.
        val state = initialState(7)
        val started = MafiaReducer.reduce(state, MafiaAction.StartGame, ctx()).newState
        val attempt = MafiaReducer.reduce(
            started,
            MafiaAction.ApplySettings(state.public.settings.copy(allowSelfVote = true)),
            ctx(),
        )
        assertThat(attempt.newState.public.settings.allowSelfVote).isFalse()
    }

    @Test
    fun apply_invalid_settings_is_rejected() {
        val state = initialState(7)
        val invalid = state.public.settings.copy(
            roleCounts = MafiaRoleCounts(mafia = 6, detective = 1, doctor = 0), // mafia >= town
        )
        val result = MafiaReducer.reduce(state, MafiaAction.ApplySettings(invalid), ctx())
        // Original settings preserved.
        assertThat(result.newState.public.settings).isEqualTo(state.public.settings)
    }

    @Test
    fun authoritative_reducer_rejects_excessive_revote_settings() {
        val state = initialState(7)
        val invalid = state.public.settings.copy(maxRevotes = MafiaSettings.MAX_REVOTES + 1)

        val result = MafiaReducer.reduce(state, MafiaAction.ApplySettings(invalid), ctx())

        assertThat(result.newState.public.settings).isEqualTo(state.public.settings)
        assertThat(result.events).isEqualTo(emptyList())
    }

    @Test
    fun start_game_assigns_roles_and_advances_to_role_assignment() {
        val state = initialState(7)
        val result = MafiaReducer.reduce(state, MafiaAction.StartGame, ctx())
        assertThat(result.newState.phase).isEqualTo(MafiaPhase.RoleAssignment)
        // Every player has a role in privatePerPlayer.
        assertThat(result.newState.privatePerPlayer.size).isEqualTo(7)
        for (p in state.players) {
            assertThat(result.newState.privatePerPlayer[p.id]).isNotNull()
        }
        // Full role map populated in hostOnly.
        assertThat(result.newState.hostOnly.fullRoleMap.size).isEqualTo(7)
    }

    @Test
    fun configure_and_start_commits_rules_and_roles_in_one_transition() {
        val state = initialState(7)
        val chosen = state.public.settings.copy(
            roleCounts = MafiaRoleCounts(mafia = 2, detective = 0, doctor = 1),
            allowSelfVote = true,
            voteTieBehavior = TieBehavior.SKIP_ELIMINATION,
        )

        val result = MafiaReducer.reduce(
            state,
            MafiaAction.ConfigureAndStart(chosen),
            ctx(),
        )

        assertThat(result.newState.phase).isEqualTo(MafiaPhase.RoleAssignment)
        assertThat(result.newState.public.settings).isEqualTo(chosen)
        assertThat(result.newState.hostOnly.fullRoleMap.values.count { it == Role.Mafia })
            .isEqualTo(2)
        assertThat(result.newState.hostOnly.fullRoleMap.values.count { it == Role.Doctor })
            .isEqualTo(1)
        assertThat(result.newState.hostOnly.fullRoleMap.values.count { it == Role.Detective })
            .isEqualTo(0)
        assertThat(result.events.first()).isEqualTo(
            com.parlor.games.mafia.domain.event.MafiaEvent.SettingsApplied,
        )
    }

    @Test
    fun configure_and_start_rejects_invalid_rules_without_starting_old_rules() {
        val state = initialState(7)
        val invalid = state.public.settings.copy(
            roleCounts = MafiaRoleCounts(mafia = 6, detective = 0, doctor = 0),
        )

        val result = MafiaReducer.reduce(
            state,
            MafiaAction.ConfigureAndStart(invalid),
            ctx(),
        )

        assertThat(result.newState).isEqualTo(state)
        assertThat(result.events).isEmpty()
    }

    @Test
    fun configure_and_start_is_idempotent_after_game_has_started() {
        val state = MafiaReducer.reduce(
            initialState(7),
            MafiaAction.ConfigureAndStart(MafiaSettingsPresets.forPlayerCount(7)),
            ctx(),
        ).newState

        val duplicate = MafiaReducer.reduce(
            state,
            MafiaAction.ConfigureAndStart(
                MafiaSettingsPresets.forPlayerCount(7).copy(allowSelfVote = true),
            ),
            ctx(),
        )

        assertThat(duplicate.newState).isEqualTo(state)
        assertThat(duplicate.events).isEmpty()
    }

    @Test
    fun advance_from_role_assignment_blocks_until_everyone_acks() {
        val state = MafiaReducer.reduce(initialState(7), MafiaAction.StartGame, ctx()).newState
        val attempt = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx())
        assertThat(attempt.newState.phase).isEqualTo(MafiaPhase.RoleAssignment)
    }

    @Test
    fun advance_from_role_assignment_succeeds_after_all_ack() {
        var state = MafiaReducer.reduce(initialState(7), MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState
        assertThat(state.phase).isInstanceOf(MafiaPhase.Night::class)
        assertThat((state.phase as MafiaPhase.Night).day).isEqualTo(1)
    }

    @Test
    fun non_mafia_submitting_kill_vote_is_a_no_op() {
        var state = MafiaReducer.reduce(initialState(7), MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState

        val townPlayer = state.privatePerPlayer.entries.first { it.value.role != Role.Mafia }.key
        val target = state.privatePerPlayer.entries.first { it.value.role == Role.Mafia }.key
        val before = state.privatePerPlayer[townPlayer]!!.pendingNightChoice
        val after = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitMafiaKillVote(by = townPlayer, target = target),
            ctx(),
        ).newState
        // Town player's pendingNightChoice unchanged.
        assertThat(after.privatePerPlayer[townPlayer]!!.pendingNightChoice).isEqualTo(before)
    }

    @Test
    fun mafia_submitting_doctor_protect_is_a_no_op() {
        var state = MafiaReducer.reduce(initialState(7), MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState

        val mafia = state.privatePerPlayer.entries.first { it.value.role == Role.Mafia }.key
        val target = state.privatePerPlayer.entries.first { it.value.role != Role.Mafia }.key
        val before = state.privatePerPlayer[mafia]!!.pendingNightChoice
        val after = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitDoctorProtect(by = mafia, target = target),
            ctx(),
        ).newState
        // SubmitDoctorProtect by a Mafia is rejected → choice unchanged.
        assertThat(after.privatePerPlayer[mafia]!!.pendingNightChoice).isEqualTo(before)
    }

    @Test
    fun mafia_coordination_snapshot_only_lives_in_mafia_private() {
        var state = MafiaReducer.reduce(initialState(7), MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState

        // Have one Mafia submit a kill vote — this triggers updateMafiaCoordination.
        val mafiaIds = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys
        val first = mafiaIds.first()
        val nonMafiaTarget = state.privatePerPlayer.entries.first { it.value.role != Role.Mafia }.key
        state = MafiaReducer.reduce(
            state,
            MafiaAction.SubmitMafiaKillVote(by = first, target = nonMafiaTarget),
            ctx(),
        ).newState

        // Every Mafia private has the snapshot; every non-Mafia private has null.
        for ((id, priv) in state.privatePerPlayer) {
            if (priv.role == Role.Mafia) {
                assertThat(priv.mafiaCoordination).isNotNull()
            } else {
                assertThat(priv.mafiaCoordination).isNull()
            }
        }
    }

    @Test
    fun host_open_vote_from_discussion_advances_to_voting() {
        var state = advanceToDiscussion(initialState(7))
        state = MafiaReducer.reduce(state, MafiaAction.OpenVote, ctx()).newState
        assertThat(state.phase).isInstanceOf(MafiaPhase.Voting::class)
        assertThat(state.public.activeVote).isNotNull()
    }

    @Test
    fun cast_vote_during_voting_records_choice() {
        var state = advanceToDiscussion(initialState(7))
        state = MafiaReducer.reduce(state, MafiaAction.OpenVote, ctx()).newState
        val voter = state.players.first().id
        val target = state.players.last().id
        state = MafiaReducer.reduce(state, MafiaAction.CastVote(voter, target), ctx()).newState
        val active = state.public.activeVote!!
        assertThat(active.castSoFar[voter]).isEqualTo(target)
    }

    @Test
    fun abstain_then_close_with_no_other_votes_ends_in_skipped_all_abstained() {
        var state = advanceToDiscussion(initialState(7))
        state = MafiaReducer.reduce(state, MafiaAction.OpenVote, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AbstainVote(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.CloseVote, ctx()).newState
        assertThat(state.phase).isInstanceOf(MafiaPhase.VoteAnnouncement::class)
        assertThat(state.public.lastVote!!.outcome)
            .isEqualTo(com.parlor.games.mafia.domain.state.VoteOutcome.AllAbstained)
    }

    @Test
    fun mafia_revote_round_opens_when_round_one_tied_and_revote_configured() {
        // Drive 7-player game to first Night. Two Mafia split votes → round 1 tied → revote.
        var state = MafiaReducer.reduce(initialState(7), MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState

        val mafiaIds = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys.toList()
        val town = state.privatePerPlayer.filterValues { it.role != Role.Mafia }.keys.toList()
        if (mafiaIds.size < 2) return // preset may be 1 mafia; this test exercises the >=2 case

        // First Mafia targets town[0], second Mafia targets town[1] → tied 1-1.
        state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(mafiaIds[0], town[0]), ctx()).newState
        state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(mafiaIds[1], town[1]), ctx()).newState
        state = submitUnsubmittedNightActions(state)
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx()).newState

        val night = state.phase as MafiaPhase.Night
        assertThat(night.mafiaCoordinationRound).isEqualTo(2)
        // Round-2 snapshot has previousRoundTally populated for anonymized display.
        val coord = state.privatePerPlayer.getValue(mafiaIds[0]).mafiaCoordination
        assertThat(coord!!.previousRoundTally).isNotNull()
        assertThat(state.isValidRecoveryState()).isTrue()
        state.players.forEach { player ->
            val publicState = MafiaProjectionPolicy.toPublic(state).state
            val ownPrivate = MafiaProjectionPolicy.toPlayer(state, player.id)
                .state.privatePerPlayer[player.id]
            assertThat(
                MafiaPeerSnapshotValidator.isValid(publicState, ownPrivate, player.id),
                "reducer-produced round-two projection for ${player.id}",
            ).isTrue()
        }

        val missingHistory = state.copy(
            privatePerPlayer = state.privatePerPlayer.mapValues { (_, private) ->
                private.copy(
                    mafiaCoordination = private.mafiaCoordination?.copy(previousRoundTally = null),
                )
            },
        )
        assertThat(missingHistory.isValidRecoveryState()).isFalse()

        val untiedHistory = state.copy(
            privatePerPlayer = state.privatePerPlayer.mapValues { (_, private) ->
                private.copy(
                    mafiaCoordination = private.mafiaCoordination?.copy(
                        previousRoundTally = mapOf(town[0] to 2, town[1] to 1),
                    ),
                )
            },
        )
        assertThat(untiedHistory.isValidRecoveryState()).isFalse()
    }

    @Test
    fun mafia_revote_uses_a_deterministic_tied_target_when_round_two_is_still_tied() {
        var state = MafiaReducer.reduce(initialState(7), MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState
        val mafiaIds = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys.toList()
        if (mafiaIds.size < 2) return

        val town = state.privatePerPlayer.filterValues { it.role != Role.Mafia }.keys.toList()
        // Round 1 tie:
        state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(mafiaIds[0], town[0]), ctx()).newState
        state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(mafiaIds[1], town[1]), ctx()).newState
        state = submitUnsubmittedNightActions(state)
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx()).newState
        // Round 2 still tied:
        state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(mafiaIds[0], town[0]), ctx()).newState
        state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(mafiaIds[1], town[1]), ctx()).newState
        val beforeResolution = state
        val firstResolution = MafiaReducer.reduce(beforeResolution, MafiaAction.ResolveNight, ctx()).newState
        val repeatedResolution = MafiaReducer.reduce(beforeResolution, MafiaAction.ResolveNight, ctx()).newState

        assertThat(firstResolution.phase).isInstanceOf<MafiaPhase.NightAnnouncement>()
        assertThat(repeatedResolution.phase).isEqualTo(firstResolution.phase)
        val selectedTarget = firstResolution.public.lastNight?.killedPlayerId
        assertThat(selectedTarget == town[0] || selectedTarget == town[1]).isTrue()
        assertThat(repeatedResolution.public.lastNight).isEqualTo(firstResolution.public.lastNight)
    }

    @Test
    fun mark_player_disconnected_and_reconnected_round_trips() {
        val state = initialState(7)
        val p = state.players.first().id
        val disconnected = MafiaReducer.reduce(state, MafiaAction.MarkPlayerDisconnected(p), ctx()).newState
        assertThat(disconnected.public.disconnectedPlayers.contains(p)).isTrue()
        val reconnected = MafiaReducer.reduce(disconnected, MafiaAction.MarkPlayerReconnected(p), ctx()).newState
        assertThat(reconnected.public.disconnectedPlayers.contains(p)).isFalse()
    }

    @Test
    fun setup_disconnect_reconnects_before_grace_expiry_without_dropping_seat() {
        val state = initialState(7)
        val player = state.players.first().id
        val disconnected = MafiaReducer.reduce(
            state,
            MafiaAction.MarkPlayerDisconnected(player),
            ctx(),
        ).newState
        val reconnected = MafiaReducer.reduce(
            disconnected,
            MafiaAction.MarkPlayerReconnected(player),
            ctx(),
        ).newState

        assertThat(player in reconnected.public.disconnectedPlayers).isFalse()
        assertThat(player in reconnected.public.droppedPlayers).isFalse()
        val started = MafiaReducer.reduce(reconnected, MafiaAction.StartGame, ctx()).newState
        assertThat(started.phase).isEqualTo(MafiaPhase.RoleAssignment)
    }

    @Test
    fun continue_without_player_drops_only_a_currently_disconnected_seat() {
        val state = initialState(7)
        val p = state.players.first().id
        val disconnected = MafiaReducer.reduce(
            state,
            MafiaAction.MarkPlayerDisconnected(p),
            ctx(),
        ).newState
        val dropped = MafiaReducer.reduce(
            disconnected,
            MafiaAction.ContinueWithoutPlayer(p),
            ctx(),
        ).newState
        assertThat(dropped.public.droppedPlayers.contains(p)).isTrue()
        assertThat(dropped.phase).isEqualTo(MafiaPhase.PostGame)
    }

    @Test
    fun continue_without_player_rejects_a_connected_seat() {
        val state = initialState(7)
        val p = state.players.first().id

        val result = MafiaReducer.reduce(
            state,
            MafiaAction.ContinueWithoutPlayer(p),
            ctx(),
        )

        assertThat(result.newState).isEqualTo(state)
        assertThat(result.events).isEmpty()
    }

    @Test
    fun end_game_jumps_to_post_game() {
        val state = initialState(7)
        val ended = MafiaReducer.reduce(state, MafiaAction.EndGame, ctx()).newState
        assertThat(ended.phase).isEqualTo(MafiaPhase.PostGame)
        // Roles have not been assigned in Setup, so an early end must not
        // fabricate a Town victory.
        assertThat(ended.public.winner).isNull()
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private fun advanceToDiscussion(initial: MafiaState): MafiaState {
        var state = initial
        state = MafiaReducer.reduce(state, MafiaAction.StartGame, ctx()).newState
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeRoleViewed(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.AdvanceFromRoleAssignment, ctx()).newState

        // Drive the Night with no kill (everyone abstains by submitting null).
        val mafiaIds = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys
        for (m in mafiaIds) {
            state = MafiaReducer.reduce(state, MafiaAction.SubmitMafiaKillVote(m, target = null), ctx()).newState
        }
        state = submitUnsubmittedNightActions(state)
        state = MafiaReducer.reduce(state, MafiaAction.ResolveNight, ctx()).newState
        // Everyone acks night announcement.
        for (p in state.players) {
            state = MafiaReducer.reduce(state, MafiaAction.AcknowledgeNightAnnouncement(p.id), ctx()).newState
        }
        state = MafiaReducer.reduce(state, MafiaAction.OpenDiscussion, ctx()).newState
        return state
    }

    /**
     * Submit a deterministic skip for every active living seat that has not
     * already acted. Tests that care about a Mafia target submit it first, then
     * use this helper to satisfy the reducer-owned readiness gate.
     */
    private fun submitUnsubmittedNightActions(initial: MafiaState): MafiaState {
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
            state = MafiaReducer.reduce(state, action, ctx()).newState
        }
        return state
    }
}
