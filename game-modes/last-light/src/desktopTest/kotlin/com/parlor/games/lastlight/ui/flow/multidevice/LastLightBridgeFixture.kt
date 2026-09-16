package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.event.LastLightEvent
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.protocol.LastLightActionCodec
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.testing.InMemoryPeerRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.session.multidevice.PeerCommandProgress
import com.parlor.session.multidevice.ValidatedSessionStart
import com.parlor.session.multidevice.awaitAuthoritativeSessionStart
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

internal val testHostId = PlayerId("last-light-host")
internal val testAliceId = PlayerId("last-light-alice")
internal val testBobId = PlayerId("last-light-bob")
internal val lastLightTestPlayers = listOf(
    Player(testHostId, "Host", 0),
    Player(testAliceId, "Alice", 1),
    Player(testBobId, "Bob", 2),
)

internal fun lastLightTestConfig(seed: Long, players: List<Player> = lastLightTestPlayers) = SessionConfig(
    sessionId = SessionId("last-light-test-${seed.toString(16)}"),
    caseId = LastLightIds.CaseId,
    modeId = LastLightIds.StandardModeId,
    players = players,
    randomSeed = seed,
)

internal fun seedWithState(
    players: List<Player> = lastLightTestPlayers,
    predicate: (LastLightState) -> Boolean,
): Long {
    val definition = LastLightDefinition()
    return (0L..2_000L).first { predicate(definition.createInitialState(lastLightTestConfig(it, players))) }
}

