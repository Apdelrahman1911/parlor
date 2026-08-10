package com.parlor.games.mafia.ui.flow.passandplay

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test

class MafiaPassAndPlayNightQueueTest {

    private val context = DefaultReducerContext(
        clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
        random = RandomSource.seeded(91L),
    )

    private fun nightState(): MafiaState {
        val players = (0 until 7).map { index ->
            Player(PlayerId("p$index"), "P$index", seat = index)
        }
        var state = MafiaDefinition(Json).createInitialState(
            SessionConfig(
                sessionId = SessionId("pass-night"),
                caseId = CaseId("default"),
                modeId = MafiaIds.ClassicModeId,
                players = players,
                randomSeed = 91L,
            ),
        )
        state = MafiaReducer.reduce(state, MafiaAction.StartGame, context).newState
        players.forEach { player ->
            state = MafiaReducer.reduce(
                state,
                MafiaAction.AcknowledgeRoleViewed(player.id),
                context,
            ).newState
        }
        state = MafiaReducer.reduce(
            state,
            MafiaAction.AdvanceFromRoleAssignment,
            context,
        ).newState
        return state
    }

    @Test
    fun queue_advances_only_after_reducer_commits_the_players_action() {
        var state = nightState()
        val phase = state.phase as MafiaPhase.Night
        val first = pendingNightPlayers(state, phase).first()
        val role = state.privatePerPlayer.getValue(first.id).role

        // A deliberately wrong role action is rejected by the reducer. The UI
        // queue must retain the player so Hide can be tapped again safely.
        val rejected = when (role) {
            Role.Mafia -> MafiaAction.SubmitDoctorProtect(first.id, null)
            else -> MafiaAction.SubmitMafiaKillVote(first.id, null)
        }
        val unchanged = MafiaReducer.reduce(state, rejected, context).newState
        assertThat(unchanged).isEqualTo(state)
        assertThat(pendingNightPlayers(unchanged, phase).map { it.id })
            .containsExactly(*pendingNightPlayers(state, phase).map { it.id }.toTypedArray())

        val accepted = when (role) {
            Role.Mafia -> MafiaAction.SubmitMafiaKillVote(first.id, null)
            Role.Doctor -> MafiaAction.SubmitDoctorProtect(first.id, null)
            Role.Detective -> MafiaAction.SubmitDetectiveInspect(first.id, null)
            Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(first.id, null)
        }
        state = MafiaReducer.reduce(state, accepted, context).newState

        assertThat(pendingNightPlayers(state, phase).map { it.id }).doesNotContain(first.id)
    }

    @Test
    fun coordination_revote_contains_only_mafia_without_round_two_submission() {
        val initial = nightState()
        val roundTwo = (initial.phase as MafiaPhase.Night).copy(mafiaCoordinationRound = 2)
        val mafiaIds = initial.privatePerPlayer
            .filterValues { it.role == Role.Mafia }
            .keys
            .sortedBy { id -> initial.players.first { it.id == id }.seat }

        assertThat(pendingNightPlayers(initial, roundTwo).map { it.id })
            .containsExactly(*mafiaIds.toTypedArray())
    }
}
