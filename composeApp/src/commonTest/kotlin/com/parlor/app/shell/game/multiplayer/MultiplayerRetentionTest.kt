package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.SendTarget
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.RetainedValueResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerRetentionTest {
    @Test
    fun all_three_games_reuse_frozen_settings_start_checkpoints_and_authority_after_ui_recreation() = runTest {
        verifyRetention(DominoGameShellBinding(DominoDefinition()))
        verifyRetention(GhamzaGameShellBinding(GhamzaDefinition()))
        verifyRetention(WordImpostorGameShellBinding(WordImpostorDefinition()))
    }

    private suspend fun <S : GameState, A : GameAction, E : GameEvent> TestScope.verifyRetention(
        spec: MultiplayerGameSpec<S, A, E>,
    ) {
        val players = gamePlayers(3)
        val bus = InMemoryRoomBus()
        val inboxes = players.drop(1).associate { it.id to Channel<HostMessage>(128) }
        val hostRoom = RecordingGameHostRoom(bus, players) { target, message ->
            when (target) {
                SendTarget.Broadcast -> inboxes.values.forEach { assertTrue(it.trySend(message).isSuccess) }
                is SendTarget.Direct -> assertTrue(inboxes.getValue(target.playerId).trySend(message).isSuccess)
            }
        }
        val owner = ProcessMultiplayerSessionOwner(backgroundScope)
        val route = MultiplayerSessionRoute.host(spec.definition.id, "Host")
        val session = assertIs<Result.Success<ProcessMultiplayerSession>>(owner.acquire(route, 42L) { Result.Success(hostRoom) }).data
        val peerOwners = mutableMapOf<PlayerId, ProcessMultiplayerSessionOwner>()
        val peerSessions = mutableMapOf<PlayerId, ProcessMultiplayerSession>()
        val checkpoints = mutableMapOf<PlayerId, GamePeerStartCheckpoint>()
        val peers = mutableMapOf<PlayerId, PeerGameRuntime<S, A, E>>()
        try {
            val setup = GameHostSetupCheckpoint(spec.kind("setup"), spec.defaultCaseId)
            val setupLookup = session.getOrCreateCheckpoint(setup.checkpointKind) { setup }
            assertSame(setup, assertIs<RetainedValueResult.Ready<*>>(setupLookup).value)
            setup.freeze(session)
            setup.freeze(session)
            runCurrent()
            assertTrue(setup.state.value.frozen)
            assertEquals(1, hostRoom.closeAdmissionsCalls)
            assertEquals(players.drop(1).map { it.id }, session.frozenRoster.value?.map { it.playerId })
            assertSame(setup, assertIs<RetainedValueResult.Ready<*>>(
                session.getOrCreateCheckpoint(setup.checkpointKind) { error("Recreation must not reset settings") },
            ).value)

            for (player in players.drop(1)) {
                bus.registerPeer(player.id)
                val room = RecordingGamePeerRoom(
                    InMemoryPeerRoom(bus, player.id, player.displayName, gameHostId), inboxes.getValue(player.id).receiveAsFlow(),
                )
                val peerOwner = ProcessMultiplayerSessionOwner(backgroundScope)
                val peerRoute = MultiplayerSessionRoute.peer(spec.definition.id, player.displayName, "A23456")
                val peerSession = assertIs<Result.Success<ProcessMultiplayerSession>>(
                    peerOwner.acquire(peerRoute) { Result.Success(room) },
                ).data
                val checkpoint = GamePeerStartCheckpoint(spec.kind("start"))
                peerSession.getOrCreateCheckpoint(checkpoint.checkpointKind) { checkpoint }
                peerOwners[player.id] = peerOwner
                peerSessions[player.id] = peerSession
                checkpoints[player.id] = checkpoint
                checkpoint.start(spec, peerSession, peerOwner)
            }
            val runtime = HostedGameRuntime(spec, FakeClock(Instant.fromEpochSeconds(0)), players,
                checkNotNull(session.hostSeed), setup.state.value.caseId, hostRoom, session.scope)
            session.getOrCreateRuntime(spec.kind("host")) { runtime }
            runCurrent()
            assertEquals(HostStartGateState.Started, runtime.startGate.value)
            for ((id, peerSession) in peerSessions) {
                val started = assertIs<GameStartState.Started>(checkpoints.getValue(id).state.value).value
                val peer = PeerGameRuntime(spec, started.offer, started.protocol, peerSession.room, peerSession.scope)
                peers[id] = peer
                peerSession.getOrCreateRuntime(spec.kind("peer")) { peer }
            }
            runCurrent()
            assertSame(session, assertIs<Result.Success<ProcessMultiplayerSession>>(owner.acquire(route, 99L) {
                error("UI recreation must not reopen the room")
            }).data)
            assertEquals(42L, session.hostSeed)
            assertSame(runtime, assertIs<RetainedValueResult.Ready<*>>(
                session.getOrCreateRuntime(spec.kind("host")) { error("No second reducer") },
            ).value)
            assertIs<RetainedValueResult.KindConflict>(session.getOrCreateRuntime("wrong-game/host/v1") {
                error("An incompatible runtime cannot be installed")
            })
            peers.forEach { (id, peer) ->
                assertTrue(peer.bridge.hasAuthoritativeSnapshot.value)
                assertEquals(runtime.session.privateStateFor(id).value, peer.session.privateStateFor(id).value)
                val peerSession = peerSessions.getValue(id)
                assertSame(peer, assertIs<RetainedValueResult.Ready<*>>(
                    peerSession.getOrCreateRuntime(spec.kind("peer")) { error("No second inbox collector") },
                ).value)
            }
            assertIs<Result.Success<Unit>>(owner.leaveRoute(route, SessionEndReason.HostLeft))
            runCurrent()
            peers.values.forEach { assertEquals(SessionEndReason.HostLeft, it.bridge.terminalReason.value) }
        } finally {
            owner.leaveRoute(route, SessionEndReason.Cancelled)
            peerSessions.forEach { (id, peer) -> peerOwners.getValue(id).leaveRoute(peer.route, SessionEndReason.Cancelled) }
            inboxes.values.forEach { it.close() }
        }
    }
}
