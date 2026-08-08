package com.parlor.session.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.ProtocolValidation
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthoritativeSessionCoordinatorTest {
    private val peerId = PlayerId("peer-one")
    private val protocol = SessionProtocol(
        sessionId = SessionId("session-0123456789"),
        gameId = GameId("fixture-game"),
        gameVersion = 1,
    )

    @Test
    fun `host applies once then rejects duplicate gap and stale revision deterministically`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var domainValue = 0
        var id = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, payload ->
                if (payload.contentEquals(byteArrayOf(1))) {
                    domainValue += 1
                    CommandApplication.Applied
                } else {
                    CommandApplication.InvalidAction
                }
            },
            snapshotFor = {
                PlayerSnapshotPayload(
                    publicPayload = byteArrayOf(domainValue.toByte()),
                    privatePayload = byteArrayOf(9),
                )
            },
            heartbeatIntervalMs = 0,
            idGenerator = { "host-message-${(++id).toString().padStart(16, '0')}" },
        )

        val first = command(id = COMMAND_ONE, clientSequence = 1, expectedRevision = 0)
        room.receive(first)
        advanceUntilIdle()
        assertEquals(1, domainValue)
        assertEquals(1, coordinator.revision.value)
        assertTrue(room.sent.any { it.message is HostMessage.PlayerSnapshot })

        room.receive(first)
        room.receive(command(id = COMMAND_GAP, clientSequence = 3, expectedRevision = 1))
        room.receive(command(id = COMMAND_STALE, clientSequence = 2, expectedRevision = 0))
        advanceUntilIdle()

        assertEquals(1, domainValue)
        val statuses = room.sent.mapNotNull { (it.message as? HostMessage.CommandResult)?.status }
        assertEquals(2, statuses.count { it == CommandStatus.Applied })
        assertTrue(CommandStatus.SequenceGap in statuses)
        assertTrue(CommandStatus.StaleRevision in statuses)
        coordinator.close()
    }

    @Test
    fun `host end is delivered before end returns and remains delivered after close`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            idGenerator = { "host-terminal-00000000000001" },
        )

        coordinator.end(SessionEndReason.HostLeft)
        coordinator.close()

        val terminal = room.sent.single().message as HostMessage.SessionEnded
        assertEquals(SessionEndReason.HostLeft, terminal.reason)
    }

    @Test
    fun `host outcome query returns recorded result without applying command again`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var domainValue = 0
        var id = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ ->
                domainValue += 1
                CommandApplication.Applied
            },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            idGenerator = { "host-outcome-${(++id).toString().padStart(16, '0')}" },
        )

        room.receive(command(id = COMMAND_ONE, clientSequence = 1, expectedRevision = 0))
        advanceUntilIdle()
        room.receive(
            PeerMessage.CommandOutcomeRequest(
                header = header(sequence = 0, messageId = COMMAND_ONE),
                actor = peerId,
                commandId = COMMAND_ONE,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, domainValue)
        val outcomes = room.sent.mapNotNull { it.message as? HostMessage.CommandResult }
        assertEquals(listOf(CommandStatus.Applied, CommandStatus.Applied), outcomes.map { it.status })
        assertEquals(listOf(1L, 1L), outcomes.map { it.authoritativeRevision })
        assertEquals(listOf(2L, 2L), outcomes.map { it.nextExpectedClientSequence })
        coordinator.close()
    }

    @Test
    fun `malformed command id is dropped without killing host command processing`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var applied = 0
        var id = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ ->
                applied += 1
                CommandApplication.Applied
            },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            idGenerator = { "host-malformed-${(++id).toString().padStart(16, '0')}" },
        )

        room.receive(command(id = "bad", clientSequence = 1, expectedRevision = 0))
        room.receive(command(id = COMMAND_ONE, clientSequence = 1, expectedRevision = 0))
        advanceUntilIdle()

        assertEquals(1, applied)
        assertEquals(
            listOf(CommandStatus.Applied),
            room.sent.mapNotNull { (it.message as? HostMessage.CommandResult)?.status },
        )
        coordinator.close()
    }

    @Test
    fun `heartbeat snapshots and terminal envelopes share one monotonic host sequence`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var id = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(1), byteArrayOf(2)) },
            heartbeatIntervalMs = 50L,
            idGenerator = { "host-message-${(++id).toString().padStart(16, '0')}" },
        )

        coordinator.publishState(incrementRevision = false)
        advanceTimeBy(50L)
        runCurrent()
        coordinator.end(SessionEndReason.HostLeft)
        coordinator.close()

        val sequences = room.sent.map {
            when (val message = it.message) {
                is HostMessage.PlayerSnapshot -> message.header.sequence
                is HostMessage.Heartbeat -> message.header.sequence
                is HostMessage.SessionEnded -> message.header.sequence
                else -> error("Unexpected message $message")
            }
        }
        assertEquals(sequences.sorted(), sequences)
        assertEquals(sequences.size, sequences.distinct().size)
    }

    @Test
    fun `peer installs only valid monotonic atomic snapshots`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val accepted = mutableListOf<Pair<Int, Int>>()
        val violations = mutableListOf<ProtocolValidation>()
        val ended = mutableListOf<SessionEndReason>()
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { payload, _ ->
                accepted += payload.publicPayload.single().toInt() to
                    payload.privatePayload.single().toInt()
            },
            onSessionEnded = { ended += it.reason },
            onProtocolViolation = { violations += it },
            idGenerator = { COMMAND_ONE },
        )

        room.receive(snapshot(sequence = 2, revision = 2, public = 7, private = 8))
        room.receive(snapshot(sequence = 1, revision = 1, public = 3, private = 4))
        room.receive(
            snapshot(sequence = 3, revision = 3, public = 5, private = 6).copy(
                header = header(sequence = 3, gameId = GameId("wrong-game")),
            ),
        )
        room.receive(
            HostMessage.SessionEnded(
                header = header(sequence = 4),
                reason = SessionEndReason.HostLeft,
                finalRevision = 2,
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(7 to 8), accepted)
        assertEquals(2, coordinator.revision.value)
        assertEquals(listOf<ProtocolValidation>(ProtocolValidation.WrongGame), violations)
        assertEquals(listOf(SessionEndReason.HostLeft), ended)
        coordinator.close()
    }

    @Test
    fun `in-memory peer room stamps command actor like production transport`() = runTest {
        val bus = InMemoryRoomBus()
        val boundActor = PlayerId("bound-peer")
        bus.registerPeer(boundActor)
        val room = InMemoryPeerRoom(
            bus = bus,
            selfPlayerId = boundActor,
            displayName = "Bound Peer",
            hostId = PlayerId("host"),
        )
        val received = async { bus.hostMessagesIn.first() }

        room.sendToHost(
            command(
                id = COMMAND_ONE,
                clientSequence = 1,
                expectedRevision = 0,
            ).copy(actor = PlayerId("forged-peer")),
        )

        assertEquals(boundActor, (received.await() as PeerMessage.ClientCommand).actor)
    }

    @Test
    fun `peer never replays sequence gap command and requests a fresh snapshot`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> },
            idGenerator = { COMMAND_ONE },
        )
        coordinator.submit(byteArrayOf(1))
        room.sentToHost.clear()

        room.receive(
            HostMessage.CommandResult(
                header = header(sequence = 1),
                commandId = COMMAND_ONE,
                status = CommandStatus.SequenceGap,
                authoritativeRevision = 0L,
            ),
        )
        advanceUntilIdle()

        assertTrue(room.sentToHost.none { it is PeerMessage.ClientCommand })
        assertTrue(room.sentToHost.any { it is PeerMessage.SnapshotRequest })
        coordinator.close()
    }

    @Test
    fun `peer accepts a command result that arrives after a newer-sequence snapshot`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> },
            idGenerator = { COMMAND_ONE },
        )
        runCurrent()
        val receipt = coordinator.submit(byteArrayOf(1))
        assertTrue(receipt is Result.Success)

        room.receive(snapshot(sequence = 9, revision = 1, public = 1, private = 2))
        room.receive(
            HostMessage.CommandResult(
                header = header(sequence = 3),
                commandId = receipt.data.commandId,
                status = CommandStatus.Applied,
                authoritativeRevision = 1L,
            ),
        )
        advanceUntilIdle()

        room.sentToHost.clear()
        room.emitEvent(PeerEvent.HostRestored)
        advanceUntilIdle()

        assertEquals(1L, coordinator.revision.value)
        assertTrue(room.sentToHost.none { it is PeerMessage.ClientCommand })
        coordinator.close()
    }

    @Test
    fun `peer allows only one mutating command in flight`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        var id = 0
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> },
            idGenerator = { "peer-command-${(++id).toString().padStart(16, '0')}" },
        )
        assertTrue(coordinator.submit(byteArrayOf(1)) is Result.Success)
        val second = coordinator.submit(byteArrayOf(2))

        assertEquals(NetError.CommandInFlight, (second as Result.Failure).error)
        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
        coordinator.close()
    }

    @Test
    fun `peer queries ambiguous send outcome on restore without replaying command`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        room.sendToHostError = NetError.Timeout
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> },
            idGenerator = { COMMAND_ONE },
        )

        assertTrue(coordinator.submit(byteArrayOf(1)) is Result.Failure)
        room.sentToHost.clear()
        room.sendToHostError = null
        runCurrent()
        room.emitEvent(PeerEvent.HostRestored)
        advanceUntilIdle()

        assertTrue(room.sentToHost.none { it is PeerMessage.ClientCommand })
        assertTrue(room.sentToHost.any { it is PeerMessage.CommandOutcomeRequest })
        coordinator.close()
    }

    @Test
    fun `unknown command outcome releases in flight command and reuses host sequence`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        var id = 0
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> },
            idGenerator = { "peer-command-${(++id).toString().padStart(16, '0')}" },
        )

        val first = coordinator.submit(byteArrayOf(1))
        assertTrue(first is Result.Success)
        room.receive(
            HostMessage.CommandResult(
                header = header(sequence = 1),
                commandId = first.data.commandId,
                status = CommandStatus.UnknownCommand,
                authoritativeRevision = 0L,
                nextExpectedClientSequence = 1L,
            ),
        )
        advanceUntilIdle()

        val second = coordinator.submit(byteArrayOf(2))
        assertTrue(second is Result.Success)
        assertEquals(1L, second.data.clientSequence)
        coordinator.close()
    }

    @Test
    fun `host rejects commands while suspended without consuming id or sequence`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var applied = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ ->
                applied += 1
                CommandApplication.Applied
            },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            idGenerator = { "host-lifecycle-000000000001" },
        )
        val first = command(id = COMMAND_ONE, clientSequence = 1, expectedRevision = 0)

        room.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)
        room.receive(first)
        advanceUntilIdle()

        assertEquals(0, applied)
        assertEquals(
            listOf(CommandStatus.SessionSuspended),
            room.sent.mapNotNull { (it.message as? HostMessage.CommandResult)?.status },
        )

        room.lifecycleState.value = RoomLifecycleState.Active
        room.receive(first)
        advanceUntilIdle()

        assertEquals(1, applied)
        assertEquals(1L, coordinator.revision.value)
        assertEquals(
            listOf(CommandStatus.SessionSuspended, CommandStatus.Applied),
            room.sent.mapNotNull { (it.message as? HostMessage.CommandResult)?.status },
        )
        coordinator.close()
    }

    @Test
    fun `peer cannot enqueue a command while room is suspended`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> },
            idGenerator = { COMMAND_ONE },
        )
        room.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)

        val result = coordinator.submit(byteArrayOf(1))

        assertEquals(NetError.SessionSuspended, (result as Result.Failure).error)
        assertTrue(room.sentToHost.isEmpty())
        coordinator.close()
    }

    private fun command(
        id: String,
        clientSequence: Long,
        expectedRevision: Long,
    ) = PeerMessage.ClientCommand(
        header = header(sequence = 0, messageId = id),
        actor = peerId,
        commandId = id,
        clientSequence = clientSequence,
        expectedRevision = expectedRevision,
        payload = byteArrayOf(1),
    )

    private fun snapshot(
        sequence: Long,
        revision: Long,
        public: Byte,
        private: Byte,
    ) = HostMessage.PlayerSnapshot(
        header = header(sequence = sequence),
        revision = revision,
        publicPayload = byteArrayOf(public),
        privatePayload = byteArrayOf(private),
    )

    private fun header(
        sequence: Long,
        messageId: String = "host-message-${sequence.toString().padStart(16, '0')}",
        gameId: GameId = protocol.gameId,
    ) = SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = protocol.sessionId,
        gameId = gameId,
        gameVersion = protocol.gameVersion,
        messageId = messageId,
        sequence = sequence,
    )

    private companion object {
        const val COMMAND_ONE = "command-00000000000000000000001"
        const val COMMAND_GAP = "command-00000000000000000000003"
        const val COMMAND_STALE = "command-00000000000000000000002"
    }
}

