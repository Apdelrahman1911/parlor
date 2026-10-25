package com.parlor.games.mafia.flow

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
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.reducer.MafiaReducer
import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaSettingsPresets
import com.parlor.games.mafia.domain.settings.TieBehavior
import com.parlor.games.mafia.domain.state.MafiaObservableStateValidator
import com.parlor.games.mafia.domain.state.MafiaPeerSnapshotValidator
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.snapshot.isValidRecoveryState
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Seeded fault injection through real reducers, with recovery and recipient checks after every command. */
class MafiaSeededStateMachineTest {
    private val definition = MafiaDefinition(Json { encodeDefaults = true })
    private val context = DefaultReducerContext(
        FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
        RandomSource.seeded(1),
    )

    @Test
    fun mixedNightChoicesVotesAndReconnectsPreserveBoundaryInvariants() {
        for (count in 5..16) {
            repeat(12) { seed ->
                drive(count, seed)
            }
        }
    }

    private fun drive(count: Int, seed: Int) {
        val random = Random(seed * 1009 + count)
        val settings = MafiaSettingsPresets.forPlayerCount(count).copy(
            doctorCanSelfHeal = seed % 2 == 0,
            doctorCanProtectSamePlayerConsecutively = seed % 3 == 0,
            detectiveCanInspectSelf = seed % 2 == 0,
            allowSelfVote = seed % 3 == 0,
            mafiaCanTargetMafia = seed % 2 == 0,
            revealRoleOnDeath = seed % 2 == 0,
            voteTieBehavior = TieBehavior.entries[seed % TieBehavior.entries.size],
            mafiaKillTieBehavior = MafiaKillTie.entries[seed % MafiaKillTie.entries.size],
            maxRevotes = seed % 3,
        )
        var state = definition.createInitialState(SessionConfig(
            sessionId = SessionId("model-$count-$seed"),
            caseId = CaseId("default"),
            modeId = MafiaIds.ClassicModeId,
            players = List(count) { Player(PlayerId("p$it"), "Player $it", it) },
            randomSeed = seed.toLong(),
        ))
        state = checked(state, MafiaAction.ConfigureAndStart(settings), count, seed)
        var commands = 0
        while (state.phase != MafiaPhase.PostGame && commands < 3000) {
            if (random.nextInt(20) == 0) {
                val id = living(state).random(random)
                val before = state
                state = checked(state, MafiaAction.MarkPlayerDisconnected(id), count, seed)
                state = checked(state, MafiaAction.MarkPlayerReconnected(id), count, seed)
                assertEquals(before, state, "Reconnect changed game state: count=$count seed=$seed")
            }
            state = checked(state, nextAction(state, random), count, seed)
            commands++
        }
        assertEquals(MafiaPhase.PostGame, state.phase, "No progress: count=$count seed=$seed")
        assertTrue(state.public.winner != null, "Natural completion must have a winner")
    }

    private fun checked(state: MafiaState, action: MafiaAction, count: Int, seed: Int): MafiaState {
        val result = MafiaReducer.reduce(state, action, context).newState
        val trace = "count=$count seed=$seed phase=${state.phase} action=$action"
        assertTrue(MafiaObservableStateValidator.isValid(result), "Invalid canonical shape: $trace")
        if (result.public.disconnectedPlayers.isEmpty()) {
            assertTrue(result.isValidRecoveryState(), "Unrecoverable connected game state: $trace")
        } else {
            // Local restore deliberately rejects LAN connection markers: loading
            // them in pass-and-play would strand the user behind a rejoin overlay.
            assertFalse(result.isValidRecoveryState(), "LAN state must not restore as local play: $trace")
        }
        val public = MafiaProjectionPolicy.toPublic(result).state
        assertTrue(public.privatePerPlayer.isEmpty(), trace)
        assertTrue(public.hostOnly.fullRoleMap.isEmpty(), trace)
        for (player in result.players) {
            val projection = MafiaProjectionPolicy.toPlayer(result, player.id).state
            assertTrue(projection.privatePerPlayer.keys.all { it == player.id }, trace)
            assertTrue(
                MafiaPeerSnapshotValidator.isValid(public, projection.privatePerPlayer[player.id], player.id),
                "Rejected legal recipient projection: $trace recipient=${player.id}",
            )
        }
        assertEquals(result, MafiaReducer.reduce(result, action, context).newState, "Duplicate changed state: $trace")
        return result
    }

