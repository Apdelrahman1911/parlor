package com.parlor.session.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.MAX_CONTROL_PAYLOAD_BYTES
import com.parlor.networking.protocol.MAX_ROOM_FRAME_BYTES
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BoundedPeerOutboxTest {
    private val peer = PlayerId("slow-peer")
    private val honest = PlayerId("honest-peer")
    private val protocol = SessionProtocol(
        sessionId = SessionId("session-outbound-0001"),
        gameId = GameId("fixture-game"),
        gameVersion = 1,
    )

    @Test
    fun `documented per-peer byte ceiling includes the retained heartbeat`() {
        assertEquals(
            BoundedPeerOutbox.MAX_CONTROL_FRAMES * MAX_CONTROL_PAYLOAD_BYTES +
                MAX_CONTROL_PAYLOAD_BYTES +
                MAX_ROOM_FRAME_BYTES * 2,
            BoundedPeerOutbox.MAX_OUTBOUND_BYTES_PER_PEER,
        )
    }

    @Test
    fun `close waits for the in-flight sender cancellation cleanup`() = runTest {
        val room = TargetBlockingRoom(
            blockedPlayer = peer,
            neverRelease = true,
            blockCancellationCleanup = true,
        )
        val outbox = BoundedPeerOutbox(peer, room, this, sendTimeoutMs = 10_000L)
        assertTrue(
            outbox.enqueue(
                HostMessage.Heartbeat(
                    header = header("heartbeat-close-0001", 1L),
                    authoritativeRevision = 0L,
                ),
            ),
        )
        runCurrent()
        room.blockEntered.await()

        val closing = async { outbox.close() }
        room.cleanupEntered.await()
        runCurrent()

        val completedBeforeCleanup = closing.isCompleted
        room.releaseCleanup.complete(Unit)
        closing.await()
        assertFalse(completedBeforeCleanup)
    }

    @Test
    fun `control queue is bounded and unsent snapshots are conflated`() = runTest {
        val room = TargetBlockingRoom(peer)
        val outbox = BoundedPeerOutbox(
            playerId = peer,
            room = room,
            scope = this,
            sendTimeoutMs = 10_000L,
        )

        assertTrue(outbox.enqueue(result(0)))
        runCurrent()
        room.blockEntered.await()

        var accepted = 0
        repeat(BoundedPeerOutbox.MAX_CONTROL_FRAMES + 10) { index ->
            if (outbox.enqueue(result(index + 1))) accepted += 1
        }
        assertEquals(BoundedPeerOutbox.MAX_CONTROL_FRAMES, accepted)
        assertFalse(outbox.enqueue(result(999)))
        assertTrue(outbox.enqueue(snapshot(revision = 1)))
        assertTrue(outbox.enqueue(snapshot(revision = 2)))

        room.releaseBlocked.complete(Unit)
        runCurrent()

        val sentResults = room.sent.mapNotNull { it.message as? HostMessage.CommandResult }
        assertEquals(BoundedPeerOutbox.MAX_CONTROL_FRAMES + 1, sentResults.size)
        assertEquals(
            listOf(2L),
            room.sent.mapNotNull { (it.message as? HostMessage.PlayerSnapshot)?.revision },
        )
        outbox.close()
    }

    @Test
    fun `stalled peer cannot delay honest peer or authoritative mutation`() = runTest {
        val room = TargetBlockingRoom(peer, neverRelease = true)
        var state = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peer, honest),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { playerId ->
                PlayerSnapshotPayload(
                    publicPayload = byteArrayOf(state.toByte()),
                    privatePayload = playerId.raw.encodeToByteArray(),
                )
            },
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
            outboundSendTimeoutMs = 10_000L,
        )

        coordinator.publishState(incrementRevision = false)
        runCurrent()
        room.blockEntered.await()
        assertTrue(
            room.sent.any {
                it.target == SendTarget.Direct(honest) &&
                    it.message is HostMessage.PlayerSnapshot
            },
        )

        val mutation = async {
            coordinator.applyHostMutation {
                state += 1
                true
            }
        }
        runCurrent()

        assertEquals(HostMutationResult.Applied, mutation.await())
        assertEquals(1L, coordinator.revision.value)
        assertTrue(
            room.sent.any {
                it.target == SendTarget.Direct(honest) &&
                    (it.message as? HostMessage.PlayerSnapshot)?.revision == 1L
            },
        )
        val privatePayloadsByTarget = room.sent
            .mapNotNull { sent ->
                (sent.message as? HostMessage.PlayerSnapshot)?.let { snapshot ->
                    sent.target to snapshot.privatePayload.decodeToString()
                }
            }
            .groupBy({ it.first }, { it.second })
        assertEquals(
            mapOf<SendTarget, List<String>>(
                SendTarget.Direct(honest) to listOf(honest.raw, honest.raw),
            ),
            privatePayloadsByTarget,
        )
        assertTrue(
            room.sent.none {
                it.target == SendTarget.Broadcast && it.message is HostMessage.PlayerSnapshot
            },
        )
        coordinator.close()
    }

    @Test
    fun `terminal delivery attempts peers in parallel and has a fixed deadline`() = runTest {
        val room = TargetBlockingRoom(peer, neverRelease = true)
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peer, honest),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0L,
            requireStartHandshake = false,
            outboundSendTimeoutMs = 100L,
        )

        val ending = async { coordinator.end(SessionEndReason.HostLeft) }
        runCurrent()
        room.blockEntered.await()
        assertTrue(
            room.sent.any {
                it.target == SendTarget.Direct(honest) &&
                    it.message is HostMessage.SessionEnded
            },
        )

        advanceTimeBy(100L)
        runCurrent()
        ending.await()
        assertEquals(1, room.attempts.count { it == SendTarget.Direct(peer) })
        coordinator.close()
    }

    @Test
    fun `terminal transport exception is returned as a sanitized failure`() = runTest {
        val room = TargetBlockingRoom(peer, sendFailure = IllegalStateException("private details"))
        val outbox = BoundedPeerOutbox(peer, room, this, sendTimeoutMs = 100L)

        val outcome = outbox.deliverTerminal(ended())

        assertIs<Result.Failure<NetError>>(outcome)
        assertIs<NetError.TransportFailure>(outcome.error)
        outbox.close()
    }

    @Test
    fun `concurrent terminal requests share one in flight delivery`() = runTest {
        val room = TargetBlockingRoom(peer)
        val outbox = BoundedPeerOutbox(peer, room, this, sendTimeoutMs = 1_000L)

        val first = async { outbox.deliverTerminal(ended()) }
        runCurrent()
        room.blockEntered.await()

        val duplicate = async { outbox.deliverTerminal(ended()) }
        runCurrent()
        room.releaseBlocked.complete(Unit)
        runCurrent()

        assertEquals(first.await(), duplicate.await())
        assertEquals(1, room.attempts.count { it == SendTarget.Direct(peer) })
        outbox.close()
    }

    @Test
    fun `close racing terminal publication cannot strand the terminal caller`() = runTest {
        val publishEntered = CompletableDeferred<Unit>()
        val allowPublish = CompletableDeferred<Unit>()
        val room = TargetBlockingRoom(peer)
        val outbox = BoundedPeerOutbox(
            playerId = peer,
            room = room,
            scope = this,
            sendTimeoutMs = 1_000L,
            beforeTerminalStatePublish = {
                publishEntered.complete(Unit)
                allowPublish.await()
            },
        )

        val ending = async { outbox.deliverTerminal(ended()) }
        publishEntered.await()
        outbox.close()
        allowPublish.complete(Unit)
        runCurrent()

        assertEquals(Result.Failure(NetError.NotConnected), ending.await())
        assertEquals(0, room.attempts.count { it == SendTarget.Direct(peer) })
    }

    private fun result(index: Int) = HostMessage.CommandResult(
        header = header("result-${index.toString().padStart(16, '0')}", index + 1L),
        commandId = "command-${index.toString().padStart(16, '0')}",
        status = CommandStatus.InvalidAction,
        authoritativeRevision = 0L,
        nextExpectedClientSequence = 1L,
    )

    private fun snapshot(revision: Long) = HostMessage.PlayerSnapshot(
        header = header("snapshot-${revision.toString().padStart(16, '0')}", revision + 1L),
        revision = revision,
        nextExpectedClientSequence = 1L,
        publicPayload = byteArrayOf(revision.toByte()),
        privatePayload = byteArrayOf(),
    )

    private fun ended() = HostMessage.SessionEnded(
        header = header("terminal-0000000000000001", 1L),
        reason = SessionEndReason.HostLeft,
        finalRevision = 0L,
    )

    private fun header(messageId: String, sequence: Long) = SessionEnvelopeHeader(
        protocol = ProtocolVersion(),
        sessionId = protocol.sessionId,
        gameId = protocol.gameId,
        gameVersion = protocol.gameVersion,
        messageId = messageId,
        sequence = sequence,
    )
}

