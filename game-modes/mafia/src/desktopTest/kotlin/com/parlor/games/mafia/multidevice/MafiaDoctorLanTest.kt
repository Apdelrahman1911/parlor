package com.parlor.games.mafia.multidevice

import com.parlor.core.result.Result
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.testing.MafiaDoctorFixture
import com.parlor.games.mafia.ui.flow.multidevice.MafiaHostRoomBridge
import com.parlor.games.mafia.ui.flow.multidevice.MafiaPeerRoomBridge
import com.parlor.games.mafia.ui.screens.night.isDoctorTargetEligible
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.testing.InMemoryHostRoom
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.SubmissionReceipt
import com.parlor.session.multidevice.PeerCommandProgress
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Production session/bridges on an in-memory authenticated room; not physical P2pKit evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class MafiaDoctorLanTest {
    private val base = MafiaSettings(MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1))

    @Test
    fun only_host_can_commit_setup_selection_and_no_one_can_change_it_after_start(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.start() // Determine a remote Doctor from the same deterministic assignment.
            val setup = game.definition.createInitialState(game.config)
            val lan = LanFixture(game, backgroundScope, setup)
            try {
                runCurrent()
                peerCommand(lan, MafiaAction.ConfigureAndStart(game.settings), CommandStatus.Unauthorized)
                assertEquals(MafiaPhase.Setup, lan.session.currentState().phase)
                val committed = assertIs<Result.Success<SubmissionReceipt>>(
                    lan.hostBridge.submitHostAction(MafiaAction.ConfigureAndStart(game.settings)),
                )
                assertTrue(committed.data.stateChanged)
                runCurrent()
                assertEquals(MafiaPhase.RoleAssignment, lan.session.currentState().phase)
                assertEquals(game.settings, lan.session.currentState().public.settings)
                lan.peers.forEach { assertEquals(game.settings, it.controller.publicState.value.state.public.settings) }

                val changed = game.settings.copy(doctorCanProtectSamePlayerConsecutively = !enabled)
                peerCommand(lan, MafiaAction.ApplySettings(changed), CommandStatus.Unauthorized)
                val ignored = assertIs<Result.Success<SubmissionReceipt>>(
                    lan.hostBridge.submitHostAction(MafiaAction.ApplySettings(changed)),
                )
                assertFalse(ignored.data.stateChanged)
                assertEquals(game.settings, lan.session.currentState().public.settings)
            } finally {
                lan.close()
            }
        }
    }

    @Test
    fun rejoin_preserves_doctor_restriction_setting_and_first_submission_without_host_secrets(): Unit = runTest {
        for (enabled in listOf(false, true)) {
            val game = MafiaDoctorFixture(base.copy(doctorCanProtectSamePlayerConsecutively = enabled))
            game.firstNight()
            val (first, second) = game.civilianTargets
            game.resolveNight(first)
            game.nextNight()
            val lan = LanFixture(game, backgroundScope, game.state)
            try {
                runCurrent()
                // This fixture bypasses the lobby/start handshake. With no
                // accepted start id, peers only have public placeholders until
                // an actual reconciliation request obtains their own slice.
                assertTrue(lan.peers.all { peer ->
                    peer.controller.privateStateFor(peer.selfPlayerId).value.state.privatePerPlayer.isEmpty()
                })
                lan.bus.emitHostRestored()
                runCurrent()
                assertTrue(lan.doctorPeer.hasAuthoritativeSnapshot.value)
                assertPrivateProjection(lan, first)
                reconnectDoctor(lan)
                val projected = lan.doctorPeer.controller.privateStateFor(game.doctor).value.state
                assertEquals(
                    enabled,
                    isDoctorTargetEligible(
                        first, game.doctor,
                        projected.privatePerPlayer.getValue(game.doctor).previousDoctorProtect,
                        projected.public.settings,
                    ),
                )
                peerCommand(
                    lan,
                    MafiaAction.SubmitDoctorProtect(game.doctor, first),
                    if (enabled) CommandStatus.Applied else CommandStatus.InvalidAction,
                )
                if (!enabled) peerCommand(lan, MafiaAction.SubmitDoctorProtect(game.doctor, second), CommandStatus.Applied)
                val selected = if (enabled) first else second
                reconnectDoctor(lan)
                assertEquals(selected, lan.session.currentState().privatePerPlayer.getValue(game.doctor).pendingNightChoice)
                peerCommand(lan, MafiaAction.SubmitDoctorProtect(game.doctor, null), CommandStatus.InvalidAction)
                assertPrivateProjection(lan, first)

                if (enabled) {
                    repeat(3) { index ->
                        completeNightAndDay(lan)
                        assertEquals(first, lan.session.currentState().hostOnly.nightLog.last().doctorProtect)
                        assertPrivateProjection(lan, first)
                        if (index < 2) {
                            peerCommand(lan, MafiaAction.SubmitDoctorProtect(game.doctor, first), CommandStatus.Applied)
                        }
                    }
                    assertEquals(4, lan.session.currentState().hostOnly.nightLog.size)
                }
                assertEquals(0, lan.bus.droppedHostMessageCount)
                assertEquals(0, lan.bus.droppedPeerMessageCount)
            } finally {
                lan.close()
            }
        }
    }

    private suspend fun TestScope.peerCommand(lan: LanFixture, action: MafiaAction, status: CommandStatus) {
        val canonicalBefore = lan.session.currentState()
        val peerBefore = lan.doctorPeer.controller.privateStateFor(lan.game.doctor).value.state
        val sent = assertIs<Result.Success<SubmissionReceipt>>(lan.doctorPeer.controller.submit(action))
        assertTrue(sent.data.awaitingAuthority)
        assertFalse(sent.data.stateChanged)
        // Standard test dispatch has not run the host's collector yet: no speculative reducer on the peer.
        assertEquals(canonicalBefore, lan.session.currentState())
        assertEquals(peerBefore, lan.doctorPeer.controller.privateStateFor(lan.game.doctor).value.state)
        runCurrent()
        val outcome = assertIs<PeerCommandProgress.Resolved>(lan.doctorPeer.commandProgress.value).outcome
        assertEquals(status, outcome.status)
        lan.doctorPeer.acknowledgeCommandOutcome(outcome.commandId)
    }

    private suspend fun TestScope.reconnectDoctor(lan: LanFixture) {
        lan.bus.emitPeerLeft(lan.game.doctor, "Doctor")
        runCurrent()
        assertTrue(lan.game.doctor in lan.session.currentState().public.disconnectedPlayers)
        advanceTimeBy(100)
        lan.bus.emitPeerReconnected(lan.game.doctor, "Doctor")
        runCurrent()
        assertFalse(lan.game.doctor in lan.session.currentState().public.disconnectedPlayers)
        assertEquals(lan.game.settings, lan.doctorPeer.controller.publicState.value.state.public.settings)
    }

    private suspend fun TestScope.completeNightAndDay(lan: LanFixture) {
        suspend fun host(action: MafiaAction) {
            assertIs<Result.Success<SubmissionReceipt>>(lan.hostBridge.submitHostAction(action))
            runCurrent()
        }
        val before = lan.session.currentState()
        before.players.forEach { player ->
            val private = before.privatePerPlayer.getValue(player.id)
            if (!private.nightChoiceSubmitted) {
                host(
                    when (private.role) {
                        Role.Mafia -> MafiaAction.SubmitMafiaKillVote(player.id, null)
                        Role.Doctor -> error("The Doctor must submit through its peer bridge")
                        Role.Detective -> MafiaAction.SubmitDetectiveInspect(player.id, null)
                        Role.Civilian -> MafiaAction.SubmitCivilianSuspicion(player.id, null)
                    },
                )
            }
        }
        host(MafiaAction.ResolveNight)
        assertEquals(MafiaPhase.NightAnnouncement(before.public.day), lan.session.currentState().phase)
        before.players.forEach { host(MafiaAction.AcknowledgeNightAnnouncement(it.id)) }
        host(MafiaAction.OpenDiscussion)
        host(MafiaAction.OpenVote)
        before.players.forEach { host(MafiaAction.AbstainVote(it.id)) }
        host(MafiaAction.CloseVote)
        before.players.forEach { host(MafiaAction.AcknowledgeVoteAnnouncement(it.id)) }
        host(MafiaAction.AdvanceFromVoteAnnouncement)
        assertEquals(MafiaPhase.Night(before.public.day + 1), lan.session.currentState().phase)
    }

    private fun assertPrivateProjection(lan: LanFixture, previous: com.parlor.core.ids.PlayerId) {
        lan.peers.forEach { peer ->
            val public = peer.controller.publicState.value.state
            val own = peer.controller.privateStateFor(peer.selfPlayerId).value.state
            assertTrue(public.privatePerPlayer.isEmpty())
            assertEquals(setOf(peer.selfPlayerId), own.privatePerPlayer.keys)
            for (projected in listOf(public, own)) {
                assertTrue(projected.hostOnly.nightLog.isEmpty())
                assertTrue(projected.hostOnly.voteLog.isEmpty())
                assertTrue(projected.hostOnly.fullRoleMap.isEmpty())
                assertEquals(0L, projected.hostOnly.randomSeed)
                assertEquals(lan.game.settings, projected.public.settings)
            }
            assertEquals(
                if (peer.selfPlayerId == lan.game.doctor) previous else null,
                own.privatePerPlayer.getValue(peer.selfPlayerId).previousDoctorProtect,
            )
        }
    }

    private class LanFixture(val game: MafiaDoctorFixture, scope: CoroutineScope, initial: MafiaState) {
        private val host = game.config.players.first { it.id != game.doctor }.id
        val bus: InMemoryRoomBus = InMemoryRoomBus().also { bus -> game.config.players.forEach { bus.registerPeer(it.id) } }
        val session = PassAndPlaySessionController(
            definition = game.definition,
            config = game.config,
            reducerContext = game.context,
            scope = scope,
            restoredState = initial,
        )
        val hostBridge = MafiaHostRoomBridge(
            controller = session,
            room = InMemoryHostRoom(bus, host, "Host"),
            players = game.config.players,
            scope = scope,
            json = game.json,
            rejoinGraceMs = 1_000L,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        val peers = game.config.players.filter { it.id != host }.map { player ->
            MafiaPeerRoomBridge(
                room = InMemoryPeerRoom(bus, player.id, player.displayName, host),
                selfPlayerId = player.id,
                initialPublic = session.publicState.value.state,
                scope = scope,
                protocol = hostBridge.protocol,
                json = game.json,
            )
        }
        val doctorPeer: MafiaPeerRoomBridge = peers.single { it.selfPlayerId == game.doctor }

        suspend fun close() {
            peers.forEach { it.close() }
            hostBridge.close()
            session.close()
        }
    }
}