/** Uses the real authenticated-actor fixture and a persistent inbox for lobby/game collector handoff. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LastLightBridgeFixture(
    val testScope: TestScope,
    val players: List<Player> = lastLightTestPlayers,
    seed: Long = 42L,
    requireStartHandshake: Boolean = false,
    reconcileMembers: Boolean = false,
    rejoinGraceMs: Long = 200L,
    initialOffline: Set<PlayerId> = emptySet(),
) {
    val scope = testScope.backgroundScope
    val bus = InMemoryRoomBus()
    private val inboxes = players.drop(1).associate { it.id to Channel<HostMessage>(128) }
    val hostRoom = LastLightRecordingHostRoom(bus, players, ::deliver).apply {
        memberState.value = memberState.value.map { it.copy(connected = it.playerId !in initialOffline) }
    }
    val definition = LastLightDefinition()
    val session = PassAndPlaySessionController<LastLightState, LastLightAction, LastLightEvent>(
        definition = definition,
        config = lastLightTestConfig(seed, players),
        reducerContext = DefaultReducerContext(
            clock = FakeClock(Instant.fromEpochSeconds(0)),
            random = RandomSource.seeded(seed),
        ),
        scope = scope,
    )
    val host = LastLightHostRoomBridge(
        controller = session,
        room = hostRoom,
        players = players,
        scope = scope,
        rejoinGraceMs = rejoinGraceMs,
        heartbeatIntervalMs = 0L,
        startRetryMs = 10L,
        startMaxRetryMs = 20L,
        startDeadlineMs = 80L,
        reconcileRoomTopology = reconcileMembers,
        requireStartHandshake = requireStartHandshake,
    )
    val peerRooms: Map<PlayerId, LastLightRecordingPeerRoom> = players.drop(1).associate { player ->
        bus.registerPeer(player.id)
        player.id to LastLightRecordingPeerRoom(
            InMemoryPeerRoom(bus, player.id, player.displayName, testHostId),
            inboxes.getValue(player.id).receiveAsFlow(),
        )
    }
    val peers = mutableMapOf<PlayerId, LastLightPeerRoomBridge>()
    val starts = mutableMapOf<PlayerId, ValidatedSessionStart>()
    private var nextId = 0

    suspend fun attachPeers(handshake: Boolean = false) {
        if (handshake) {
            val waiting = peerRooms.mapValues { (_, room) ->
                scope.async {
                    awaitAuthoritativeSessionStart(room, LastLightIds.GameId, 1, deadlineMs = 200L) { offer, _ ->
                        acceptsLastLightStart(offer, room)
                    }
                }
            }
            val announcing = scope.async { host.announceStart(LastLightIds.CaseId.raw, LastLightIds.StandardModeId.raw) }
            testScope.runCurrent()
            waiting.forEach { (id, result) ->
                starts[id] = assertIs<Result.Success<ValidatedSessionStart>>(result.await()).data
            }
            assertIs<Result.Success<Unit>>(announcing.await())
        }
        peerRooms.keys.forEach { id -> peers[id] = createPeer(id) }
        testScope.runCurrent()
        if (!handshake) peerRooms.keys.forEach { requestSnapshot(it) }
        testScope.runCurrent()
    }

    fun createPeer(id: PlayerId): LastLightPeerRoomBridge {
        val started = starts[id]
        return LastLightPeerRoomBridge(
            room = peerRooms.getValue(id),
            selfPlayerId = id,
            initialPublic = session.currentState(),
            scope = scope,
            protocol = started?.protocol ?: host.protocol,
            hostLostTimeoutMs = 200L,
            acceptedStartOffer = started?.offer,
        )
    }

    fun header(label: String, protocol: SessionProtocol = host.protocol) = SessionEnvelopeHeader(
        protocol = protocol.protocol,
        sessionId = protocol.sessionId,
        gameId = protocol.gameId,
        gameVersion = protocol.gameVersion,
        messageId = "${label}-${++nextId}".padEnd(32, 'x'),
        sequence = 0L,
        connectionEpoch = protocol.connectionEpoch,
    )

    fun latestSnapshot(id: PlayerId): HostMessage.PlayerSnapshot = hostRoom.sent
        .filter { it.first == SendTarget.Direct(id) }
        .mapNotNull { it.second as? HostMessage.PlayerSnapshot }
        .last()

    fun command(
        action: LastLightAction,
        sender: PlayerId,
        sequence: Long = latestSnapshot(sender).nextExpectedClientSequence,
        revision: Long = latestSnapshot(sender).revision,
    ): PeerMessage.ClientCommand {
        val envelope = header("command")
        return PeerMessage.ClientCommand(envelope, sender, envelope.messageId, sequence, revision, LastLightActionCodec.encode(action))
    }

    fun send(sender: PlayerId, command: PeerMessage.ClientCommand): HostMessage.CommandResult {
        sendPeerMessage(sender, command)
        return hostRoom.sent.filter { it.first == SendTarget.Direct(sender) }
            .mapNotNull { it.second as? HostMessage.CommandResult }.last { it.commandId == command.commandId }
    }

    fun requestSnapshot(id: PlayerId) {
        sendPeerMessage(id, PeerMessage.SnapshotRequest(header("snapshot"), id, -1L))
    }

    private fun sendPeerMessage(id: PlayerId, message: PeerMessage) {
        val sending = scope.async { peerRooms.getValue(id).sendToHost(message) }
        testScope.runCurrent()
        assertTrue(sending.isCompleted)
        assertIs<Result.Success<Unit>>(sending.getCompleted())
    }

    /** Performs a real UI-to-bridge action and checks the authoritative acknowledgement. */
    suspend fun perform(action: LastLightAction) {
        val actor = when (action) {
            is LastLightAction.PlayCards -> action.by
            is LastLightAction.Challenge -> action.by
            else -> testHostId
        }
        if (actor == testHostId) {
            val applied = assertIs<Result.Success<*>>(host.submitHostAction(action))
            assertTrue((applied.data as com.parlor.session.SubmissionReceipt).stateChanged)
        } else {
            val peer = peers.getValue(actor)
            assertIs<Result.Success<*>>(peer.controller.submit(action))
            testScope.runCurrent()
            val resolved = assertIs<PeerCommandProgress.Resolved>(peer.commandProgress.value)
            assertEquals(CommandStatus.Applied, resolved.outcome.status)
            peer.acknowledgeCommandOutcome(resolved.outcome.commandId)
        }
        testScope.runCurrent()
    }

    suspend fun playCurrent(count: Int = 1) {
        val state = session.currentState()
        val actor = PlayerId(checkNotNull(state.public.turnPlayerId))
        perform(LastLightAction.PlayCards(actor, state.privatePerPlayer.getValue(actor).hand.take(count).map { it.id }))
    }

    suspend fun challengeCurrent() {
        perform(LastLightAction.Challenge(PlayerId(checkNotNull(session.currentState().public.turnPlayerId))))
    }

    fun deliver(target: SendTarget, message: HostMessage) {
        when (target) {
            SendTarget.Broadcast -> inboxes.values.forEach { assertTrue(it.trySend(message).isSuccess) }
            is SendTarget.Direct -> assertTrue(inboxes.getValue(target.playerId).trySend(message).isSuccess)
        }
    }

    suspend fun close() {
        host.close()
        peers.values.forEach { it.close() }
        session.close()
        inboxes.values.forEach { it.close() }
    }
}

