package com.parlor.session.multidevice

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolValidation
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.protocol.validateFor
import com.parlor.networking.room.ForegroundConnectionValidator
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundSessionValidationTest {
    @Test
    fun hostUsesFreshAuthenticatedHeartbeatEchoWithoutReplayingGameActions() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.host.publishState(incrementRevision = false)
        runCurrent()
        fixture.hostRoom.foregroundReady.value = false
        val checking = async { assertNotNull(fixture.hostRoom.validator).validate() }
        runCurrent()
        assertTrue(checking.await())
        val challenge = fixture.hostRoom.sent.filterIsInstance<HostMessage.Heartbeat>().single()
        val reply = fixture.peerRoom.sent.filterIsInstance<PeerMessage.SessionHeartbeat>().single()
        assertEquals(challenge.header.messageId, reply.header.messageId)
        assertTrue(fixture.peerRoom.sent.none { it is PeerMessage.ClientCommand })
        assertEquals(0, fixture.applied)
        fixture.close()
        assertNull(fixture.hostRoom.validator)
        assertNull(fixture.peerRoom.validator)
    }

    @Test
    fun peerRequiresFreshRoundTripAndMissingAuthoritativeRevisionBeforeReady() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.host.publishState(incrementRevision = false)
        runCurrent()
        fixture.hostRoom.dropSnapshots = true
        assertEquals(HostMutationResult.Applied, fixture.host.applyHostMutation { fixture.applied++; true })
        runCurrent()
        fixture.peerRoom.foregroundReady.value = false
        val checking = async { assertNotNull(fixture.peerRoom.validator).validate() }
        runCurrent()
        assertFalse(checking.isCompleted)
        val probe = fixture.peerRoom.sent.filterIsInstance<PeerMessage.CommandOutcomeRequest>().single()
        assertEquals(probe.commandId, probe.header.messageId)
        assertEquals(ProtocolValidation.Valid, probe.validateFor(fixture.host.protocol))
        assertEquals(0L, fixture.peer.revision.value)
        assertEquals(Result.Failure(NetError.SessionSuspended), fixture.peer.submit(byteArrayOf(1)))
        assertTrue(fixture.peerRoom.sent.none { it is PeerMessage.ClientCommand })
        fixture.hostRoom.dropSnapshots = false
        fixture.peer.requestSnapshot()
        runCurrent()
        assertTrue(checking.await())
        assertEquals(1L, fixture.peer.revision.value)
        assertEquals(1, fixture.applied)
        fixture.close()
    }

    @Test
    fun peerRejectsWrongSessionAndUncorrelatedReplyEvenOnConnectedRoom() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.hostRoom.dropResults = true
        val checking = async { assertNotNull(fixture.peerRoom.validator).validate() }
        runCurrent()
        val result = fixture.hostRoom.sent.filterIsInstance<HostMessage.CommandResult>().single()
        fixture.peerRoom.receive(result.copy(header = result.header.copy(sessionId = SessionId("other-session-000000"))))
        fixture.peerRoom.receive(result.copy(
            header = result.header.copy(messageId = "unrelated-reply-000000000001"),
            commandId = "old-probe-00000000000000000001",
        ))
        runCurrent()
        assertFalse(checking.isCompleted)
        assertEquals(listOf<ProtocolValidation>(ProtocolValidation.WrongSession), fixture.violations)
        fixture.peerRoom.receive(result)
        runCurrent()
        assertTrue(checking.await())
        assertEquals(0, fixture.applied)
        fixture.close()
    }

    @Test
    fun foregroundProbeCannotReuseAnAlreadyAcceptedHostMessageId() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.host.publishState(incrementRevision = false)
        runCurrent()
        val snapshot = fixture.hostRoom.sent.filterIsInstance<HostMessage.PlayerSnapshot>().single()
        fixture.hostRoom.dropResults = true
        val checking = async { assertNotNull(fixture.peerRoom.validator).validate() }
        runCurrent()
        val result = fixture.hostRoom.sent.filterIsInstance<HostMessage.CommandResult>().single()
        fixture.peerRoom.receive(result.copy(header = snapshot.header))
        runCurrent()
        assertFalse(checking.isCompleted)
        fixture.peerRoom.receive(result)
        runCurrent()
        assertTrue(checking.await())
        assertEquals(0, fixture.applied)
        fixture.close()
    }

    @Test
    fun closingSessionDuringValidationCannotBecomeReadyFromALateReply() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.hostRoom.dropResults = true
        val validator = assertNotNull(fixture.peerRoom.validator)
        val checking = async { validator.validate() }
        runCurrent()
        val result = fixture.hostRoom.sent.filterIsInstance<HostMessage.CommandResult>().single()
        fixture.peer.close()
        fixture.peerRoom.receive(result)
        runCurrent()
        assertFalse(checking.await())
        assertFalse(validator.ready)
        assertNull(fixture.peerRoom.validator)
        fixture.host.close()
    }

    @Test
    fun interruptedPendingActionIsReconciledByIdNeverReplayedDuringValidation() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.host.publishState(incrementRevision = false)
        runCurrent()
        fixture.hostRoom.dropResults = true
        fixture.hostRoom.dropSnapshots = true
        assertIs<Result.Success<PeerCommandReceipt>>(fixture.peer.submit(byteArrayOf(1)))
        runCurrent()
        assertEquals(1, fixture.applied)
        fixture.peerRoom.foregroundReady.value = false
        fixture.hostRoom.dropResults = false
        fixture.hostRoom.dropSnapshots = false
        val checking = async { assertNotNull(fixture.peerRoom.validator).validate() }
        runCurrent()
        assertTrue(checking.await())
        assertEquals(1, fixture.applied)
        assertEquals(1, fixture.peerRoom.sent.filterIsInstance<PeerMessage.ClientCommand>().size)
        assertEquals(CommandStatus.Applied, assertIs<PeerCommandProgress.Resolved>(fixture.peer.commandProgress.value).outcome.status)
        fixture.close()
    }

    @Test
    fun hostLocalMutationIsBlockedWhileHealthyRemoteCommandsCanContinue() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.host.publishState(incrementRevision = false)
        runCurrent()
        fixture.hostRoom.foregroundReady.value = false
        assertEquals(HostMutationResult.Suspended, fixture.host.applyHostMutation { fixture.applied++; true })
        assertIs<Result.Success<PeerCommandReceipt>>(fixture.peer.submit(byteArrayOf(1)))
        runCurrent()
        assertEquals(1, fixture.applied)
        val progress = assertIs<PeerCommandProgress.Resolved>(fixture.peer.commandProgress.value)
        fixture.peer.acknowledgeCommandOutcome(progress.outcome.commandId)
        fixture.hostRoom.remoteCommandsAllowed = false
        assertIs<Result.Success<PeerCommandReceipt>>(fixture.peer.submit(byteArrayOf(1)))
        runCurrent()
        assertEquals(1, fixture.applied)
        assertEquals(
            CommandStatus.SessionSuspended,
            assertIs<PeerCommandProgress.Resolved>(fixture.peer.commandProgress.value).outcome.status,
        )
        fixture.close()
    }

    private class Fixture(scope: CoroutineScope) {
        private val protocol = SessionProtocol(SessionId("continuity-session"), GameId("fixture-game"), 1)
        val hostRoom = ValidationRoom(isHost = true, selfPlayerId = PlayerId("host"))
        val peerRoom = ValidationRoom(isHost = false, selfPlayerId = PlayerId("peer"))
        val violations = mutableListOf<ProtocolValidation>()
        var applied = 0
        val host: HostAuthoritativeSessionCoordinator
        val peer: PeerAuthoritativeSessionCoordinator

        init {
            hostRoom.other = peerRoom
            peerRoom.other = hostRoom
            host = HostAuthoritativeSessionCoordinator(
                room = hostRoom, protocol = protocol, remotePlayers = setOf(PlayerId("peer")), scope = scope,
                applyCommand = { _, _ -> applied++; CommandApplication.Applied },
                snapshotFor = { PlayerSnapshotPayload(byteArrayOf(applied.toByte()), byteArrayOf(9)) },
                heartbeatIntervalMs = 0L, requireStartHandshake = false,
            )
            peer = PeerAuthoritativeSessionCoordinator(
                room = peerRoom, protocol = protocol, selfPlayerId = PlayerId("peer"), scope = scope,
                onSnapshot = { _, _ -> true }, onProtocolViolation = { violations += it },
            )
        }

        suspend fun close() {
            peer.close()
            host.close()
        }
    }

    private class ValidationRoom(override val isHost: Boolean, override val selfPlayerId: PlayerId) : LocalRoom {
        override val info = MutableStateFlow(RoomInfo("ABCDEF", "Host", PlayerId("host"), RoomInfo.Status.Joined))
        override val members = MutableStateFlow(listOf(RoomMember(PlayerId(if (isHost) "peer" else "host"), "Guest", true)))
        override val foregroundReady = MutableStateFlow(true)
        override val acceptsRemoteGameCommands: Boolean get() = remoteCommandsAllowed
        private val inbox = Channel<RoomMessage>(32)
        override val incoming = inbox.receiveAsFlow()
        var remoteCommandsAllowed = true
        var validator: ForegroundConnectionValidator? = null
        lateinit var other: ValidationRoom
        val sent = mutableListOf<RoomMessage>()
        var dropSnapshots = false
        var dropResults = false

        override fun registerForegroundValidator(validator: ForegroundConnectionValidator): () -> Unit {
            this.validator = validator
            return { if (this.validator === validator) this.validator = null }
        }

        suspend fun receive(message: RoomMessage) = inbox.send(message)

        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
            sent += message
            if (!(dropSnapshots && message is HostMessage.PlayerSnapshot) && !(dropResults && message is HostMessage.CommandResult)) {
                other.receive(message)
            }
            return Result.Success(Unit)
        }

        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
            sent += message
            other.receive(message)
            return Result.Success(Unit)
        }

        override suspend fun leave() = Unit
    }
}
