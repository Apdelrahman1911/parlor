package com.parlor.games.mafia.snapshot

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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Isolated audit proof. Not registered in any source set without root-owned temporary injection. */
class MC01DoctorHistoryRecoveryTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
    private val definition = MafiaDefinition(json)
    private val context = DefaultReducerContext(
        FakeClock(Instant.fromEpochSeconds(0)),
        RandomSource.seeded(43L),
    )

    @Test
    fun witnessAcceptedContradictionReenablesIllegalConsecutiveProtection() {
        val valid = secondNightAfterProtection()
        val doctor = valid.privatePerPlayer.entries.single { it.value.role == Role.Doctor }.key
        val previousTarget = requireNotNull(valid.privatePerPlayer.getValue(doctor).previousDoctorProtect)
        val repeat = MafiaAction.SubmitDoctorProtect(doctor, previousTarget)
        assertEquals(valid, reduce(valid, repeat), "valid state forbids repeating previous-night target")
        val corrupted = valid.copy(privatePerPlayer = valid.privatePerPlayer + (
            doctor to valid.privatePerPlayer.getValue(doctor).copy(previousDoctorProtect = null)
        ))
        // Expected current-source witness: accepted despite contradictory authenticated state.
        val restored = definition.snapshotCodec().decode(definition.snapshotCodec().encode(corrupted))
        assertNotEquals(restored, reduce(restored, repeat))
        assertEquals(previousTarget, reduce(restored, repeat).privatePerPlayer.getValue(doctor).pendingNightChoice)
    }

    @Test
    fun regressionCodecShouldRejectDoctorHistoryContradictingLastEffectiveProtection() {
        val valid = secondNightAfterProtection()
        val doctor = valid.privatePerPlayer.entries.single { it.value.role == Role.Doctor }.key
        val corrupted = valid.copy(privatePerPlayer = valid.privatePerPlayer + (
            doctor to valid.privatePerPlayer.getValue(doctor).copy(previousDoctorProtect = null)
        ))
        // The source's canonical codec promises reducer-reachable local snapshots.
        assertFailsWith<IllegalArgumentException> { definition.snapshotCodec().encode(corrupted) }
    }

    private fun secondNightAfterProtection(): MafiaState {
        val players = (0 until 6).map { Player(PlayerId("audit-$it"), "Audit $it", it) }
        var state = definition.createInitialState(SessionConfig(
            sessionId = SessionId("audit-doctor-history"),
            caseId = CaseId("default"),
            modeId = MafiaIds.ClassicModeId,
            players = players,
            randomSeed = 43L,
        ))
        state = reduce(state, MafiaAction.StartGame)
        players.forEach { state = reduce(state, MafiaAction.AcknowledgeRoleViewed(it.id)) }
        state = reduce(state, MafiaAction.AdvanceFromRoleAssignment)
        val doctor = state.privatePerPlayer.entries.single { it.value.role == Role.Doctor }.key
        val target = players.first { it.id != doctor }.id
        players.forEach { player ->
            val action = when (state.privatePerPlayer.getValue(player.id).role) {
                Role.Doctor -> MafiaAction.SubmitDoctorProtect(player.id, target)
                Role.Detective -> MafiaAction.SubmitDetectiveInspect(player.id, null)
                Role.Mafia -> MafiaAction.SubmitMafiaKillVote(player.id, null)
                Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(player.id, null)
            }
            state = reduce(state, action)
        }
        state = reduce(state, MafiaAction.ResolveNight)
        players.forEach { state = reduce(state, MafiaAction.AcknowledgeNightAnnouncement(it.id)) }
        state = reduce(state, MafiaAction.OpenDiscussion)
        state = reduce(state, MafiaAction.OpenVote)
        players.forEach { state = reduce(state, MafiaAction.AbstainVote(it.id)) }
        state = reduce(state, MafiaAction.CloseVote)
        players.forEach { state = reduce(state, MafiaAction.AcknowledgeVoteAnnouncement(it.id)) }
        state = reduce(state, MafiaAction.AdvanceFromVoteAnnouncement)
        assertEquals(MafiaPhase.Night(day = 2), state.phase)
        assertTrue(state.isValidRecoveryState())
        assertEquals(target, state.hostOnly.nightLog.last().doctorProtect)
        assertEquals(target, state.privatePerPlayer.getValue(doctor).previousDoctorProtect)
        return state
    }

    private fun reduce(state: MafiaState, action: MafiaAction): MafiaState =
        MafiaReducer.reduce(state, action, context).newState
}
