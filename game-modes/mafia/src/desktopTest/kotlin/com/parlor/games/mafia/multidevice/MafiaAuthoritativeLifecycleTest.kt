package com.parlor.games.mafia.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.ui.flow.multidevice.MafiaHostRoomBridge
import com.parlor.games.mafia.ui.flow.multidevice.MafiaPeerRoomBridge
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MafiaAuthoritativeLifecycleTest {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }
    private val host = PlayerId("host")
    private val alice = PlayerId("alice")
    private val players = listOf(
        Player(host, "Host", 0),
        Player(alice, "Alice", 1),
        Player(PlayerId("bob"), "Bob", 2),
        Player(PlayerId("carol"), "Carol", 3),
        Player(PlayerId("dave"), "Dave", 4),
    )

    @Test
    fun close_waits_for_host_and_peer_room_event_collectors() = runTest {
        val fixture = fixture(
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            startGame = false,
        )
        assertThat(fixture.bus.peerEventSubscriberCount > 0).isTrue()

        fixture.close()

        assertThat(fixture.bus.peerEventSubscriberCount).isEqualTo(0)
    }

    @Test
    fun disconnect_pauses_commands_and_reconnect_cancels_expiry_then_resyncs() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))
        val snapshotsBefore = fixture.hostRoom.sent.count {
            it.first == SendTarget.Direct(alice) && it.second is HostMessage.PlayerSnapshot
        }

        fixture.bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        assertThat(
            alice in fixture.session.hostState.value.state.public.disconnectedPlayers,
        ).isTrue()

        // Orchestration pause rejects otherwise valid gameplay commands.
        fixture.peerBridge.controller.submit(MafiaAction.AcknowledgeRoleViewed(alice))
        runCurrent()
        assertThat(
            fixture.session.hostState.value.state.privatePerPlayer[alice]?.roleAcknowledged == true,
        ).isFalse()

        advanceTimeBy(100)
        fixture.bus.emitPeerReconnected(alice, "Alice")
        runCurrent()
        advanceTimeBy(200)
        runCurrent()

        val restored = fixture.session.hostState.value.state
        assertThat(alice in restored.public.disconnectedPlayers).isFalse()
        assertThat(alice in restored.public.droppedPlayers).isFalse()
        assertThat(restored.phase is MafiaPhase.RoleAssignment).isTrue()
        val snapshotsAfter = fixture.hostRoom.sent.count {
            it.first == SendTarget.Direct(alice) && it.second is HostMessage.PlayerSnapshot
        }
        assertThat(snapshotsAfter > snapshotsBefore).isTrue()

        // The same self-actor command is accepted after the seat is restored.
        fixture.peerBridge.controller.submit(MafiaAction.AcknowledgeRoleViewed(alice))
        runCurrent()
        assertThat(
            fixture.session.hostState.value.state.privatePerPlayer[alice]?.roleAcknowledged,
        ).isEqualTo(true)

        fixture.close()
    }

    @Test
    fun reconnect_keeps_seat_disconnected_until_start_commit_is_acknowledged() = runTest {
        val fixture = fixture(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            startGame = false,
            requireStartHandshake = true,
        )
        val startId = completeStartHandshake(fixture)
        assertThat(fixture.bridge.submitHostAction(MafiaAction.StartGame))
            .isInstanceOf(Result.Success::class)
        fixture.hostRoom.sent.clear()

        fixture.bus.emitPeerLeft(alice, "Alice")
        fixture.bus.emitPeerReconnected(alice, "Alice")
        runCurrent()
        assertThat(alice in fixture.session.hostState.value.state.public.disconnectedPlayers)
            .isTrue()
        assertThat(
            fixture.hostRoom.sent.any {
                it.first == SendTarget.Direct(alice) &&
                    it.second is HostMessage.SessionStarting
            },
        ).isTrue()

        fixture.bus.fromPeer(
            PeerMessage.SessionStartReady(
                peerHeader(fixture.bridge, "rejoin-ready"),
                alice,
                startId,
            ),
        )
        runCurrent()
        assertThat(alice in fixture.session.hostState.value.state.public.disconnectedPlayers)
            .isTrue()
        assertThat(
            fixture.hostRoom.sent.any {
                it.first == SendTarget.Direct(alice) &&
                    it.second is HostMessage.SessionStartCommitted
            },
        ).isTrue()

        fixture.bus.fromPeer(
            PeerMessage.SessionStartCommitAck(
                peerHeader(fixture.bridge, "rejoin-commit-ack"),
                alice,
                startId,
            ),
        )
        runCurrent()
        assertThat(alice in fixture.session.hostState.value.state.public.disconnectedPlayers)
            .isFalse()
        assertThat(alice in fixture.session.hostState.value.state.public.droppedPlayers).isFalse()
        fixture.close()
    }

    @Test
    fun reconnect_handshake_failure_leaves_grace_deadline_authoritative() = runTest {
        val fixture = fixture(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            startGame = false,
            requireStartHandshake = true,
        )
        val startId = completeStartHandshake(fixture)
        assertThat(fixture.bridge.submitHostAction(MafiaAction.StartGame))
            .isInstanceOf(Result.Success::class)

        fixture.bus.emitPeerLeft(alice, "Alice")
        fixture.bus.emitPeerReconnected(alice, "Alice")
        runCurrent()
        advanceTimeBy(201L)
        runCurrent()

        val expired = fixture.session.hostState.value.state
        assertThat(expired.phase is MafiaPhase.PostGame).isTrue()
        assertThat(alice in expired.public.droppedPlayers).isTrue()
        assertThat(alice in expired.public.disconnectedPlayers).isFalse()

        // Frames delayed beyond the grace boundary cannot revive the seat.
        fixture.bus.fromPeer(
            PeerMessage.SessionStartReady(
                peerHeader(fixture.bridge, "late-rejoin-ready"),
                alice,
                startId,
            ),
        )
        fixture.bus.fromPeer(
            PeerMessage.SessionStartCommitAck(
                peerHeader(fixture.bridge, "late-rejoin-ack"),
                alice,
                startId,
            ),
        )
        runCurrent()
        assertThat(fixture.session.hostState.value.state).isEqualTo(expired)
        fixture.close()
    }

    @Test
    fun rejoin_grace_expiry_deterministically_finishes_active_game() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))

        fixture.bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        advanceTimeBy(201)
        runCurrent()

        val state = fixture.session.hostState.value.state
        assertThat(state.phase is MafiaPhase.PostGame).isTrue()
        assertThat(alice in state.public.droppedPlayers).isTrue()
        assertThat(alice in state.public.disconnectedPlayers).isFalse()
        fixture.close()
    }

    @Test
    fun host_can_continue_without_before_expiry_and_cancels_the_grace_job() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))

        fixture.bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        assertThat(fixture.bridge.continueWithout(alice)).isTrue()
        runCurrent()

        val afterDecision = fixture.session.hostState.value.state
        assertThat(alice in afterDecision.public.disconnectedPlayers).isFalse()
        assertThat(alice in afterDecision.public.droppedPlayers).isTrue()
        assertThat(fixture.hostRoom.retiredMembers).isEqualTo(listOf(alice))

        advanceTimeBy(201)
        runCurrent()
        assertThat(fixture.session.hostState.value.state).isEqualTo(afterDecision)
        assertThat(fixture.bridge.continueWithout(alice)).isFalse()
        fixture.close()
    }

    @Test
    fun failed_transport_retirement_keeps_the_player_disconnected_and_retryable() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))
        fixture.hostRoom.retirementError =
            NetError.TransportFailure("injected retirement failure")
        fixture.bus.emitPeerLeft(alice, "Alice")
        runCurrent()

        assertThat(fixture.bridge.continueWithout(alice)).isFalse()

        val state = fixture.session.hostState.value.state
        assertThat(alice in state.public.disconnectedPlayers).isTrue()
        assertThat(alice in state.public.droppedPlayers).isFalse()
        assertThat(fixture.hostRoom.retiredMembers).isEqualTo(emptyList())
        fixture.close()
    }

    @Test
    fun suspended_room_rejects_host_gameplay_without_mutating_state() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))
        val before = fixture.session.hostState.value.state
        fixture.hostRoom.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)

        val result = fixture.bridge.submitHostAction(MafiaAction.AcknowledgeRoleViewed(host))

        assertThat(result).isEqualTo(Result.Failure(SubmitError.SessionSuspended))
        assertThat(fixture.session.hostState.value.state).isEqualTo(before)
        fixture.close()
    }

    @Test
    fun one_departure_ending_game_clears_other_concurrent_disconnects() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))
        val bob = players[2].id

        fixture.bus.emitPeerLeft(alice, "Alice")
        fixture.bus.emitPeerLeft(bob, "Bob")
        runCurrent()
        assertThat(
            fixture.session.hostState.value.state.public.disconnectedPlayers,
        ).isEqualTo(setOf(alice, bob))

        assertThat(fixture.bridge.continueWithout(alice)).isTrue()
        runCurrent()
        val terminal = fixture.session.hostState.value.state
        assertThat(terminal.phase).isEqualTo(MafiaPhase.PostGame)
        assertThat(terminal.public.disconnectedPlayers.isEmpty()).isTrue()

        advanceTimeBy(201L)
        runCurrent()
        assertThat(fixture.session.hostState.value.state).isEqualTo(terminal)
        fixture.close()
    }

    @Test
    fun stamped_start_and_explicit_host_leave_are_terminal_for_peer() = runTest {
        val fixture = fixture(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            startGame = false,
            requireStartHandshake = true,
        )
        var peerEnded = false
        val collector = fixture.scope.launch {
            fixture.peerBridge.hostDisconnected.collect { peerEnded = true }
        }

        val announcing = async {
            fixture.bridge.announceStart("default", MafiaIds.ClassicModeId.raw)
        }
        runCurrent()
        val start = fixture.hostRoom.sent
            .mapNotNull { it.second as? HostMessage.SessionStarting }
            .first()
        players.drop(1).forEach { player ->
            fixture.bus.fromPeer(
                PeerMessage.SessionStartReady(
                    peerHeader(fixture.bridge, "ready-${player.seat}"),
                    player.id,
                    start.startId,
                ),
            )
        }
        runCurrent()
        players.drop(1).forEach { player ->
            fixture.bus.fromPeer(
                PeerMessage.SessionStartCommitAck(
                    peerHeader(fixture.bridge, "commit-${player.seat}"),
                    player.id,
                    start.startId,
                ),
            )
        }
        runCurrent()
        assertThat(announcing.await()).isInstanceOf(Result.Success::class)
        assertThat(start.header.sessionId).isEqualTo(fixture.bridge.protocol.sessionId)
        assertThat(start.header.gameId).isEqualTo(MafiaIds.GameId)
        assertThat(start.header.gameVersion).isEqualTo(MafiaHostRoomBridge.GAME_VERSION)

        fixture.bridge.terminate(SessionEndReason.HostLeft)
        fixture.bridge.close()
        runCurrent()
        assertThat(peerEnded).isTrue()

        collector.cancel()
        fixture.peerBridge.close()
    }

    @Test
    fun terminate_waits_for_authenticated_terminal_delivery_before_returning() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))
        val terminalGate = CompletableDeferred<Unit>()
        fixture.hostRoom.terminalSendGate = terminalGate

        val terminating = async { fixture.bridge.terminate(SessionEndReason.HostLeft) }
        runCurrent()

        assertThat(terminating.isCompleted).isFalse()
        assertThat(
            fixture.hostRoom.sent.count { it.second is HostMessage.SessionEnded },
        ).isEqualTo(players.drop(1).size)

        terminalGate.complete(Unit)
        terminating.await()
        assertThat(terminating.isCompleted).isTrue()
        fixture.close()
    }

    @Test
    fun production_topology_reconciliation_observes_disconnect_before_bridge_creation() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val initialMembers = players.drop(1).map { player ->
            RoomMember(
                playerId = player.id,
                displayName = player.displayName,
                connected = player.id != alice,
            )
        }
        val fixture = fixture(
            scope = scope,
            reconcileRoomTopology = true,
            initialMembers = initialMembers,
            startGame = false,
        )
        runCurrent()
        assertThat(alice in fixture.session.hostState.value.state.public.disconnectedPlayers).isTrue()

        fixture.hostRoom.setMembers(
            initialMembers.map { member ->
                if (member.playerId == alice) member.copy(connected = true) else member
            },
        )
        runCurrent()
        assertThat(alice in fixture.session.hostState.value.state.public.disconnectedPlayers).isFalse()
        fixture.close()
    }

    @Test
    fun post_game_disconnect_does_not_cover_terminal_results_with_reconnect_state() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))
        assertThat(fixture.bridge.submitHostAction(MafiaAction.EndGame))
            .isInstanceOf(Result.Success::class)
        val terminal = fixture.session.hostState.value.state
        assertThat(terminal.phase).isEqualTo(MafiaPhase.PostGame)

        fixture.bus.emitPeerLeft(alice, "Alice")
        runCurrent()

        assertThat(fixture.session.hostState.value.state).isEqualTo(terminal)
        fixture.close()
    }

    private suspend fun fixture(
        scope: TestScope,
        reconcileRoomTopology: Boolean = false,
        initialMembers: List<RoomMember> = emptyList(),
        startGame: Boolean = true,
        requireStartHandshake: Boolean = false,
    ): Fixture {
        val bus = InMemoryRoomBus()
        players.forEach { bus.registerPeer(it.id) }
        val session = PassAndPlaySessionController<MafiaState, MafiaAction, MafiaEvent>(
            definition = MafiaDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("mafia-lifecycle-host"),
                caseId = CaseId("default"),
                modeId = MafiaIds.ClassicModeId,
                players = players,
                randomSeed = 21L,
            ),
            reducerContext = DefaultReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(0)),
                random = RandomSource.seeded(21L),
            ),
            scope = scope,
        )
        val hostRoom = LifecycleHostRoom(bus, host)
        hostRoom.setMembers(initialMembers)
        val bridge = MafiaHostRoomBridge(
            controller = session,
            room = hostRoom,
            players = players,
            scope = scope,
            json = json,
            rejoinGraceMs = 200L,
            heartbeatIntervalMs = 0L,
            reconcileRoomTopology = reconcileRoomTopology,
            requireStartHandshake = requireStartHandshake,
        )
        if (startGame) bridge.submitHostAction(MafiaAction.StartGame)
        scope.runCurrent()
        val peerBridge = MafiaPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", host),
            selfPlayerId = alice,
            initialPublic = session.publicState.value.state,
            scope = scope,
            protocol = bridge.protocol,
            json = json,
            hostLostTimeoutMs = 200L,
        )
        scope.runCurrent()
        return Fixture(scope, bus, session, hostRoom, bridge, peerBridge)
    }

    private fun peerHeader(
        bridge: MafiaHostRoomBridge,
        label: String,
    ) = SessionEnvelopeHeader(
        protocol = bridge.protocol.protocol,
        sessionId = bridge.protocol.sessionId,
        gameId = bridge.protocol.gameId,
        gameVersion = bridge.protocol.gameVersion,
        messageId = "$label-012345678901234567890",
        sequence = 0L,
        connectionEpoch = bridge.protocol.connectionEpoch,
    )

    private suspend fun TestScope.completeStartHandshake(fixture: Fixture): String {
        val announcing = async {
            fixture.bridge.announceStart("default", MafiaIds.ClassicModeId.raw)
        }
        runCurrent()
        val startId = fixture.hostRoom.sent
            .mapNotNull { it.second as? HostMessage.SessionStarting }
            .first()
            .startId
        players.drop(1).forEach { player ->
            fixture.bus.fromPeer(
                PeerMessage.SessionStartReady(
                    peerHeader(fixture.bridge, "initial-ready-${player.seat}"),
                    player.id,
                    startId,
                ),
            )
        }
        runCurrent()
        assertThat(announcing.await()).isInstanceOf(Result.Success::class)
        players.drop(1).forEach { player ->
            fixture.bus.fromPeer(
                PeerMessage.SessionStartCommitAck(
                    peerHeader(fixture.bridge, "initial-ack-${player.seat}"),
                    player.id,
                    startId,
                ),
            )
        }
        runCurrent()
        return startId
    }

    private data class Fixture(
        val scope: TestScope,
        val bus: InMemoryRoomBus,
        val session: PassAndPlaySessionController<MafiaState, MafiaAction, MafiaEvent>,
        val hostRoom: LifecycleHostRoom,
        val bridge: MafiaHostRoomBridge,
        val peerBridge: MafiaPeerRoomBridge,
    ) {
        suspend fun close() {
            bridge.close()
            peerBridge.close()
        }
    }
}