private data class SentMessage(val target: SendTarget, val message: HostMessage)

private class RecordingRoom(
    override val isHost: Boolean,
    override val selfPlayerId: PlayerId,
) : LocalRoom {
    private val inbox = Channel<RoomMessage>(Channel.UNLIMITED)
    private val events = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 8)
    override val incoming: Flow<RoomMessage> = inbox.receiveAsFlow()
    override val peerEvents: SharedFlow<PeerEvent> = events.asSharedFlow()
    override val info = MutableStateFlow(
        RoomInfo("local", "Fixture", PlayerId("host"), RoomInfo.Status.Joined),
    )
    override val members = MutableStateFlow<List<RoomMember>>(emptyList())
    val lifecycleState = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
    override val lifecycle = lifecycleState
    val sent = mutableListOf<SentMessage>()
    val sentToHost = mutableListOf<PeerMessage>()
    var sendToHostError: NetError? = null

    suspend fun receive(message: RoomMessage) {
        inbox.send(message)
    }

    suspend fun emitEvent(event: PeerEvent) {
        events.emit(event)
    }

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> {
        sent += SentMessage(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        sentToHost += message
        val error = sendToHostError
        return if (error == null) Result.Success(Unit) else Result.Failure(error)
    }

    override suspend fun leave() = Unit
}
