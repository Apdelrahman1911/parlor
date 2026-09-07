package com.parlor.session.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.state.Player
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStartCommitDeadlineTest {
    @Test
    fun lateAcceptedCommitSurvivesSlowAcknowledgement() = runTest {
        val room = CommitAckRoom {
            delay(10L)
            Result.Success(Unit)
        }
        val waiting = startPreparedPeer(room)
        deliverLateCommit(room)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(waiting.isCompleted, "The receive deadline cannot undo an accepted commit")
        advanceTimeBy(9L)
        runCurrent()

        val start = assertIs<Result.Success<ValidatedSessionStart>>(waiting.await()).data
        assertEquals(offer, start.offer)
        assertEquals(protocol.copy(startId = offer.startId), start.protocol)
        assertEquals(1, room.commitAcknowledgements)
        assertEquals(0, room.incomingCollectors)
    }

    @Test
    fun acknowledgementTimeoutOnlyBoundsDeliveryAfterAcceptedCommit() = runTest {
        var acknowledgementCancelled = false
        val room = CommitAckRoom {
            try {
                awaitCancellation()
            } finally {
                acknowledgementCancelled = true
            }
        }
        val waiting = startPreparedPeer(room)
        deliverLateCommit(room)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(waiting.isCompleted)
        advanceTimeBy(19L)
        runCurrent()

        assertIs<Result.Success<ValidatedSessionStart>>(waiting.await())
        assertTrue(acknowledgementCancelled)
        assertEquals(119L, testScheduler.currentTime)
        assertEquals(1, room.commitAcknowledgements)
        assertEquals(0, room.incomingCollectors)
    }

    @Test
    fun acknowledgementFailureAfterReceiveDeadlineStillEntersCommittedStart() = runTest {
        val room = CommitAckRoom {
            delay(10L)
            Result.Failure(NetError.NotConnected)
        }
        val waiting = startPreparedPeer(room)
        deliverLateCommit(room)
        advanceTimeBy(10L)
        runCurrent()

        assertIs<Result.Success<ValidatedSessionStart>>(waiting.await())
        assertEquals(1, room.commitAcknowledgements)
        assertEquals(0, room.incomingCollectors)
    }

    @Test
    fun acknowledgementAdapterExceptionDoesNotUndoAcceptedStart() = runTest {
        val room = CommitAckRoom {
            delay(10L)
            throw IllegalStateException("synthetic acknowledgement failure")
        }
        val waiting = startPreparedPeer(room)
        deliverLateCommit(room)
        advanceTimeBy(10L)
        runCurrent()

        assertIs<Result.Success<ValidatedSessionStart>>(waiting.await())
        assertEquals(1, room.commitAcknowledgements)
        assertEquals(0, room.incomingCollectors)
    }

    @Test
    fun callerCancellationDuringAcknowledgementStillPropagates() = runTest {
        var acknowledgementCancelled = false
        val room = CommitAckRoom {
            try {
                awaitCancellation()
            } finally {
                acknowledgementCancelled = true
            }
        }
        val waiting = startPreparedPeer(room)
        deliverLateCommit(room)
        waiting.cancel(CancellationException("synthetic owner cancellation"))
        runCurrent()

        assertFailsWith<CancellationException> { waiting.await() }
        assertTrue(acknowledgementCancelled)
        assertEquals(1, room.commitAcknowledgements)
        assertEquals(0, room.incomingCollectors)
    }

    @Test
    fun absentCommitStillTimesOutWithoutAnAcknowledgement() = runTest {
        val room = CommitAckRoom()
        val waiting = startPreparedPeer(room)
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(
            SessionStartFailure.Network(NetError.Timeout),
            assertIs<Result.Failure<SessionStartFailure>>(waiting.await()).error,
        )
        assertEquals(0, room.commitAcknowledgements)
        assertEquals(0, room.incomingCollectors)
    }

    @Test
    fun commitForAnotherStartCannotEscapeTheReceiveDeadline() = runTest {
        val room = CommitAckRoom()
        val waiting = startPreparedPeer(room)
        room.inbox.send(commit.copy(startId = "other-start-01234567890123"))
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(
            SessionStartFailure.Network(NetError.Timeout),
            assertIs<Result.Failure<SessionStartFailure>>(waiting.await()).error,
        )
        assertEquals(0, room.commitAcknowledgements)
    }

    @Test
    fun matchingStartWithInvalidVersionIsRejectedWithoutAcknowledgement() = runTest {
        val room = CommitAckRoom()
        val waiting = startPreparedPeer(room)
        room.inbox.send(commit.copy(header = commit.header.copy(gameVersion = 2)))
        runCurrent()

        assertIs<SessionStartFailure.Protocol>(
            assertIs<Result.Failure<SessionStartFailure>>(waiting.await()).error,
        )
        assertEquals(0, room.commitAcknowledgements)
        assertEquals(0, room.incomingCollectors)
    }

    @Test
    fun coordinatorReacknowledgesDuplicateAndAcceptsFirstSnapshotAfterLateCommit() = runTest {
        val room = CommitAckRoom {
            delay(10L)
            Result.Success(Unit)
        }
        val waiting = startPreparedPeer(room)
        deliverLateCommit(room)
        advanceTimeBy(10L)
        runCurrent()
        val start = assertIs<Result.Success<ValidatedSessionStart>>(waiting.await()).data
        var snapshotsApplied = 0
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = start.protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { snapshot, revision ->
                assertEquals(0L, revision)
                assertContentEquals(byteArrayOf(1), snapshot.publicPayload)
                assertContentEquals(byteArrayOf(2), snapshot.privatePayload)
                snapshotsApplied++
                true
            },
            idGenerator = { "coordinator-frame-012345678901" },
        )
        try {
            runCurrent()
            room.inbox.send(commit)
            runCurrent()
            advanceTimeBy(10L)
            runCurrent()
            assertEquals(2, room.commitAcknowledgements)
            room.inbox.send(
                HostMessage.PlayerSnapshot(
                    header = header("snapshot-frame-0123456789012", sequence = 2L),
                    revision = 0L,
                    nextExpectedClientSequence = 1L,
                    publicPayload = byteArrayOf(1),
                    privatePayload = byteArrayOf(2),
                ),
            )
            runCurrent()
            assertTrue(coordinator.hasAuthoritativeSnapshot.value)
            assertEquals(0L, coordinator.revision.value)
            assertEquals(1, snapshotsApplied)
        } finally {
            coordinator.close()
        }
        assertEquals(0, room.incomingCollectors)
    }

    private suspend fun TestScope.startPreparedPeer(
        room: CommitAckRoom,
    ): Deferred<Result<ValidatedSessionStart, SessionStartFailure>> {
        var messageNumber = 0
        val waiting = async {
            awaitAuthoritativeSessionStart(
                room = room,
                expectedGameId = protocol.gameId,
                expectedGameVersion = protocol.gameVersion,
                deadlineMs = 100L,
                sendTimeoutMs = 20L,
                idGenerator = { "peer-frame-${(messageNumber++).toString().padStart(24, '0')}" },
            ) { received, expected ->
                assertEquals(offer, received)
                assertEquals(protocol, expected)
                true
            }
        }
        runCurrent()
        room.inbox.send(offer)
        runCurrent()
        assertEquals(1, room.sentToHost.count { it is PeerMessage.SessionStartReady })
        return waiting
    }

    private suspend fun TestScope.deliverLateCommit(room: CommitAckRoom) {
        advanceTimeBy(99L)
        room.inbox.send(commit)
        runCurrent()
        assertEquals(1, room.commitAcknowledgements)
    }

    private class CommitAckRoom(
        private val acknowledge: suspend () -> Result<Unit, NetError> = { Result.Success(Unit) },
    ) : LocalRoom {
        override val isHost = false
        override val selfPlayerId = PlayerId("peer")
        val inbox = Channel<RoomMessage>(8)
        var incomingCollectors = 0
            private set
        override val incoming = inbox.receiveAsFlow()
            .onStart { incomingCollectors++ }
            .onCompletion { incomingCollectors-- }
        override val peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 8)
        override val info = MutableStateFlow(
            RoomInfo("ROOM42", "Fixture", PlayerId("host"), RoomInfo.Status.Joined),
        )
        override val members = MutableStateFlow<List<RoomMember>>(emptyList())
        override val lifecycle = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
        val sentToHost = mutableListOf<PeerMessage>()
        val commitAcknowledgements: Int
            get() = sentToHost.count { it is PeerMessage.SessionStartCommitAck }

        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> =
            Result.Failure(NetError.Unauthorized)

        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
            sentToHost += message
            return if (message is PeerMessage.SessionStartCommitAck) acknowledge() else Result.Success(Unit)
        }

        override suspend fun leave() {
            inbox.close()
        }
    }

    private val peerId = PlayerId("peer")
    private val protocol = SessionProtocol(SessionId("test-session-012345678901"), GameId("fixture-game"), 1)
    private val offer = HostMessage.SessionStarting(
        startId = "test-start-012345678901234",
        caseId = "case",
        modeId = "classic",
        players = listOf(Player(PlayerId("host"), "Host", 0), Player(peerId, "Peer", 1)),
        sessionNonce = 0L,
        header = header("test-start-012345678901234", sequence = 0L),
    )
    private val commit = HostMessage.SessionStartCommitted(
        startId = offer.startId,
        header = header("commit-frame-01234567890123", sequence = 1L),
    )

    private fun header(messageId: String, sequence: Long): SessionEnvelopeHeader = SessionEnvelopeHeader(
        protocol = protocol.protocol,
        sessionId = protocol.sessionId,
        gameId = protocol.gameId,
        gameVersion = protocol.gameVersion,
        messageId = messageId,
        sequence = sequence,
        connectionEpoch = protocol.connectionEpoch,
    )
}
