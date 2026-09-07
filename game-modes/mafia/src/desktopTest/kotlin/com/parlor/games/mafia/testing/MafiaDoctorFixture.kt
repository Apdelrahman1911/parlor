package com.parlor.games.mafia.testing

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
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Legal reducer traces only: no state copies to manufacture phases or history. */
internal class MafiaDoctorFixture(
    val settings: MafiaSettings = MafiaSettings(MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1)),
    playerCount: Int = 6,
) {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
    val definition = MafiaDefinition(json)
    val config = SessionConfig(
        sessionId = SessionId("doctor-regression"),
        caseId = CaseId("default"),
        modeId = MafiaIds.ClassicModeId,
        players = (0 until playerCount).map { Player(PlayerId("doctor-test-$it"), "Player $it", it) },
        randomSeed = 43L,
    )
    val context = DefaultReducerContext(FakeClock(Instant.fromEpochSeconds(0)), RandomSource.seeded(43L))
    var state = definition.createInitialState(config)
        private set

    val doctor: PlayerId
        get() = state.hostOnly.fullRoleMap.entries.single { it.value == Role.Doctor }.key

    val civilianTargets: List<PlayerId>
        get() = state.hostOnly.fullRoleMap.filterValues { it == Role.Civilian }.keys.toList()

    fun apply(action: MafiaAction): MafiaState {
        state = MafiaReducer.reduce(state, action, context).newState
        return state
    }

    fun start(): MafiaState = apply(MafiaAction.ConfigureAndStart(settings)).also {
        assertEquals(MafiaPhase.RoleAssignment, it.phase)
    }

    fun firstNight(): MafiaState {
        start()
        config.players.forEach { apply(MafiaAction.AcknowledgeRoleViewed(it.id)) }
        return apply(MafiaAction.AdvanceFromRoleAssignment).also {
            assertEquals(MafiaPhase.Night(1), it.phase)
        }
    }

    fun resolveNight(protection: PlayerId?, kill: PlayerId? = null): MafiaState {
        val day = (state.phase as MafiaPhase.Night).day
        aliveIds().forEach { id ->
            val private = state.privatePerPlayer.getValue(id)
            if (!private.nightChoiceSubmitted) {
                apply(
                    when (private.role) {
                        Role.Doctor -> MafiaAction.SubmitDoctorProtect(id, protection)
                        Role.Mafia -> MafiaAction.SubmitMafiaKillVote(id, kill)
                        Role.Detective -> MafiaAction.SubmitDetectiveInspect(id, null)
                        Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(id, null)
                    },
                )
            }
        }
        return apply(MafiaAction.ResolveNight).also {
            assertEquals(MafiaPhase.NightAnnouncement(day), it.phase)
        }
    }

    fun openDiscussion(): MafiaState {
        aliveIds().forEach { apply(MafiaAction.AcknowledgeNightAnnouncement(it)) }
        return apply(MafiaAction.OpenDiscussion).also {
            assertEquals(MafiaPhase.Discussion(it.public.day), it.phase)
        }
    }

    fun openVoting(): MafiaState {
        openDiscussion()
        return apply(MafiaAction.OpenVote).also {
            assertEquals(MafiaPhase.Voting(it.public.day), it.phase)
        }
    }

    fun finishVoting(eliminate: PlayerId? = null): MafiaState {
        requireNotNull(state.public.activeVote).ballot.forEach { voter ->
            apply(
                if (eliminate == null || eliminate == voter) MafiaAction.AbstainVote(voter)
                else MafiaAction.CastVote(voter, eliminate),
            )
        }
        return apply(MafiaAction.CloseVote)
    }

    fun nextNight(): MafiaState {
        if (state.phase is MafiaPhase.NightAnnouncement) openVoting()
        if (state.phase is MafiaPhase.Voting) finishVoting()
        val day = (state.phase as MafiaPhase.VoteAnnouncement).day
        aliveIds().forEach { apply(MafiaAction.AcknowledgeVoteAnnouncement(it)) }
        return apply(MafiaAction.AdvanceFromVoteAnnouncement).also {
            assertEquals(MafiaPhase.Night(day + 1), it.phase)
        }
    }

    private fun aliveIds(): List<PlayerId> = state.public.roster.filter { it.alive }.map { it.playerId }
}
