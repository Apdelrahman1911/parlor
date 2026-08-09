package com.parlor.games.mafia.snapshot

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.storage.snapshot.InMemorySnapshotStore
import kotlinx.datetime.Instant as DateTimeInstant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Instant

class MafiaSnapshotRecoveryTest {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }
    private val definition = MafiaDefinition(json)
    private val players = (0 until 5).map { seat ->
        Player(PlayerId("p${seat + 1}"), "Player ${seat + 1}", seat)
    }

    @Test
    fun valid_local_snapshot_restores_full_authoritative_state() = runTest {
        val sessionId = SessionId("mafia-resume")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, state))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat((result as Result.Success).data.state).isEqualTo(state)
        assertThat(result.data.sessionId).isEqualTo(sessionId)
    }

    @Test
    fun structurally_inconsistent_roster_is_rejected_after_authentic_decoding() = runTest {
        val sessionId = SessionId("mafia-bad-roster")
        val valid = definition.createInitialState(config(sessionId))
        val corrupted = valid.copy(public = valid.public.copy(roster = valid.public.roster.dropLast(1)))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, corrupted))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun unknown_local_play_mode_is_not_silently_changed_to_pass_and_play() = runTest {
        val sessionId = SessionId("mafia-unknown-mode")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(
            snapshot(sessionId, state).copy(
                metadata = mapOf(MAFIA_PLAY_MODE_KEY to "ModifiedMode"),
            ),
        )

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun missing_play_mode_is_rejected_because_mafia_has_no_legacy_snapshot_format() = runTest {
        val sessionId = SessionId("mafia-missing-mode")
        val state = definition.createInitialState(config(sessionId))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, state).copy(metadata = emptyMap()))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun phase_day_and_active_vote_must_form_a_reducer_reachable_state() = runTest {
        val sessionId = SessionId("mafia-impossible-phase")
        val state = nightState(sessionId)
        val phase = state.phase as MafiaPhase.Night
        val corrupted = state.copy(phase = phase.copy(day = phase.day + 1))
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, corrupted))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun role_map_counts_and_private_team_identity_cannot_be_forged() = runTest {
        val sessionId = SessionId("mafia-forged-role")
        val state = nightState(sessionId)
        val playerId = state.players.first().id
        val original = state.privatePerPlayer.getValue(playerId)
        val corrupted = state.copy(
            privatePerPlayer = state.privatePerPlayer +
                (playerId to original.copy(team = if (original.team == com.parlor.games.mafia.domain.state.Team.Mafia) {
                    com.parlor.games.mafia.domain.state.Team.Town
                } else {
                    com.parlor.games.mafia.domain.state.Team.Mafia
                })),
        )
        val store = InMemorySnapshotStore()
        store.save(snapshot(sessionId, corrupted))

        val result = loadMafiaResumedSession(store, definition, sessionId)

        assertThat(result).isInstanceOf(Result.Failure::class)
        assertThat((result as Result.Failure).error).isEqualTo(DataError.CorruptedData)
    }

    @Test
    fun terminal_state_erases_transient_private_action_data_before_persistence() {
        val state = nightState(SessionId("mafia-terminal-clean"))
        val actingPlayer = state.players.first()
        val role = state.privatePerPlayer.getValue(actingPlayer.id).role
        val withAction = MafiaReducer.reduce(
            state,
            when (role) {
                com.parlor.games.mafia.domain.state.Role.Mafia ->
                    MafiaAction.SubmitMafiaKillVote(actingPlayer.id, null)
                com.parlor.games.mafia.domain.state.Role.Doctor ->
                    MafiaAction.SubmitDoctorProtect(actingPlayer.id, null)
                com.parlor.games.mafia.domain.state.Role.Detective ->
                    MafiaAction.SubmitDetectiveInspect(actingPlayer.id, null)
                com.parlor.games.mafia.domain.state.Role.Civilian ->
                    MafiaAction.SubmitCivilianSuspicion(actingPlayer.id, null)
            },
            reducerContext(),
        ).newState

        val terminal = MafiaReducer.reduce(withAction, MafiaAction.EndGame, reducerContext()).newState

        assertThat(terminal.phase).isEqualTo(MafiaPhase.PostGame)
        assertThat(terminal.isValidRecoveryState()).isEqualTo(true)
        terminal.privatePerPlayer.values.forEach { private ->
            assertThat(private.mafiaCoordination).isEqualTo(null)
            assertThat(private.pendingNightChoice).isEqualTo(null)
            assertThat(private.pendingDetectiveResult).isEqualTo(null)
            assertThat(private.nightChoiceSubmitted).isEqualTo(false)
        }
    }

    private fun config(sessionId: SessionId) = SessionConfig(
        sessionId = sessionId,
        caseId = CaseId("default"),
        modeId = MafiaIds.ClassicModeId,
        players = players,
        randomSeed = 42L,
    )

    private fun reducerContext() = DefaultReducerContext(
        clock = FakeClock(DateTimeInstant.fromEpochSeconds(1_700_000_000)),
        random = RandomSource.seeded(42L),
    )

    private fun nightState(sessionId: SessionId): com.parlor.games.mafia.domain.state.MafiaState {
        var state = definition.createInitialState(config(sessionId))
        state = MafiaReducer.reduce(state, MafiaAction.StartGame, reducerContext()).newState
        players.forEach { player ->
            state = MafiaReducer.reduce(
                state,
                MafiaAction.AcknowledgeRoleViewed(player.id),
                reducerContext(),
            ).newState
        }
        return MafiaReducer.reduce(
            state,
            MafiaAction.AdvanceFromRoleAssignment,
            reducerContext(),
        ).newState
    }

    private fun snapshot(
        sessionId: SessionId,
        state: com.parlor.games.mafia.domain.state.MafiaState,
    ) = GameSnapshot(
        sessionId = sessionId,
        gameId = MafiaIds.GameId,
        engineVersion = MAFIA_SNAPSHOT_VERSION,
        createdAt = Instant.fromEpochSeconds(1),
        phaseId = state.phase.id,
        payload = definition.snapshotCodec().encode(state),
        metadata = mapOf(MAFIA_PLAY_MODE_KEY to MAFIA_PASS_AND_PLAY_MODE),
    )
}