private data class OutboundAttempt(val target: SendTarget, val message: HostMessage)

private class TargetBlockingRoom(
    private val blockedPlayer: PlayerId,
    private val neverRelease: Boolean = false,
    private val sendFailure: Exception? = null,
    private val blockCancellationCleanup: Boolean = false,
) : LocalRoom {
    private val inbox = Channel<RoomMessage>(capacity = 8)
    override val incoming: Flow<RoomMessage> = inbox.receiveAsFlow()
    override val info = MutableStateFlow(
        RoomInfo("local", "Fixture", PlayerId("host"), RoomInfo.Status.Hosting),
    )
    override val members = MutableStateFlow<List<RoomMember>>(emptyList())
    override val isHost: Boolean = true
    override val selfPlayerId: PlayerId = PlayerId("host")
    val attempts = mutableListOf<SendTarget>()
    val sent = mutableListOf<OutboundAttempt>()
    val blockEntered = CompletableDeferred<Unit>()
    val releaseBlocked = CompletableDeferred<Unit>()
    val cleanupEntered = CompletableDeferred<Unit>()
    val releaseCleanup = CompletableDeferred<Unit>()

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> {
        attempts += target
        sendFailure?.let { throw it }
        if (target == SendTarget.Direct(blockedPlayer)) {
            blockEntered.complete(Unit)
            if (neverRelease) {
                try {
                    awaitCancellation()
                } finally {
                    if (blockCancellationCleanup) {
                        cleanupEntered.complete(Unit)
                        withContext(NonCancellable) { releaseCleanup.await() }
                    }
                }
            } else {
                releaseBlocked.await()
            }
        }
        sent += OutboundAttempt(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun leave() = Unit
}
