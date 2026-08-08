package com.parlor.games.mafia.multidevice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
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
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.session.multidevice.InMemoryPeerRoom
import com.parlor.session.multidevice.InMemoryRoomBus
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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

        advanceTimeBy(201)
        runCurrent()
        assertThat(fixture.session.hostState.value.state).isEqualTo(afterDecision)
        assertThat(fixture.bridge.continueWithout(alice)).isFalse()
        fixture.close()
    }

    @Test
    fun stamped_start_and_explicit_host_leave_are_terminal_for_peer() = runTest {
        val fixture = fixture(TestScope(UnconfinedTestDispatcher(testScheduler)))
        var peerEnded = false
        val collector = fixture.scope.launch {
            fixture.peerBridge.hostDisconnected.collect { peerEnded = true }
        }

        fixture.bridge.announceStart("default", MafiaIds.ClassicModeId.raw)
        runCurrent()
        val start = fixture.hostRoom.sent
            .mapNotNull { it.second as? HostMessage.SessionStarting }
            .single()
        assertThat(start.header?.sessionId).isEqualTo(fixture.bridge.protocol.sessionId)
        assertThat(start.header?.gameId).isEqualTo(MafiaIds.GameId)
        assertThat(start.header?.gameVersion).isEqualTo(MafiaHostRoomBridge.GAME_VERSION)

        fixture.bridge.terminate(SessionEndReason.HostLeft)
        fixture.bridge.close()
        runCurrent()
        assertThat(peerEnded).isTrue()

        collector.cancel()
        fixture.peerBridge.close()
    }

    private suspend fun fixture(scope: TestScope): Fixture {
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
        val bridge = MafiaHostRoomBridge(
            controller = session,
            room = hostRoom,
            players = players,
            scope = scope,
            json = json,
            rejoinGraceMs = 200L,
            heartbeatIntervalMs = 0L,
        )
        val before = session.hostState.value.state
        session.submit(MafiaAction.StartGame)
        if (session.hostState.value.state != before) bridge.publishHostMutation()
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

    private data class Fixture(
        val scope: TestScope,
        val bus: InMemoryRoomBus,
        val session: PassAndPlaySessionController<MafiaState, MafiaAction, MafiaEvent>,
        val hostRoom: LifecycleHostRoom,
        val bridge: MafiaHostRoomBridge,
        val peerBridge: MafiaPeerRoomBridge,
    ) {
        fun close() {
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
    override val members = MutableStateFlow<List<RoomMember>>(emptyList()).asStateFlow()
    override val isHost = true
    override val selfPlayerId = hostId
    override val incoming: Flow<RoomMessage> = bus.hostMessagesIn
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents
    val sent = mutableListOf<Pair<SendTarget, HostMessage>>()

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> {
        sent += target to message
        bus.fromHost(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun leave() = Unit
}
