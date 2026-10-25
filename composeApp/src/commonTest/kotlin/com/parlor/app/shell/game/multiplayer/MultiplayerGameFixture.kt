package com.parlor.app.shell.game.multiplayer

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.FakeClock
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
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
import com.parlor.session.SubmissionReceipt
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

internal val gameHostId = PlayerId("game-host")
internal fun gamePlayers(count: Int): List<Player> = listOf(Player(gameHostId, "Host", 0)) +
    (1 until count).map { Player(PlayerId("seat-$it"), "Player $it", it) }

/** Real session coordinators, authenticated actor stamping and persistent lobby/game inbox handoff. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MultiplayerGameFixture<S : GameState, A : GameAction, E : GameEvent>(
    val testScope: TestScope,
    val spec: MultiplayerGameSpec<S, A, E>,
    val players: List<Player> = gamePlayers(3),
    seed: Long = 42L,
    val caseId: CaseId = spec.defaultCaseId,
    private val handshake: Boolean = true,
    reconcileMembers: Boolean = false,
    rejoinGraceMs: Long = 200L,
) {
    val scope = testScope.backgroundScope
    val bus = InMemoryRoomBus()
    private val inboxes = players.drop(1).associate { it.id to Channel<HostMessage>(128) }
    val hostRoom = RecordingGameHostRoom(bus, players, ::deliver)
    val session = PassAndPlaySessionController<S, A, E>(
        definition = spec.definition,
        config = SessionConfig(SessionId("test-session-$seed"), caseId, spec.modeId, players, seed),
        reducerContext = DefaultReducerContext(FakeClock(Instant.fromEpochSeconds(0)), RandomSource.seeded(seed)),
        scope = scope,
    )
    val host = HostedGameBridge(
        spec, session, hostRoom, players, scope,
        rejoinGraceMs = rejoinGraceMs, heartbeatIntervalMs = 0L,
        startRetryMs = 10L, startMaxRetryMs = 20L, startDeadlineMs = 80L,
        requireStartHandshake = handshake, reconcileRoomTopology = reconcileMembers,
    )
    val peerRooms = players.drop(1).associate { player ->
        bus.registerPeer(player.id)
        player.id to RecordingGamePeerRoom(
            InMemoryPeerRoom(bus, player.id, player.displayName, gameHostId),
            inboxes.getValue(player.id).receiveAsFlow(),
        )
    }
    val peers = mutableMapOf<PlayerId, PeerGameBridge<S, A, E>>()
    val starts = mutableMapOf<PlayerId, ValidatedSessionStart>()
    private var nextId = 0

    suspend fun attachPeers() {
        if (handshake) {
            val waiting = peerRooms.mapValues { (_, room) ->
                scope.async {
                    awaitAuthoritativeSessionStart(room, spec.definition.id, spec.version, deadlineMs = 200L) { offer, _ ->
                        spec.acceptsStart(offer, room)
                    }
                }
            }
            val announcing = scope.async { host.announceStart(caseId.raw, spec.modeId.raw) }
            testScope.runCurrent()
            waiting.forEach { (id, result) -> starts[id] = assertIs<Result.Success<ValidatedSessionStart>>(result.await()).data }
            assertIs<Result.Success<Unit>>(announcing.await())
        }
        peerRooms.keys.forEach { peers[it] = createPeer(it) }
        testScope.runCurrent()
        if (!handshake) peerRooms.keys.forEach(::requestSnapshot)
        testScope.runCurrent()
        assertSynchronized()
    }

    fun createPeer(id: PlayerId): PeerGameBridge<S, A, E> {
        val started = starts[id]
        return PeerGameBridge(
            spec, peerRooms.getValue(id), id, session.currentState(), scope,
            started?.protocol ?: host.protocol, hostLostTimeoutMs = 200L, acceptedStartOffer = started?.offer,
        )
    }

    fun header(label: String, protocol: SessionProtocol = host.protocol) = SessionEnvelopeHeader(
        protocol = protocol.protocol, sessionId = protocol.sessionId, gameId = protocol.gameId,
        gameVersion = protocol.gameVersion, messageId = "$label-${++nextId}".padEnd(32, 'x'),
        sequence = 0L, connectionEpoch = protocol.connectionEpoch,
    )

    fun latestSnapshot(id: PlayerId): HostMessage.PlayerSnapshot = hostRoom.sent
        .filter { it.first == SendTarget.Direct(id) }
        .mapNotNull { it.second as? HostMessage.PlayerSnapshot }.last()

    fun command(
        action: A, sender: PlayerId,
        sequence: Long = latestSnapshot(sender).nextExpectedClientSequence,
        revision: Long = latestSnapshot(sender).revision,
    ): PeerMessage.ClientCommand {
        val envelope = header("command")
        return PeerMessage.ClientCommand(envelope, sender, envelope.messageId, sequence, revision, spec.encodeAction(action))
    }

    fun send(sender: PlayerId, command: PeerMessage.ClientCommand): HostMessage.CommandResult {
        sendPeerMessage(sender, command)
        return hostRoom.sent.filter { it.first == SendTarget.Direct(sender) }
            .mapNotNull { it.second as? HostMessage.CommandResult }.last { it.commandId == command.commandId }
    }

    fun requestSnapshot(id: PlayerId) = sendPeerMessage(id, PeerMessage.SnapshotRequest(header("snapshot"), id, -1L))

    private fun sendPeerMessage(id: PlayerId, message: PeerMessage) {
        val sending = scope.async { peerRooms.getValue(id).sendToHost(message) }
        testScope.runCurrent()
        assertTrue(sending.isCompleted)
        assertIs<Result.Success<Unit>>(sending.getCompleted())
    }

    suspend fun perform(action: A, actor: PlayerId = gameHostId) {
        if (actor == gameHostId) {
            val applied = assertIs<Result.Success<SubmissionReceipt>>(host.submitHostAction(action))
            assertTrue(applied.data.stateChanged)
        } else {
            val peer = peers.getValue(actor)
            assertIs<Result.Success<SubmissionReceipt>>(peer.controller.submit(action))
            testScope.runCurrent()
            val resolved = assertIs<PeerCommandProgress.Resolved>(peer.commandProgress.value)
            assertEquals(CommandStatus.Applied, resolved.outcome.status)
            peer.acknowledgeCommandOutcome(resolved.outcome.commandId)
        }
        testScope.runCurrent()
        assertSynchronized()
    }

    fun assertSynchronized() {
        val canonical = session.currentState()
        val policy = spec.definition.projectionPolicy()
        peers.forEach { (id, peer) ->
            assertTrue(peer.hasAuthoritativeSnapshot.value)
            assertEquals(policy.toPublic(canonical), peer.controller.publicState.value)
            assertEquals(policy.toPlayer(canonical, id), peer.controller.privateStateFor(id).value)
            assertNull(peer.controller.canonicalState)
            assertNull(peer.controller.hostState)
            assertFailsWith<IllegalArgumentException> { peer.controller.privateStateFor(gameHostId) }
        }
        assertTrue(hostRoom.sent.filter { it.second is HostMessage.PlayerSnapshot }.all { it.first is SendTarget.Direct })
        assertEquals(0, bus.droppedHostMessageCount)
        assertEquals(0, bus.droppedPeerMessageCount)
        assertEquals(0, bus.droppedPeerEventCount)
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

internal class RecordingGameHostRoom(
    bus: InMemoryRoomBus,
    players: List<Player>,
    private val deliver: suspend (SendTarget, HostMessage) -> Unit,
) : LocalRoom {
    override val info = MutableStateFlow(RoomInfo("ABCD", "Host", gameHostId, RoomInfo.Status.Hosting)).asStateFlow()
    val memberState = MutableStateFlow(players.drop(1).map { RoomMember(it.id, it.displayName, true) })
    override val members = memberState.asStateFlow()
    override val isHost = true
    override val selfPlayerId = gameHostId
    override val incoming: Flow<RoomMessage> = bus.hostMessagesIn
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents
    val lifecycleState = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
    override val lifecycle = lifecycleState.asStateFlow()
    val sent = mutableListOf<Pair<SendTarget, HostMessage>>()
    val retired = mutableListOf<PlayerId>()
    var closeAdmissionsCalls = 0
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

    override suspend fun closeAdmissions(): Result<List<RoomMember>, NetError> {
        closeAdmissionsCalls++
        return Result.Success(members.value.filter { it.connected })
    }

    override suspend fun retireDisconnectedMember(playerId: PlayerId): Result<Unit, NetError> {
        retired += playerId
        return Result.Success(Unit)
    }

    override suspend fun leave() = Unit
}

internal class RecordingGamePeerRoom(
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
