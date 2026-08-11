package com.parlor.games.whodunit.multidevice

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.ackBriefingForAll
import com.parlor.games.whodunit.ackIntroForAll
import com.parlor.games.whodunit.revealRolesAndAdvance
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.networking.protocol.CommandStatus
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
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.session.passandplay.PassAndPlaySessionController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import com.parlor.core.result.Result as PResult

/**
 * Wave 9H-5: the host bridge subscribes to LocalRoom.peerEvents and
 * translates them into canonical actions:
 *
 *  - PeerLeft → MarkPlayerDisconnected
 *  - PeerReconnected → MarkPlayerReconnected + targeted snapshot re-send
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalCoroutinesApi::class)
class PartyConnectionEventsTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private val hostId = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val carol = PlayerId("carol")
    private val players = listOf(
        Player(hostId, "Host", seat = 0),
        Player(alice, "Alice", seat = 1),
        Player(bob, "Bob", seat = 2),
        Player(carol, "Carol", seat = 3),
    )

    @Test
    fun close_waits_for_room_event_collector_cleanup() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        val session = buildHostSession(loadCase())
        val bridge = WhodunitHostRoomBridge(
            session,
            PartyEventsHostRoom(bus, hostId),
            players,
            scope,
            json,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        runCurrent()
        assertThat(bus.peerEventSubscriberCount).isEqualTo(1)

        bridge.close()

        assertThat(bus.peerEventSubscriberCount).isEqualTo(0)
    }

    @Test
    fun peer_left_event_marks_player_disconnected() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(hostId)
        bus.registerPeer(alice)

        val payload = loadCase()
        val session = buildHostSession(payload)
        val hostRoom = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(
            session, hostRoom, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        runCurrent()

        // PeerLeft → host state's disconnectedPlayers grows.
        bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        assertThat(session.publicState.value.state.public.disconnectedPlayers).contains(alice)

        bridge.close()
    }

    @Test
    fun peer_reconnected_event_clears_disconnected_and_triggers_snapshot_resend() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bus = InMemoryRoomBus()
        bus.registerPeer(hostId)
        bus.registerPeer(alice)

        val payload = loadCase()
        val session = buildHostSession(payload)
        val hostRoom = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(
            session, hostRoom, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        runCurrent()

        // Alice drops off, then reconnects.
        bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        bus.emitPeerReconnected(alice, "Alice")
        runCurrent()

        // disconnectedPlayers should be empty again.
        assertThat(session.publicState.value.state.public.disconnectedPlayers)
            .doesNotContain(alice)

        bridge.close()
    }

    @Test
    fun production_reconnect_waits_for_start_commit_ack_before_restoring_seat() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        listOf(alice, bob, carol).forEach(bus::registerPeer)
        val payload = loadCase()
        val session = buildHostSession(payload)
        val room = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(
            session,
            room,
            players,
            scope,
            json,
            rejoinGraceMs = 200L,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = true,
        )
        val startId = completeStartHandshake(bridge, room, bus)
        room.sent.clear()

        bus.emitPeerLeft(alice, "Alice")
        bus.emitPeerReconnected(alice, "Alice")
        runCurrent()
        assertThat(session.hostState.value.state.public.disconnectedPlayers).contains(alice)
        assertThat(
            room.sent.any {
                it.first == SendTarget.Direct(alice) &&
                    it.second is HostMessage.SessionStarting
            },
        ).isTrue()

        bus.fromPeer(
            PeerMessage.SessionStartReady(
                peerHeader(bridge, "rejoin-ready"),
                alice,
                startId,
            ),
        )
        runCurrent()
        assertThat(session.hostState.value.state.public.disconnectedPlayers).contains(alice)
        assertThat(
            room.sent.any {
                it.first == SendTarget.Direct(alice) &&
                    it.second is HostMessage.SessionStartCommitted
            },
        ).isTrue()

        bus.fromPeer(
            PeerMessage.SessionStartCommitAck(
                peerHeader(bridge, "rejoin-commit-ack"),
                alice,
                startId,
            ),
        )
        runCurrent()
        assertThat(session.hostState.value.state.public.disconnectedPlayers)
            .doesNotContain(alice)
        assertThat(session.hostState.value.state.public.droppedPlayers).doesNotContain(alice)
        bridge.close()
    }

    @Test
    fun failed_reconnect_cannot_cancel_or_outlive_grace_expiry() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        listOf(alice, bob, carol).forEach(bus::registerPeer)
        val payload = loadCase()
        val session = buildHostSession(payload)
        val room = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(
            session,
            room,
            players,
            scope,
            json,
            rejoinGraceMs = 200L,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = true,
        )
        val startId = completeStartHandshake(bridge, room, bus)

        bus.emitPeerLeft(alice, "Alice")
        bus.emitPeerReconnected(alice, "Alice")
        runCurrent()
        advanceTimeBy(201L)
        runCurrent()

        val expired = session.hostState.value.state
        assertThat(expired.public.disconnectedPlayers).doesNotContain(alice)
        assertThat(expired.public.droppedPlayers).contains(alice)

        bus.fromPeer(
            PeerMessage.SessionStartReady(
                peerHeader(bridge, "late-rejoin-ready"),
                alice,
                startId,
            ),
        )
        bus.fromPeer(
            PeerMessage.SessionStartCommitAck(
                peerHeader(bridge, "late-rejoin-ack"),
                alice,
                startId,
            ),
        )
        runCurrent()
        assertThat(session.hostState.value.state).isEqualTo(expired)
        bridge.close()
    }

    @Test
    fun production_topology_reconciliation_observes_disconnect_before_bridge_creation() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val room = PartyEventsHostRoom(InMemoryRoomBus(), hostId)
        room.setMembers(
            listOf(
                RoomMember(alice, "Alice", connected = false),
                RoomMember(bob, "Bob", connected = true),
                RoomMember(carol, "Carol", connected = true),
            ),
        )
        val session = buildHostSession(loadCase())

        val bridge = WhodunitHostRoomBridge(
            session,
            room,
            players,
            scope,
            json,
            heartbeatIntervalMs = 0L,
            reconcileRoomTopology = true,
            requireStartHandshake = false,
        )
        runCurrent()
        assertThat(session.hostState.value.state.public.disconnectedPlayers).contains(alice)

        room.setMembers(
            listOf(
                RoomMember(alice, "Alice", connected = true),
                RoomMember(bob, "Bob", connected = true),
                RoomMember(carol, "Carol", connected = true),
            ),
        )
        runCurrent()
        assertThat(session.hostState.value.state.public.disconnectedPlayers)
            .doesNotContain(alice)
        bridge.close()
    }

    @Test
    fun host_can_continue_without_before_expiry_and_cancels_the_grace_job() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(hostId)
        bus.registerPeer(alice)
        val session = buildHostSession(loadCase())
        val room = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(
            session,
            room,
            players,
            scope,
            json,
            rejoinGraceMs = 200L,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )

        bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        assertThat(bridge.continueWithout(alice)).isTrue()
        runCurrent()

        val afterDecision = session.hostState.value.state
        assertThat(afterDecision.public.disconnectedPlayers.isEmpty()).isTrue()
        assertThat(afterDecision.phase is WhodunitPhase.Reveal).isTrue()
        assertThat(room.retiredMembers).isEqualTo(listOf(alice))

        advanceTimeBy(201L)
        runCurrent()
        assertThat(session.hostState.value.state).isEqualTo(afterDecision)
        assertThat(bridge.continueWithout(alice)).isFalse()
        bridge.close()
    }

    @Test
    fun failed_transport_retirement_keeps_the_player_disconnected_and_retryable() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val session = buildHostSession(loadCase())
        val room = PartyEventsHostRoom(bus, hostId).apply {
            retirementError = NetError.TransportFailure("injected retirement failure")
        }
        val bridge = WhodunitHostRoomBridge(
            session,
            room,
            players,
            scope,
            json,
            rejoinGraceMs = 200L,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )

        bus.emitPeerLeft(alice, "Alice")
        runCurrent()
        assertThat(bridge.continueWithout(alice)).isFalse()

        val state = session.hostState.value.state
        assertThat(state.public.disconnectedPlayers).contains(alice)
        assertThat(state.public.droppedPlayers).doesNotContain(alice)
        assertThat(room.retiredMembers).isEqualTo(emptyList())
        bridge.close()
    }

    @Test
    fun suspended_room_rejects_host_gameplay_without_mutating_state() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val room = PartyEventsHostRoom(InMemoryRoomBus(), hostId)
        val session = buildHostSession(loadCase())
        val bridge = WhodunitHostRoomBridge(
            session,
            room,
            players,
            scope,
            json,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        room.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)
        runCurrent()
        val before = session.hostState.value.state

        val result = bridge.submitHostAction(WhodunitAction.AcknowledgeIntro(hostId))

        assertThat(result).isEqualTo(PResult.Failure(SubmitError.SessionSuspended))
        assertThat(session.hostState.value.state).isEqualTo(before)
        bridge.close()
    }

    @Test
    fun start_is_version_stamped_and_host_leave_reaches_peer_before_close() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        bus.registerPeer(bob)
        bus.registerPeer(carol)
        val payload = loadCase()
        val session = buildHostSession(payload)
        val hostRoom = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(
            session, hostRoom, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = true,
        )
        val peerBridge = com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerRoomBridge(
            room = InMemoryPeerRoom(bus, alice, "Alice", hostId),
            selfPlayerId = alice,
            initialPublic = session.publicState.value.state,
            case = validatedWhodunitCaseForTest(payload, caseId = "last-dinner"),
            scope = scope,
            protocol = bridge.protocol,
            json = json,
        )
        var peerEnded = false
        val endCollector = scope.launch {
            peerBridge.hostDisconnected.collect { peerEnded = true }
        }

        val announcing = async {
            bridge.announceStart(
                caseId = "last-dinner",
                modeId = WhodunitIds.ClassicVoteModeId.raw,
                caseVersion = "1.0.0",
                caseDigest = "0".repeat(64),
            )
        }
        runCurrent()
        val starts = hostRoom.sent
            .mapNotNull { it.second as? HostMessage.SessionStarting }
        assertThat(starts.map(HostMessage.SessionStarting::startId).distinct().size)
            .isEqualTo(1)
        val start = starts.first()
        listOf(alice, bob, carol).forEach { playerId ->
            bus.fromPeer(
                PeerMessage.SessionStartReady(
                    peerHeader(bridge, "ready-${playerId.raw}"),
                    playerId,
                    start.startId,
                ),
            )
        }
        runCurrent()
        listOf(alice, bob, carol).forEach { playerId ->
            bus.fromPeer(
                PeerMessage.SessionStartCommitAck(
                    peerHeader(bridge, "commit-ack-${playerId.raw}"),
                    playerId,
                    start.startId,
                ),
            )
        }
        runCurrent()
        assertThat(announcing.await() is PResult.Success).isTrue()
        assertThat(start.header.sessionId == bridge.protocol.sessionId).isTrue()
        assertThat(start.header.gameId == WhodunitIds.GameId).isTrue()
        assertThat(start.header.gameVersion == WhodunitHostRoomBridge.GAME_VERSION).isTrue()
        assertThat(WhodunitHostRoomBridge.GAME_VERSION).isEqualTo(6)

        bridge.terminate(SessionEndReason.HostLeft)
        bridge.close()
        runCurrent()
        assertThat(peerEnded).isTrue()

        endCollector.cancel()
        peerBridge.close()
    }

    @Test
    fun retired_structured_action_command_is_rejected_without_mutating_host_state() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        val session = buildHostSession(loadCase())
        val hostRoom = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(
            session,
            hostRoom,
            players,
            scope,
            json,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        val before = session.hostState.value.state
        val commandId = "retired-action-000000000001"
        val legacyPayload = """
            {
              "type":"com.parlor.games.whodunit.domain.action.WhodunitAction.SubmitStructuredAction",
              "payload":{
                "type":"com.parlor.games.whodunit.domain.action.StructuredActionPayload.Alibi",
                "by":"alice",
                "text":"legacy"
              }
            }
        """.trimIndent()

        bus.fromPeer(
            PeerMessage.ClientCommand(
                header = peerHeader(bridge, commandId),
                actor = alice,
                commandId = commandId,
                clientSequence = 1L,
                expectedRevision = 0L,
                payload = legacyPayload.encodeToByteArray(),
            ),
        )
        runCurrent()

        val result = hostRoom.sent
            .mapNotNull { it.second as? HostMessage.CommandResult }
            .single { it.commandId == commandId }
        assertThat(result.status).isEqualTo(CommandStatus.InvalidAction)
        assertThat(result.authoritativeRevision).isEqualTo(0L)
        assertThat(session.hostState.value.state).isEqualTo(before)
        bridge.close()
    }

    @Test
    fun terminate_waits_for_authenticated_terminal_delivery_before_returning() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val room = PartyEventsHostRoom(InMemoryRoomBus(), hostId)
        val terminalGate = CompletableDeferred<Unit>()
        room.terminalSendGate = terminalGate
        val bridge = WhodunitHostRoomBridge(
            buildHostSession(loadCase()),
            room,
            players,
            scope,
            json,
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )

        val terminating = async { bridge.terminate(SessionEndReason.HostLeft) }
        runCurrent()

        assertThat(terminating.isCompleted).isFalse()
        assertThat(room.sent.count { it.second is HostMessage.SessionEnded }).isEqualTo(3)

        terminalGate.complete(Unit)
        terminating.await()
        assertThat(terminating.isCompleted).isTrue()
        bridge.close()
    }

    @Test
    fun app_lifecycle_suspension_freezes_and_then_resumes_the_authoritative_game() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val session = buildHostSession(loadCase())
        advanceToTimedRound(session)
        val room = PartyEventsHostRoom(InMemoryRoomBus(), hostId)
        val bridge = WhodunitHostRoomBridge(
            session, room, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        runCurrent()

        room.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)
        runCurrent()
        assertThat(session.hostState.value.state.public.paused).isTrue()

        room.lifecycleState.value = RoomLifecycleState.Resuming(120_000L)
        runCurrent()
        assertThat(session.hostState.value.state.public.paused).isTrue()
        assertThat(session.hostState.value.state.public.disconnectedPlayers.isEmpty()).isTrue()

        room.lifecycleState.value = RoomLifecycleState.Active
        runCurrent()
        assertThat(session.hostState.value.state.public.paused).isFalse()
        bridge.close()
    }

    @Test
    fun app_lifecycle_never_resumes_a_player_owned_pause() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val session = buildHostSession(loadCase())
        advanceToTimedRound(session)
        val room = PartyEventsHostRoom(InMemoryRoomBus(), hostId)
        val bridge = WhodunitHostRoomBridge(
            session, room, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        runCurrent()
        session.submit(WhodunitAction.Pause)
        assertThat(session.hostState.value.state.public.paused).isTrue()

        room.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)
        runCurrent()
        room.lifecycleState.value = RoomLifecycleState.Active
        runCurrent()

        assertThat(session.hostState.value.state.public.paused).isTrue()
        bridge.close()
    }

    @Test
    fun lifecycle_pause_resumes_after_active_precedes_final_peer_rejoin() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bus = InMemoryRoomBus()
        bus.registerPeer(alice)
        bus.registerPeer(bob)
        val session = buildHostSession(loadCase())
        advanceToTimedRound(session)
        val room = PartyEventsHostRoom(bus, hostId)
        val bridge = WhodunitHostRoomBridge(
            session, room, players, scope, json, heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
        )
        runCurrent()

        room.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)
        bus.emitPeerLeft(alice, "Alice")
        bus.emitPeerLeft(bob, "Bob")
        runCurrent()
        assertThat(session.hostState.value.state.public.paused).isTrue()

        // Foreground arrives first. Resume is correctly rejected while either
        // retained seat is still disconnected, and Active will not re-emit.
        room.lifecycleState.value = RoomLifecycleState.Active
        runCurrent()
        assertThat(session.hostState.value.state.public.paused).isTrue()

        bus.emitPeerReconnected(alice, "Alice")
        runCurrent()
        assertThat(session.hostState.value.state.public.paused).isTrue()
        assertThat(session.hostState.value.state.public.disconnectedPlayers).contains(bob)

        bus.emitPeerReconnected(bob, "Bob")
        runCurrent()
        assertThat(session.hostState.value.state.public.disconnectedPlayers.isEmpty()).isTrue()
        assertThat(session.hostState.value.state.public.paused).isFalse()
        assertThat(session.hostState.value.state.public.timer?.paused).isEqualTo(false)
        bridge.close()
    }

    // ============================================================ Fixture ==

    private suspend fun loadCase(): WhodunitCase {
        val bundled = com.parlor.games.whodunit.content.BundledWhodunitCases(
            knownCaseIds = listOf("last-dinner"),
            loadJson = { id -> runCatching { Res.readBytes("files/cases/$id.json").decodeToString() }.getOrNull() },
            json = json,
        )
        val emptyRemote = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        val definition = WhodunitDefinition(json)
        val repo = com.parlor.content.repository.DefaultCaseRepository(
            remote = com.parlor.content.datasource.KtorRemoteCaseDataSource(emptyRemote, "https://test.local"),
            cache = com.parlor.content.datasource.InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = com.parlor.content.validation.DefaultCaseValidator(
                json = json,
                knownSchemaVersion = 1,
                installedAppVersion = com.parlor.core.versioning.SemVer(1, 0, 0),
                gameRegistry = com.parlor.engine.registry.DefaultGameRegistry(listOf(definition)),
            ),
            json = json,
        )
        val r = repo.loadCase(CaseId("last-dinner"), com.parlor.games.whodunit.content.WhodunitPayloadValidator(json))
        return (r as PResult.Success).data.payload
    }

    private fun TestScope.buildHostSession(
        payload: WhodunitCase,
    ): PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent> {
        val sessionScope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val session = PassAndPlaySessionController(
            definition = WhodunitDefinition(json),
            config = SessionConfig(
                sessionId = SessionId("conn-events"),
                caseId = CaseId("last-dinner"),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = players,
                randomSeed = 42L,
            ),
            reducerContext = WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochSeconds(0)),
                random = RandomSource.seeded(42L),
                case = validatedWhodunitCaseForTest(payload, caseId = "last-dinner"),
            ),
            scope = sessionScope,
        )
        // Drive past Setup into PublicIntro so the state has assigned roles.
        kotlinx.coroutines.runBlocking { session.submit(WhodunitAction.AssignRoles(42L)) }
        return session
    }

    private suspend fun advanceToTimedRound(
        session: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    ) {
        session.ackIntroForAll(players)
        session.submit(WhodunitAction.AdvanceFromIntro)
        session.ackBriefingForAll(players)
        for (index in 1..4) session.submit(WhodunitAction.AdvanceBriefingCard(index))
        session.revealRolesAndAdvance(players)
        session.submit(WhodunitAction.RevealNextClue)
        session.submit(WhodunitAction.StartDiscussionTimer(180))
    }

    private fun peerHeader(
        bridge: WhodunitHostRoomBridge,
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

    private suspend fun TestScope.completeStartHandshake(
        bridge: WhodunitHostRoomBridge,
        room: PartyEventsHostRoom,
        bus: InMemoryRoomBus,
    ): String {
        val announcing = async {
            bridge.announceStart(
                caseId = "last-dinner",
                modeId = WhodunitIds.ClassicVoteModeId.raw,
                caseVersion = "1.0.0",
                caseDigest = "0".repeat(64),
            )
        }
        runCurrent()
        val startId = room.sent
            .mapNotNull { it.second as? HostMessage.SessionStarting }
            .first()
            .startId
        listOf(alice, bob, carol).forEach { playerId ->
            bus.fromPeer(
                PeerMessage.SessionStartReady(
                    peerHeader(bridge, "initial-ready-${playerId.raw}"),
                    playerId,
                    startId,
                ),
            )
        }
        runCurrent()
        assertThat(announcing.await() is PResult.Success).isTrue()
        listOf(alice, bob, carol).forEach { playerId ->
            bus.fromPeer(
                PeerMessage.SessionStartCommitAck(
                    peerHeader(bridge, "initial-ack-${playerId.raw}"),
                    playerId,
                    startId,
                ),
            )
        }
        runCurrent()
        return startId
    }
}

private class PartyEventsHostRoom(
    private val bus: InMemoryRoomBus,
    private val hostId: PlayerId,
) : LocalRoom {
    val sent = mutableListOf<Pair<SendTarget, HostMessage>>()
    val retiredMembers = mutableListOf<PlayerId>()
    var retirementError: NetError? = null
    var terminalSendGate: CompletableDeferred<Unit>? = null
    private val _info = MutableStateFlow(
        RoomInfo(
            code = "test",
            hostDisplayName = "Test Host",
            hostPlayerId = hostId,
            status = RoomInfo.Status.Hosting,
        ),
    )
    private val _members = MutableStateFlow<List<RoomMember>>(emptyList())

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost = true
    override val selfPlayerId: PlayerId = hostId
    override val incoming: Flow<RoomMessage> = bus.hostMessagesIn
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents
    val lifecycleState = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
    override val lifecycle = lifecycleState.asStateFlow()

    fun setMembers(members: List<RoomMember>) {
        _members.value = members
    }

    override suspend fun send(target: SendTarget, message: HostMessage): com.parlor.core.result.Result<Unit, NetError> {
        sent += target to message
        if (message is HostMessage.SessionEnded) terminalSendGate?.await()
        bus.fromHost(target, message)
        return com.parlor.core.result.Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): com.parlor.core.result.Result<Unit, NetError> =
        com.parlor.core.result.Result.Failure(NetError.Unauthorized)

    override suspend fun retireDisconnectedMember(
        playerId: PlayerId,
    ): com.parlor.core.result.Result<Unit, NetError> {
        retirementError?.let { return com.parlor.core.result.Result.Failure(it) }
        if (playerId !in retiredMembers) retiredMembers += playerId
        _members.value = _members.value.filterNot { it.playerId == playerId }
        return com.parlor.core.result.Result.Success(Unit)
    }

    override suspend fun leave() {}
}