private class LifecycleHostRoom(
    private val bus: InMemoryRoomBus,
    hostId: PlayerId,
) : LocalRoom {
    override val info = MutableStateFlow(
        RoomInfo("test", "Mafia Host", hostId, RoomInfo.Status.Hosting),
    ).asStateFlow()
    private val memberState = MutableStateFlow<List<RoomMember>>(emptyList())
    override val members = memberState.asStateFlow()
    override val isHost = true
    override val selfPlayerId = hostId
    override val incoming: Flow<RoomMessage> = bus.hostMessagesIn
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents
    val lifecycleState = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
    override val lifecycle = lifecycleState.asStateFlow()
    val sent = mutableListOf<Pair<SendTarget, HostMessage>>()
    val retiredMembers = mutableListOf<PlayerId>()
    var retirementError: NetError? = null
    var terminalSendGate: CompletableDeferred<Unit>? = null

    fun setMembers(members: List<RoomMember>) {
        memberState.value = members
    }

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> {
        sent += target to message
        if (message is HostMessage.SessionEnded) terminalSendGate?.await()
        bus.fromHost(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun retireDisconnectedMember(playerId: PlayerId): Result<Unit, NetError> {
        retirementError?.let { return Result.Failure(it) }
        if (playerId !in retiredMembers) retiredMembers += playerId
        memberState.value = memberState.value.filterNot { it.playerId == playerId }
        return Result.Success(Unit)
    }

    override suspend fun leave() = Unit
}
