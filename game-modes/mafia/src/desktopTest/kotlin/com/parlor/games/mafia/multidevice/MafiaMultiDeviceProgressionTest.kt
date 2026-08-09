package com.parlor.games.mafia.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.parlor.core.ids.CaseId
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
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.ui.flow.multidevice.nextHostAdvance
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Multi-device host-progression regression.
 *
 * In multi-device the host is the sole authority for the gated phase advances
 * (`AdvanceFromRoleAssignment`, `ResolveNight`, `OpenDiscussion`, `CloseVote`,
 * `AdvanceFromVoteAnnouncement`). The multi-device router previously wired only
 * two of them, so the game deadlocked at the first transition (role assignment
 * → night). The fix is [nextHostAdvance], which decides — purely — which advance
 * the host should submit once each readiness gate holds.
 *
 * This test drives a full multi-device game where **every gated advance is issued
 * only via [nextHostAdvance]** (never hardcoded). Peers' acks/votes/night actions
 * are fed directly, as the authenticated host bridge would. If [nextHostAdvance]
 * failed to advance any transition, the game could not reach a winner and the
 * safety-bounded loop would assert-fail. The only host action issued directly is
 * `OpenVote` (the Discussion → Voting step is a deliberate host tap with no
 * auto-advance).
 */
class MafiaMultiDeviceProgressionTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private fun ctx(seed: Long): ReducerContext = DefaultReducerContext(
        clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
        random = RandomSource.seeded(seed),
    )

    private fun players(n: Int): List<Player> =
        (0 until n).map { Player(PlayerId("p$it"), "P$it", seat = it) }

    private fun initialState(n: Int, seed: Long): MafiaState =
        MafiaDefinition(json).createInitialState(
            SessionConfig(
                sessionId = SessionId("md-$seed"),
                caseId = CaseId("default"),
                modeId = MafiaIds.ClassicModeId,
                players = players(n),
                randomSeed = seed,
            ),
        )

    private fun step(state: MafiaState, action: MafiaAction, c: ReducerContext): MafiaState =
        MafiaReducer.reduce(state, action, c).newState

    private fun alive(state: MafiaState): List<PlayerId> =
        state.public.roster.filter { it.alive }.map { it.playerId }

    /** Apply every host advance [nextHostAdvance] reports until none is ready. */
    private fun driveHostAdvances(start: MafiaState, c: ReducerContext): MafiaState {
        var s = start
        var guard = 0
        while (guard++ < 50) {
            val advance = nextHostAdvance(s) ?: break
            s = step(s, advance, c)
        }
        return s
    }

    /** Every alive player submits their role's night action (sets nightChoiceSubmitted). */
    private fun submitAllNightActions(start: MafiaState, c: ReducerContext): MafiaState {
        var s = start
        // All Mafia target the same first non-Mafia alive player (unanimous →
        // no second coordination round) so the kill lands deterministically.
        val mafiaTarget = s.privatePerPlayer.entries
            .firstOrNull { (id, priv) -> priv.role != Role.Mafia && id in alive(s) }?.key
        for (id in alive(s)) {
            val role = s.privatePerPlayer[id]?.role ?: continue
            val other = alive(s).firstOrNull { it != id }
            s = when (role) {
                Role.Mafia -> step(s, MafiaAction.SubmitMafiaKillVote(id, mafiaTarget), c)
                Role.Doctor -> step(s, MafiaAction.SubmitDoctorProtect(id, null), c)
                Role.Detective -> step(s, MafiaAction.SubmitDetectiveInspect(id, other), c)
                Role.Civilian -> step(s, MafiaAction.SubmitCivilianSuspicion(id, other), c)
            }
        }
        // Detective results are created immediately by submission and must be
        // acknowledged before the reducer will resolve the night.
        for (id in alive(s)) {
            val priv = s.privatePerPlayer[id] ?: continue
            if (priv.pendingDetectiveResult != null && !priv.detectiveResultAcknowledged) {
                s = step(s, MafiaAction.AcknowledgeDetectiveResult(id), c)
            }
        }
        return s
    }

    @Test
    fun multi_device_host_driver_progresses_a_full_game_to_a_winner() {
        val seed = 24680L
        val c = ctx(seed)
        var state = initialState(7, seed)

        state = step(state, MafiaAction.StartGame, c)
        assertThat(state.phase).isEqualTo(MafiaPhase.RoleAssignment)

        // Peers (and host) acknowledge their roles.
        for (p in state.players) {
            state = step(state, MafiaAction.AcknowledgeRoleViewed(p.id), c)
        }
        // The host driver must now advance RoleAssignment → Night. Before the
        // fix nothing submitted AdvanceFromRoleAssignment and this stalled.
        state = driveHostAdvances(state, c)
        assertThat(state.phase is MafiaPhase.Night).isEqualTo(true)

        var safety = 0
        while (state.public.winner == null && safety++ < 30) {
            // NIGHT: everyone alive submits; host driver fires ResolveNight.
            state = submitAllNightActions(state, c)
            state = driveHostAdvances(state, c)
            if (state.public.winner != null) break

            // NIGHT ANNOUNCEMENT: everyone acks; host driver fires OpenDiscussion.
            if (state.phase is MafiaPhase.NightAnnouncement) {
                for (p in alive(state)) {
                    state = step(state, MafiaAction.AcknowledgeNightAnnouncement(p), c)
                }
                state = driveHostAdvances(state, c)
                assertThat(state.phase is MafiaPhase.Discussion).isEqualTo(true)

                // DISCUSSION → host taps Open Vote (deliberate manual step).
                state = step(state, MafiaAction.OpenVote, c)

                // VOTING: town votes out a Mafia member; host driver fires CloseVote.
                // The targeted Mafia can't self-vote (disabled by default), so
                // they abstain — every ballot member must still act for the
                // CloseVote gate to fire.
                val mafiaId = state.privatePerPlayer.entries
                    .first { (id, priv) -> priv.role == Role.Mafia && id in alive(state) }.key
                for (voter in state.public.activeVote!!.ballot) {
                    state = if (voter == mafiaId) {
                        step(state, MafiaAction.AbstainVote(voter), c)
                    } else {
                        step(state, MafiaAction.CastVote(voter, mafiaId), c)
                    }
                }
                state = driveHostAdvances(state, c)
                if (state.public.winner != null) break

                // VOTE ANNOUNCEMENT: everyone acks; host driver fires AdvanceFromVoteAnnouncement.
                if (state.phase is MafiaPhase.VoteAnnouncement) {
                    for (p in alive(state)) {
                        state = step(state, MafiaAction.AcknowledgeVoteAnnouncement(p), c)
                    }
                    state = driveHostAdvances(state, c)
                }
            }
        }

        // Reaching a winner at all proves every gated advance fired through the
        // host driver — a single missing advance would have deadlocked the loop.
        assertThat(state.public.winner).isNotNull()
        assertThat(state.phase).isEqualTo(MafiaPhase.PostGame)
    }

    @Test
    fun role_assignment_does_not_advance_until_every_active_player_has_acked() {
        val seed = 1357L
        val c = ctx(seed)
        var state = step(initialState(5, seed), MafiaAction.StartGame, c)

        // No acks yet → no advance.
        assertThat(nextHostAdvance(state)).isNull()

        // All but one acked → still no advance.
        val ids = state.players.map { it.id }
        for (id in ids.dropLast(1)) {
            state = step(state, MafiaAction.AcknowledgeRoleViewed(id), c)
        }
        assertThat(nextHostAdvance(state)).isNull()

        // Final ack → AdvanceFromRoleAssignment becomes ready.
        state = step(state, MafiaAction.AcknowledgeRoleViewed(ids.last()), c)
        assertThat(nextHostAdvance(state)).isEqualTo(MafiaAction.AdvanceFromRoleAssignment)
    }

    @Test
    fun ready_advance_is_suspended_during_disconnect_and_reoffered_after_reconnect() {
        val seed = 2468L
        val c = ctx(seed)
        var state = step(initialState(5, seed), MafiaAction.StartGame, c)
        for (player in state.players) {
            state = step(state, MafiaAction.AcknowledgeRoleViewed(player.id), c)
        }
        assertThat(nextHostAdvance(state)).isEqualTo(MafiaAction.AdvanceFromRoleAssignment)

        val missing = state.players.last().id
        state = step(state, MafiaAction.MarkPlayerDisconnected(missing), c)
        assertThat(nextHostAdvance(state)).isNull()

        state = step(state, MafiaAction.MarkPlayerReconnected(missing), c)
        assertThat(nextHostAdvance(state)).isEqualTo(MafiaAction.AdvanceFromRoleAssignment)
    }

    @Test
    fun an_eliminated_host_can_still_resolve_the_night() {
        // Regression for the dead-host deadlock: the manual Resolve button lives
        // only in the alive-host branch, so night resolution must come from the
        // host driver — which keys off living players' submissions, not the
        // host's own aliveness. Here p0 (the would-be host) is dead; once the
        // living players have submitted, ResolveNight must still be offered.
        val seed = 333L
        val c = ctx(seed)
        var state = step(initialState(5, seed), MafiaAction.StartGame, c)
        for (p in state.players) state = step(state, MafiaAction.AcknowledgeRoleViewed(p.id), c)
        state = driveHostAdvances(state, c) // → Night day 1

        // Drive one full night/day so a player dies, giving us a dead seat.
        state = submitAllNightActions(state, c)
        state = driveHostAdvances(state, c) // ResolveNight → NightAnnouncement
        // Someone was killed at night; capture a dead player.
        val deadId = state.players.map { it.id }.firstOrNull { it !in alive(state) }
        assertThat(deadId).isNotNull()

        // Treat the dead player as the host. Advance to the next night.
        for (p in alive(state)) state = step(state, MafiaAction.AcknowledgeNightAnnouncement(p), c)
        state = driveHostAdvances(state, c) // → Discussion
        if (state.phase is MafiaPhase.Discussion) {
            state = step(state, MafiaAction.OpenVote, c)
            for (voter in state.public.activeVote!!.ballot) {
                state = step(state, MafiaAction.AbstainVote(voter), c)
            }
            state = driveHostAdvances(state, c) // CloseVote → VoteAnnouncement
        }
        if (state.phase is MafiaPhase.VoteAnnouncement) {
            for (p in alive(state)) state = step(state, MafiaAction.AcknowledgeVoteAnnouncement(p), c)
            state = driveHostAdvances(state, c) // → Night day 2
        }

        if (state.public.winner == null && state.phase is MafiaPhase.Night) {
            // Living players submit; the dead "host" never submits, yet the
            // driver must still offer ResolveNight (keyed on living players).
            state = submitAllNightActions(state, c)
            assertThat(nextHostAdvance(state)).isEqualTo(MafiaAction.ResolveNight)
        }
    }
}
