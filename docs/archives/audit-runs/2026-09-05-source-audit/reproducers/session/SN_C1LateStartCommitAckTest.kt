// Audit-only reproducer. Not part of any application/test source set; NOT EXECUTED.
package audit.parlor

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
import com.parlor.session.multidevice.ValidatedSessionStart
import com.parlor.session.multidevice.awaitAuthoritativeSessionStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SN_C1LateStartCommitAckTest {
    @Test
    fun late_valid_commit_must_not_be_rejected_by_best_effort_ack_delay() = runTest {
        val room = SlowAckRoom()
        val protocol = SessionProtocol(SessionId("audit-session-0123456"), GameId("fixture-game"), 2)
        val startId = "audit-start-01234567890123"
        fun header(id: String, sequence: Long) = SessionEnvelopeHeader(
            protocol = protocol.protocol,
            sessionId = protocol.sessionId,
            gameId = protocol.gameId,
            gameVersion = protocol.gameVersion,
            messageId = id,
            sequence = sequence,
            connectionEpoch = protocol.connectionEpoch,
        )
        val offer = HostMessage.SessionStarting(
            startId = startId,
            caseId = "case",
            modeId = "classic",
            players = listOf(Player(PlayerId("host"), "Host", 0), Player(room.selfPlayerId, "Peer", 1)),
            sessionNonce = 0L,
            header = header(startId, 0L),
        )
        val waiting = async {
            awaitAuthoritativeSessionStart(
                room = room,
                expectedGameId = protocol.gameId,
                expectedGameVersion = protocol.gameVersion,
                deadlineMs = 100L,
                sendTimeoutMs = 20L,
            ) { _, _ -> true }
        }
        runCurrent()
        room.inbox.send(offer)
        runCurrent()
        advanceTimeBy(99L)
        room.inbox.send(HostMessage.SessionStartCommitted(startId, header("audit-commit-0123456789012", 1L)))
        runCurrent()
        assertEquals(1, room.commitAcknowledgements)
        advanceTimeBy(1L)
        runCurrent()
        // Predicted actual: Result.Failure(SessionStartFailure.Network(NetError.Timeout)).
        assertIs<Result.Success<ValidatedSessionStart>>(waiting.await())
    }

    private class SlowAckRoom : LocalRoom {
        override val isHost = false
        override val selfPlayerId = PlayerId("peer")
        val inbox = Channel<RoomMessage>(8)
        override val incoming = inbox.receiveAsFlow()
        override val peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 8)
        override val info = MutableStateFlow(RoomInfo("ROOM42", "Fixture", PlayerId("host"), RoomInfo.Status.Joined))
        override val members = MutableStateFlow<List<RoomMember>>(emptyList())
        override val lifecycle = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
        var commitAcknowledgements = 0
        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> =
            Result.Failure(NetError.Unauthorized)
        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
            if (message is PeerMessage.SessionStartCommitAck) {
                commitAcknowledgements++
                delay(10L)
            }
            return Result.Success(Unit)
        }
        override suspend fun leave() { inbox.close() }
    }
}
