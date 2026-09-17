package com.parlor.transport.p2p

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.SessionEndCommitStatus
import dev.p2pkit.core.ConnectionState
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerId
import dev.p2pkit.core.Platform
import dev.p2pkit.core.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundReconnectRecoveryTest {
    @Test
    fun persistentSoftLossStartsSecureResumeAfterOneSecondNotTheNativeThirtySecondRetryLoop() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        assertEquals(listOf<PeerEvent>(PeerEvent.HostLost), fixture.events)
        assertFalse(fixture.room.members.value.single().connected)
        advanceTimeBy(999L)
        runCurrent()
        assertTrue(fixture.requests.isEmpty())
        assertEquals(0, fixture.session.closeCalls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(fixture.credential), fixture.requests)
        assertEquals(1, fixture.session.closeCalls)
        assertEquals(RoomLifecycleState.Active, fixture.room.lifecycle.value)
        assertEquals(listOf(PeerEvent.HostLost, PeerEvent.HostRestored), fixture.events)
        assertEquals(
            listOf(
                PeerMessage.ResumeReady(PlayerId("peer"), fixture.rotated.offerId, 2L),
                PeerMessage.ResumeCommitAck(PlayerId("peer"), fixture.rotated.offerId, 2L),
            ),
            fixture.replacement.sent.filterIsInstance<P2pMessage.Binary>().map { fixture.codec.decode(it.bytes) },
        )
    }

    @Test
    fun fastNativeReconnectCancelsFallbackAndKeepsTheExistingSession() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(999L)
        fixture.session.stateFlow.value = ConnectionState.Connected
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue(fixture.requests.isEmpty())
        assertEquals(0, fixture.session.closeCalls)
        assertEquals(RoomInfo.Status.Joined, fixture.room.info.value.status)
        assertEquals(listOf(PeerEvent.HostLost, PeerEvent.HostRestored), fixture.events)
    }

    @Test
    fun reconnectProgressDoesNotRestartTheBound() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(600L)
        fixture.session.stateFlow.value = ConnectionState.Handshaking
        runCurrent()
        advanceTimeBy(399L)
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, fixture.requests.size)
        assertEquals(1, fixture.session.closeCalls)
    }

    @Test
    fun retiredSocketCannotRestoreTheRoomAndReplacementMustFinishReady() = runTest {
        val fixture = Fixture(this)
        val allowClose = CompletableDeferred<Unit>()
        val allowReady = CompletableDeferred<Unit>()
        fixture.session.closeHandler = { allowClose.await() }
        fixture.replacement.sendHandler = { message ->
            if (message is P2pMessage.Binary && fixture.codec.decode(message.bytes) is PeerMessage.ResumeReady) {
                allowReady.await()
            }
        }
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, fixture.session.closeCalls)
        assertTrue(fixture.requests.isEmpty(), "Close the retired session before opening its replacement")
        fixture.session.stateFlow.value = ConnectionState.Connected
        runCurrent()
        assertEquals(RoomLifecycleState.Resuming(120_000L), fixture.room.lifecycle.value)
        assertFalse(fixture.room.members.value.single().connected)
        assertFalse(fixture.room.acceptsLocalGameCommands)
        assertEquals(listOf<PeerEvent>(PeerEvent.HostLost), fixture.events)
        allowClose.complete(Unit)
        runCurrent()
        assertEquals(1, fixture.requests.size)
        assertEquals(listOf<PeerEvent>(PeerEvent.HostLost), fixture.events)
        assertIs<RoomLifecycleState.Resuming>(fixture.room.lifecycle.value)
        allowReady.complete(Unit)
        runCurrent()
        assertEquals(listOf(PeerEvent.HostLost, PeerEvent.HostRestored), fixture.events)
        assertEquals(RoomLifecycleState.Active, fixture.room.lifecycle.value)
    }

    @Test
    fun backgroundBeforeFallbackDefersRecoveryUntilForeground() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(400L)
        fixture.room.appBackgrounded(400L)
        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(fixture.requests.isEmpty())
        assertIs<RoomLifecycleState.Suspended>(fixture.room.lifecycle.value)
        fixture.room.appForegrounded(testScheduler.currentTime)
        runCurrent()
        assertEquals(1, fixture.requests.size)
        assertEquals(RoomLifecycleState.Active, fixture.room.lifecycle.value)
    }

    @Test
    fun leaveCancelsPendingFallbackWithoutRejoining() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(400L)
        fixture.room.leave()
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(RoomLifecycleState.Closed, fixture.room.lifecycle.value)
        assertEquals(1, fixture.kit.stopCalls)
        assertTrue(fixture.requests.isEmpty())
        assertFalse(PeerEvent.HostRestored in fixture.events)
    }

    @Test
    fun validatedTerminalFramePreventsPendingFallback() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        val terminal = HostMessage.SessionEnded(
            header = SessionEnvelopeHeader(
                protocol = ProtocolVersion(), sessionId = SessionId("test-session"),
                gameId = GameId("test-game"), gameVersion = 1,
                messageId = "terminal-message-000000000001", sequence = 1L,
            ),
            reason = SessionEndReason.HostLeft,
            finalRevision = 2L,
        )
        fixture.session.incomingFlow.emit(P2pMessage.Binary(fixture.codec.encode(terminal)))
        assertEquals(terminal, fixture.room.incoming.first())
        assertEquals(Result.Success(SessionEndCommitStatus.Committed), fixture.room.commitValidatedSessionEnd(terminal))
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue(fixture.requests.isEmpty())
        assertEquals(0, fixture.session.closeCalls)
        assertFalse(PeerEvent.HostRestored in fixture.events)
    }

    @Test
    fun replacingTheSessionCancelsItsOldFallback() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(400L)
        assertEquals(ResumeAdoptionOutcome.Ready, fixture.room.adoptResumedConnection(fixture.resumed()))
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue(fixture.requests.isEmpty())
        assertEquals(1, fixture.session.closeCalls)
        assertEquals(0, fixture.replacement.closeCalls)
        assertEquals(RoomLifecycleState.Active, fixture.room.lifecycle.value)
    }

    @Test
    fun fallbackPreservesTheOriginalRecoveryDeadlineThroughFurtherLossAndBackground() = runTest {
        val fixture = Fixture(this)
        fixture.connect = { Result.Failure(ResumeConnectionFailure(NetError.Timeout)) }
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(RoomLifecycleState.Resuming(120_000L), fixture.room.lifecycle.value)
        advanceTimeBy(5_000L)
        fixture.room.appBackgrounded(testScheduler.currentTime)
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(5_000L)
        fixture.room.appForegrounded(testScheduler.currentTime)
        runCurrent()
        assertEquals(RoomLifecycleState.Resuming(120_000L), fixture.room.lifecycle.value)
        advanceTimeBy(109_000L)
        runCurrent()
        assertEquals(RoomLifecycleState.Expired, fixture.room.lifecycle.value)
        assertEquals(1, fixture.kit.stopCalls)
    }

    @Test
    fun absentResumeCapabilityDoesNotKillARecoverableNativeSession() = runTest {
        val fixture = Fixture(this, resumable = false)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(0, fixture.session.closeCalls)
        assertTrue(fixture.requests.isEmpty())
        fixture.session.stateFlow.value = ConnectionState.Connected
        runCurrent()
        assertEquals(RoomInfo.Status.Joined, fixture.room.info.value.status)
    }

    @Test
    fun delayedOsTimerCannotStartResumeAfterTheOriginalLossDeadline() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.session.stateFlow.value = ConnectionState.Reconnecting
        runCurrent()
        fixture.wallOffset = 121_000L
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(fixture.requests.isEmpty())
        assertEquals(RoomLifecycleState.Expired, fixture.room.lifecycle.value)
        assertEquals(1, fixture.kit.stopCalls)
    }

    private class Fixture(scope: TestScope, resumable: Boolean = true) {
        val kit = FakeP2pKit(PeerId("peer"))
        val host = Peer(PeerId("host"), "Host", Platform.UNKNOWN, setOf(TransportKind.LAN))
        val session = FakeP2pSession(host)
        val replacement = FakeP2pSession(host)
        val codec = RoomMessageCodec()
        val credential = ResumableSessionCredential(
            offerId = "offer-1", membershipId = "membership", roomCode = "ABCDEF", displayName = "Peer",
            playerId = "peer", hostPeerId = "host", hostFingerprint = checkNotNull(session.peerIdentity.fingerprint).value,
            secret = "a".repeat(64), generation = 1L, issuedAtEpochMillis = 1L, expiresAtEpochMillis = 500_000L,
            gameId = "test-game", gameVersion = 1,
        )
        val rotated = credential.copy(offerId = "offer-2", generation = 2L, secret = "b".repeat(64))
        val requests = mutableListOf<ResumableSessionCredential>()
        val events = mutableListOf<PeerEvent>()
        var wallOffset = 0L
        var connect: suspend () -> Result<ResumedPeerConnection, ResumeConnectionFailure> = { Result.Success(resumed()) }
        val room = PeerP2pRoom(
            kit = kit, session = session, hostPeer = host, roomCode = "ABCDEF", scope = scope.backgroundScope,
            codec = codec, initialCredential = credential.takeIf { resumable }, hostDisplayName = host.name,
            currentTimeMillis = { scope.testScheduler.currentTime + wallOffset },
            resumeConnector = if (resumable) ({ requests += it; connect() }) else null,
        )

        init {
            scope.backgroundScope.launch { room.peerEvents.collect { events += it } }
        }

        fun resumed() = ResumedPeerConnection(replacement, host, rotated, host.name)
    }
}
