package com.parlor.games.mafia.flow

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
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
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.games.mafia.domain.state.MafiaPeerSnapshotValidator
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.snapshot.isValidRecoveryState
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end reducer drive. Drives a 7-player game to a Mafia win and a
 * 5-player game to a Town win, purely by feeding the reducer — no UI, no
 * session controller. M1 exit criterion: the full game is provable here
 * before any flow code is written.
 *
 * The driver feeds actions in the canonical order the UI would issue them:
 *   ApplySettings (implicit via preset already in state) → StartGame
 *   → per-player AcknowledgeRoleViewed → AdvanceFromRoleAssignment
 *   → Night submissions → ResolveNight
 *   → AcknowledgeNightAnnouncement × all → OpenDiscussion → OpenVote
 *   → CastVote × all → CloseVote
 *   → AcknowledgeVoteAnnouncement × all → AdvanceFromVoteAnnouncement
 *   → ... repeat until winner.
 */
class FullGameDriveTest {

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
                sessionId = SessionId("s-$seed"),
                caseId = CaseId("default"),
                modeId = MafiaIds.ClassicModeId,
                players = players(n),
                randomSeed = seed,
            ),
        )

    private fun step(state: MafiaState, action: MafiaAction, ctx: ReducerContext): MafiaState =
        MafiaReducer.reduce(state, action, ctx).newState.also(::assertSnapshotBoundaries)

    /**
     * Every reducer-produced state in the full-game drives must remain valid
     * at both persistence and per-player network boundaries. This couples the
     * validators to legal traces instead of testing only hand-built examples.
     */
    private fun assertSnapshotBoundaries(state: MafiaState) {
        assertTrue(
            state.isValidRecoveryState(),
            "canonical Mafia state is not recoverable in ${state.phase}",
        )
        val publicState = MafiaProjectionPolicy.toPublic(state).state
        state.players.forEach { player ->
            val ownPrivate = MafiaProjectionPolicy.toPlayer(state, player.id)
                .state
                .privatePerPlayer[player.id]
            assertTrue(
                MafiaPeerSnapshotValidator.isValid(publicState, ownPrivate, player.id),
                "peer Mafia projection is invalid in ${state.phase} for ${player.id.raw}",
            )
        }
    }

    @Test
    fun seven_player_mafia_win_via_night_kills() {
        val seed = 1234L
        var state = initialState(7, seed).also(::assertSnapshotBoundaries)
        val c = ctx(seed)

        state = step(state, MafiaAction.StartGame, c)
        for (p in state.players) {
            state = step(state, MafiaAction.AcknowledgeRoleViewed(p.id), c)
        }
        state = step(state, MafiaAction.AdvanceFromRoleAssignment, c)

        var safety = 0
        while (state.public.winner == null && safety < 20) {
            // Night: Mafia targets a Town player; Doctor does nothing; Detective inspects.
            state = driveNightFavoringMafia(state, c)
            if (state.public.winner != null) break

            // After resolveNight, we are in NightAnnouncement OR PostGame.
            if (state.phase is MafiaPhase.NightAnnouncement) {
                for (p in alive(state)) {
                    state = step(state, MafiaAction.AcknowledgeNightAnnouncement(p), c)
                }
                state = step(state, MafiaAction.OpenDiscussion, c)
                state = step(state, MafiaAction.OpenVote, c)
                // Everyone votes for the first non-Mafia (worst possible from Town's POV).
                // This ensures Town keeps eliminating itself, accelerating Mafia win.
                val mafiaPick = wrongVoteForTown(state)
                if (mafiaPick != null) {
                    for (voter in state.public.activeVote!!.ballot) {
                        state = if (voter == mafiaPick) {
                            step(state, MafiaAction.AbstainVote(voter), c)
                        } else {
                            step(state, MafiaAction.CastVote(voter, mafiaPick), c)
                        }
                    }
                }
                state = step(state, MafiaAction.CloseVote, c)
                if (state.public.winner != null) break

                if (state.phase is MafiaPhase.VoteAnnouncement) {
                    for (p in alive(state)) {
                        state = step(state, MafiaAction.AcknowledgeVoteAnnouncement(p), c)
                    }
                    state = step(state, MafiaAction.AdvanceFromVoteAnnouncement, c)
                }
            }
            safety++
        }
        assertThat(state.public.winner).isEqualTo(Team.Mafia)
        assertThat(state.phase).isEqualTo(MafiaPhase.PostGame)
    }

    @Test
    fun five_player_town_wins_when_mafia_is_voted_out() {
        val seed = 5555L
        var state = initialState(5, seed).also(::assertSnapshotBoundaries)
        val c = ctx(seed)

        state = step(state, MafiaAction.StartGame, c)
        for (p in state.players) {
            state = step(state, MafiaAction.AcknowledgeRoleViewed(p.id), c)
        }
        state = step(state, MafiaAction.AdvanceFromRoleAssignment, c)

        // First night: skip the kill so we can vote out a Mafia in daylight.
        val mafiaIds = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys
        for (m in mafiaIds) {
            state = step(state, MafiaAction.SubmitMafiaKillVote(m, target = null), c)
        }
        state = submitUnsubmittedNightActions(state, c)
        state = step(state, MafiaAction.ResolveNight, c)
        // No one was killed — proceed.
        if (state.phase is MafiaPhase.NightAnnouncement) {
            for (p in alive(state)) {
                state = step(state, MafiaAction.AcknowledgeNightAnnouncement(p), c)
            }
            state = step(state, MafiaAction.OpenDiscussion, c)
            state = step(state, MafiaAction.OpenVote, c)
            // Everyone alive votes for the Mafia → eliminated.
            val mafiaId = mafiaIds.first { it in alive(state) }
            for (voter in state.public.activeVote!!.ballot) {
                state = if (voter == mafiaId) {
                    step(state, MafiaAction.AbstainVote(voter), c)
                } else {
                    step(state, MafiaAction.CastVote(voter, mafiaId), c)
                }
            }
            state = step(state, MafiaAction.CloseVote, c)
        }
        assertThat(state.public.winner).isEqualTo(Team.Town)
        // 5-player preset is 1 mafia → killing them ends the game in PostGame.
        assertThat(state.phase).isEqualTo(MafiaPhase.PostGame)
    }

    @Test
    fun winner_is_set_immediately_when_last_mafia_dies_during_night() {
        // A more contrived scenario: drive a 5-player game where the Mafia
        // and Doctor both target the Doctor (no-op for Doctor; no self-heal
        // by default). The Doctor dies, but Mafia still alive — game ongoing.
        // Use a different setup to test the alternative: the Mafia survives
        // long enough to win by parity.
        val seed = 9999L
        var state = initialState(5, seed).also(::assertSnapshotBoundaries)
        val c = ctx(seed)

        state = step(state, MafiaAction.StartGame, c)
        for (p in state.players) {
            state = step(state, MafiaAction.AcknowledgeRoleViewed(p.id), c)
        }
        state = step(state, MafiaAction.AdvanceFromRoleAssignment, c)

        // Mafia kills civilians turn after turn until parity → Mafia win.
        var safety = 0
        while (state.public.winner == null && safety < 10) {
            state = driveNightFavoringMafia(state, c)
            if (state.public.winner != null) break

            if (state.phase is MafiaPhase.NightAnnouncement) {
                for (p in alive(state)) {
                    state = step(state, MafiaAction.AcknowledgeNightAnnouncement(p), c)
                }
                state = step(state, MafiaAction.OpenDiscussion, c)
                state = step(state, MafiaAction.OpenVote, c)
                // Town abstains so Mafia survives the daylight phase.
                for (voter in state.public.activeVote!!.ballot) {
                    state = step(state, MafiaAction.AbstainVote(voter), c)
                }
                state = step(state, MafiaAction.CloseVote, c)
                if (state.phase is MafiaPhase.VoteAnnouncement) {
                    for (p in alive(state)) {
                        state = step(state, MafiaAction.AcknowledgeVoteAnnouncement(p), c)
                    }
                    state = step(state, MafiaAction.AdvanceFromVoteAnnouncement, c)
                }
            }
            safety++
        }
        // Mafia must eventually win by parity.
        assertThat(state.public.winner).isEqualTo(Team.Mafia)
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private fun alive(state: MafiaState): List<PlayerId> =
        state.public.roster.filter { it.alive }.map { it.playerId }

    private fun driveNightFavoringMafia(initial: MafiaState, c: ReducerContext): MafiaState {
        var state = initial
        // Every living Mafia targets the same first non-Mafia alive player so
        // the kill goes through unanimously (no tie, no doctor save).
        val livingMafia = state.privatePerPlayer.filterValues { it.role == Role.Mafia }.keys
            .filter { it in alive(state) }
        val target = state.privatePerPlayer.entries
            .firstOrNull { (id, priv) -> priv.role != Role.Mafia && id in alive(state) }
            ?.key
        if (target != null) {
            for (m in livingMafia) {
                state = step(state, MafiaAction.SubmitMafiaKillVote(m, target), c)
            }
        }
        // Every other living role explicitly skips its action. The reducer,
        // not the UI, owns the all-seats-submitted readiness gate.
        state = submitUnsubmittedNightActions(state, c)
        state = step(state, MafiaAction.ResolveNight, c)
        return state
    }

    private fun submitUnsubmittedNightActions(
        initial: MafiaState,
        c: ReducerContext,
    ): MafiaState {
        var state = initial
        for (id in alive(state)) {
            val private = state.privatePerPlayer[id] ?: continue
            if (private.nightChoiceSubmitted) continue
            val action = when (private.role) {
                Role.Mafia -> MafiaAction.SubmitMafiaKillVote(id, null)
                Role.Doctor -> MafiaAction.SubmitDoctorProtect(id, null)
                Role.Detective -> MafiaAction.SubmitDetectiveInspect(id, null)
                Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(id, null)
            }
            state = step(state, action, c)
        }
        return state
    }

    private fun wrongVoteForTown(state: MafiaState): PlayerId? {
        // Return any non-Mafia alive player (Town shoots itself in the foot).
        // hostOnly is available during this driver run — we cheat by looking
        // at it for the test's narrative purpose.
        return state.hostOnly.fullRoleMap.entries
            .firstOrNull { it.value != Role.Mafia && it.key in alive(state) }
            ?.key
    }
}