internal class LastLightRecordingHostRoom(
    bus: InMemoryRoomBus,
    players: List<Player>,
    private val deliver: suspend (SendTarget, HostMessage) -> Unit,
) : LocalRoom {
    override val info = MutableStateFlow(RoomInfo("ABCD", "Host", testHostId, RoomInfo.Status.Hosting)).asStateFlow()
    val memberState = MutableStateFlow(players.drop(1).map { RoomMember(it.id, it.displayName, true) })
    override val members = memberState.asStateFlow()
    override val isHost = true
    override val selfPlayerId = testHostId
    override val incoming: Flow<RoomMessage> = bus.hostMessagesIn
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents
    val lifecycleState = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
    override val lifecycle = lifecycleState.asStateFlow()
    val sent = mutableListOf<Pair<SendTarget, HostMessage>>()
    val retired = mutableListOf<PlayerId>()
    var holdSnapshots = false
    var dropStartOffers = false
    var snapshotSendGate: Pair<PlayerId, CompletableDeferred<Unit>>? = null
    var terminalGate: CompletableDeferred<Unit>? = null
    private val heldSnapshots = mutableListOf<Pair<SendTarget, HostMessage>>()

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        sent += target to message
        if (message is HostMessage.PlayerSnapshot) {
            snapshotSendGate?.let { (recipient, gate) ->
                if (target == SendTarget.Direct(recipient)) gate.await()
            }
        }
        if (message is HostMessage.SessionEnded) terminalGate?.await()
        if (message is HostMessage.SessionStarting && dropStartOffers) return Result.Success(Unit)
        if (message is HostMessage.PlayerSnapshot && holdSnapshots) heldSnapshots += target to message
        else deliver(target, message)
        return Result.Success(Unit)
    }

    suspend fun releaseSnapshots() {
        holdSnapshots = false
        val pending = heldSnapshots.toList()
        heldSnapshots.clear()
        pending.forEach { (target, message) -> deliver(target, message) }
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> = Result.Failure(NetError.Unauthorized)

    override suspend fun retireDisconnectedMember(playerId: PlayerId): Result<Unit, NetError> {
        retired += playerId
        return Result.Success(Unit)
    }

    override suspend fun leave() = Unit
}

internal class LastLightRecordingPeerRoom(
    private val delegate: InMemoryPeerRoom,
    override val incoming: Flow<RoomMessage>,
) : LocalRoom by delegate {
    val sent = mutableListOf<PeerMessage>()
    var failNextCommandAfterDelivery = false
    var readyGate: CompletableDeferred<Unit>? = null
    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        sent += message
        if (message is PeerMessage.SessionStartReady) readyGate?.await()
        val result = delegate.sendToHost(message)
        return if (message is PeerMessage.ClientCommand && failNextCommandAfterDelivery) {
            failNextCommandAfterDelivery = false
            Result.Failure(NetError.NotConnected)
        } else result
    }
}