    private fun nextAction(state: MafiaState, random: Random): MafiaAction = when (state.phase) {
        MafiaPhase.Setup -> MafiaAction.StartGame
        MafiaPhase.RoleAssignment -> state.privatePerPlayer.entries
            .filter { !it.value.roleAcknowledged }.shuffled(random).firstOrNull()
            ?.let { MafiaAction.AcknowledgeRoleViewed(it.key) } ?: MafiaAction.AdvanceFromRoleAssignment
        is MafiaPhase.Night -> nightAction(state, random)
        is MafiaPhase.NightAnnouncement -> living(state).shuffled(random)
            .firstOrNull { !state.privatePerPlayer.getValue(it).nightAcknowledged }
            ?.let { MafiaAction.AcknowledgeNightAnnouncement(it) } ?: MafiaAction.OpenDiscussion
        is MafiaPhase.Discussion -> MafiaAction.OpenVote
        is MafiaPhase.Voting -> voteAction(state, random)
        is MafiaPhase.VoteAnnouncement -> living(state).shuffled(random)
            .firstOrNull { !state.privatePerPlayer.getValue(it).voteAcknowledged }
            ?.let { MafiaAction.AcknowledgeVoteAnnouncement(it) } ?: MafiaAction.AdvanceFromVoteAnnouncement
        MafiaPhase.PostGame -> MafiaAction.EndGame
    }

    private fun nightAction(state: MafiaState, random: Random): MafiaAction {
        val alive = living(state)
        val inspector = alive.firstOrNull {
            val private = state.privatePerPlayer.getValue(it)
            private.pendingDetectiveResult != null && !private.detectiveResultAcknowledged
        }
        if (inspector != null) return MafiaAction.AcknowledgeDetectiveResult(inspector)
        val actor = alive.shuffled(random).firstOrNull { !state.privatePerPlayer.getValue(it).nightChoiceSubmitted }
            ?: return MafiaAction.ResolveNight
        val private = state.privatePerPlayer.getValue(actor)
        val settings = state.public.settings
        val targets = alive.filter { target ->
            when (private.role) {
                Role.Mafia -> target != actor &&
                    (settings.mafiaCanTargetMafia || state.hostOnly.fullRoleMap[target] != Role.Mafia)
                Role.Doctor -> (target != actor || settings.doctorCanSelfHeal) &&
                    (settings.doctorCanProtectSamePlayerConsecutively || target != private.previousDoctorProtect)
                Role.Detective -> target != actor || settings.detectiveCanInspectSelf
                Role.Civilian -> target != actor
            }
        }
        val target = (targets + listOf(null)).random(random)
        return when (private.role) {
            Role.Mafia -> MafiaAction.SubmitMafiaKillVote(actor, target)
            Role.Doctor -> MafiaAction.SubmitDoctorProtect(actor, target)
            Role.Detective -> MafiaAction.SubmitDetectiveInspect(actor, target)
            Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(actor, target)
        }
    }

    private fun voteAction(state: MafiaState, random: Random): MafiaAction {
        val vote = requireNotNull(state.public.activeVote)
        val actor = vote.ballot.shuffled(random).firstOrNull { it !in vote.castSoFar && it !in vote.abstained }
            ?: return MafiaAction.CloseVote
        val candidates = vote.candidates.filter { it != actor || state.public.settings.allowSelfVote }
        val target = (candidates + listOf(null)).random(random)
        return if (target == null) MafiaAction.AbstainVote(actor) else MafiaAction.CastVote(actor, target)
    }

    private fun living(state: MafiaState): List<PlayerId> =
        state.public.roster.filter { it.alive }.map { it.playerId }
}
