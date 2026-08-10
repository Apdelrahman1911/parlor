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
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.networking.testing.InMemoryPeerRoom
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
            requireStartHandshake = false,
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
    fun `host mutation and peer command share one authoritative order`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var domainValue = 0
        val hostStarted = CompletableDeferred<Unit>()
        val releaseHost = CompletableDeferred<Unit>()
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ ->
                domainValue += 100
                CommandApplication.Applied
            },
            snapshotFor = {
                PlayerSnapshotPayload(byteArrayOf(domainValue.toByte()), byteArrayOf())
            },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )

        val hostMutation = async {
            coordinator.applyHostMutation {
                hostStarted.complete(Unit)
                releaseHost.await()
                domainValue += 1
                true
            }
        }
        hostStarted.await()
        room.receive(command(id = COMMAND_ONE, clientSequence = 1, expectedRevision = 0))
        releaseHost.complete(Unit)
        advanceUntilIdle()

        assertEquals(HostMutationResult.Applied, hostMutation.await())
        assertEquals(1, domainValue)
        assertEquals(1L, coordinator.revision.value)
        assertTrue(
            room.sent.mapNotNull { it.message as? HostMessage.CommandResult }
                .any { it.status == CommandStatus.StaleRevision },
        )
        coordinator.close()
    }

    @Test
    fun `unchanged host mutation does not advance revision or publish snapshot`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )

        assertEquals(HostMutationResult.Unchanged, coordinator.applyHostMutation { false })
        assertEquals(0L, coordinator.revision.value)
        assertTrue(room.sent.none { it.message is HostMessage.PlayerSnapshot })
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
            requireStartHandshake = false,
            idGenerator = { "host-terminal-00000000000001" },
        )

        coordinator.end(SessionEndReason.HostLeft)
        coordinator.close()

        val terminal = room.sent.single().message as HostMessage.SessionEnded
        assertEquals(SessionEndReason.HostLeft, terminal.reason)
    }

    @Test
    fun `closing host coordinator completes executing and queued mutation callers`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        val entered = CompletableDeferred<Unit>()
        val neverReleased = CompletableDeferred<Unit>()
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )
        val executing = async {
            coordinator.applyHostMutation {
                entered.complete(Unit)
                neverReleased.await()
                true
            }
        }
        entered.await()
        val queued = async { coordinator.applyHostMutation { true } }
        runCurrent()

        coordinator.close()
        runCurrent()

        assertTrue(executing.isCancelled)
        assertTrue(queued.isCancelled || queued.await() == HostMutationResult.Closed)
    }

    @Test
    fun `parent scope cancellation completes executing and queued mutation callers`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        val parent = SupervisorJob()
        val ownedScope = CoroutineScope(coroutineContext + parent)
        val entered = CompletableDeferred<Unit>()
        val neverReleased = CompletableDeferred<Unit>()
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = ownedScope,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )
        val executing = async {
            coordinator.applyHostMutation {
                entered.complete(Unit)
                neverReleased.await()
                true
            }
        }
        entered.await()
        val queued = async { coordinator.applyHostMutation { true } }
        runCurrent()

        parent.cancel(CancellationException("screen owner disposed"))
        runCurrent()

        assertFailsWith<CancellationException> { withTimeout(1_000L) { executing.await() } }
        assertFailsWith<CancellationException> { withTimeout(1_000L) { queued.await() } }
    }

    @Test
    fun `caller cancellation removes a queued host mutation before it can execute`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var domainValue = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )
        val executing = async {
            coordinator.applyHostMutation {
                entered.complete(Unit)
                release.await()
                domainValue += 1
                true
            }
        }
        entered.await()
        val cancelled = async {
            coordinator.applyHostMutation {
                domainValue += 100
                true
            }
        }
        runCurrent()

        cancelled.cancel(CancellationException("host screen action abandoned"))
        release.complete(Unit)
        assertEquals(HostMutationResult.Applied, executing.await())
        runCurrent()

        assertTrue(cancelled.isCancelled)
        assertEquals(1, domainValue)
        coordinator.close()
    }

    @Test
    fun `failed publication does not kill mailbox or strand later host mutation`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var snapshotAttempt = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = {
                snapshotAttempt += 1
                if (snapshotAttempt == 1) error("injected snapshot failure")
                PlayerSnapshotPayload(byteArrayOf(1), byteArrayOf())
            },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )

        coordinator.publishState(incrementRevision = false)
        runCurrent()

        val result = withTimeout(1_000L) {
            coordinator.applyHostMutation { true }
        }
        assertEquals(HostMutationResult.Applied, result)
        assertEquals(1L, coordinator.revision.value)
        coordinator.close()
    }

    @Test
    fun `failed terminal transport is bounded and mailbox remains closable`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )
        room.sendFailure = IllegalStateException("injected terminal send failure")

        withTimeout(1_000L) { coordinator.end(SessionEndReason.HostLeft) }
        coordinator.close()
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
            requireStartHandshake = false,
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
    fun `one peer cannot evict another peer command outcome`() = runTest {
        val attacker = PlayerId("peer-two")
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var id = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId, attacker),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
            idGenerator = { "host-ledger-${(++id).toString().padStart(16, '0')}" },
        )

        room.receive(command(id = COMMAND_ONE, clientSequence = 1, expectedRevision = 0))
        runCurrent()
        repeat(257) { index ->
            val commandId = "attacker-command-${index.toString().padStart(16, '0')}"
            room.receive(
                PeerMessage.ClientCommand(
                    header = header(sequence = 0, messageId = commandId),
                    actor = attacker,
                    commandId = commandId,
                    clientSequence = index + 1L,
                    expectedRevision = 0L,
                    payload = byteArrayOf(1),
                ),
            )
            runCurrent()
        }
        room.receive(
            PeerMessage.CommandOutcomeRequest(
                header = header(sequence = 0, messageId = COMMAND_ONE),
                actor = peerId,
                commandId = COMMAND_ONE,
            ),
        )
        runCurrent()

        val honestOutcomes = room.sent
            .filter { it.target == SendTarget.Direct(peerId) }
            .mapNotNull { it.message as? HostMessage.CommandResult }
            .filter { it.commandId == COMMAND_ONE }
        assertEquals(
            listOf(CommandStatus.InvalidAction, CommandStatus.InvalidAction),
            honestOutcomes.map { it.status },
        )
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
            requireStartHandshake = false,
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
            requireStartHandshake = false,
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
                true
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
    fun `failed snapshot installation does not consume its revision`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val installed = mutableListOf<Int>()
        val violations = mutableListOf<ProtocolValidation>()
        var rejectNext = true
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { payload, _ ->
                if (rejectNext) {
                    rejectNext = false
                    false
                } else {
                    installed += payload.publicPayload.single().toInt()
                    true
                }
            },
            onProtocolViolation = { violations += it },
            idGenerator = { COMMAND_ONE },
        )

        room.receive(snapshot(sequence = 1, revision = 4, public = 1, private = 2))
        runCurrent()
        assertEquals(0L, coordinator.revision.value)
        assertEquals(
            listOf<ProtocolValidation>(ProtocolValidation.SnapshotPayloadInvalid),
            violations,
        )

        room.receive(snapshot(sequence = 2, revision = 4, public = 7, private = 8))
        runCurrent()

        assertEquals(listOf(7), installed)
        assertEquals(4L, coordinator.revision.value)
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
            onSnapshot = { _, _ -> true },
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
            onSnapshot = { _, _ -> true },
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
            onSnapshot = { _, _ -> true },
            idGenerator = { "peer-command-${(++id).toString().padStart(16, '0')}" },
        )
        assertTrue(coordinator.submit(byteArrayOf(1)) is Result.Success)
        val second = coordinator.submit(byteArrayOf(2))

        assertEquals(NetError.CommandInFlight, (second as Result.Failure).error)
        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
        coordinator.close()
    }

    @Test
    fun `peer retains every authoritative command outcome until UI acknowledgement`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        var id = 0
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { "peer-command-${(++id).toString().padStart(16, '0')}" },
        )
        val statuses = listOf(
            CommandStatus.Applied,
            CommandStatus.InvalidAction,
            CommandStatus.Unauthorized,
            CommandStatus.StaleRevision,
            CommandStatus.Duplicate,
        )

        statuses.forEachIndexed { index, status ->
            val submitted = coordinator.submit(byteArrayOf(index.toByte())) as Result.Success
            val receipt = submitted.data
            room.receive(
                HostMessage.CommandResult(
                    header = header(sequence = index.toLong() + 1L),
                    commandId = receipt.commandId,
                    status = status,
                    authoritativeRevision = 0L,
                    nextExpectedClientSequence = index.toLong() + 2L,
                ),
            )
            runCurrent()

            val resolved = coordinator.commandProgress.value as PeerCommandProgress.Resolved
            assertEquals(receipt.commandId, resolved.outcome.commandId)
            assertEquals(status, resolved.outcome.status)
            // Results are replayed, so attaching after host delivery still
            // observes the outcome instead of losing it to tryEmit.
            assertEquals(status, coordinator.results.first().status)
            coordinator.acknowledgeCommandOutcome(receipt.commandId)
            assertEquals(PeerCommandProgress.Idle, coordinator.commandProgress.value)
        }
        coordinator.close()
    }

    @Test
    fun `slow compatibility result observer cannot block authoritative inbound frames`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        var id = 0
        var installedRevision = -1L
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, revision ->
                installedRevision = revision
                true
            },
            idGenerator = { "peer-command-${(++id).toString().padStart(16, '0')}" },
        )
        val observerEntered = CompletableDeferred<Unit>()
        val releaseObserver = CompletableDeferred<Unit>()
        val observer = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.results.collect {
                observerEntered.complete(Unit)
                releaseObserver.await()
            }
        }

        repeat(3) { index ->
            val receipt = (coordinator.submit(byteArrayOf(index.toByte())) as Result.Success).data
            room.receive(
                HostMessage.CommandResult(
                    header = header(sequence = index.toLong() + 1L),
                    commandId = receipt.commandId,
                    status = CommandStatus.Applied,
                    authoritativeRevision = index.toLong(),
                    nextExpectedClientSequence = index.toLong() + 2L,
                ),
            )
            runCurrent()
            val resolved = assertIs<PeerCommandProgress.Resolved>(
                coordinator.commandProgress.value,
            )
            assertEquals(receipt.commandId, resolved.outcome.commandId)
            coordinator.acknowledgeCommandOutcome(receipt.commandId)
        }
        observerEntered.await()
        room.receive(snapshot(revision = 3L, sequence = 4L, public = 1, private = 2))
        runCurrent()

        assertEquals(3L, installedRevision)
        releaseObserver.complete(Unit)
        observer.cancel()
        coordinator.close()
    }

    @Test
    fun `close rejects stale submissions and wipes retained command payload`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { COMMAND_ONE },
        )
        val callerPayload = byteArrayOf(7, 8, 9)
        assertIs<Result.Success<PeerCommandReceipt>>(coordinator.submit(callerPayload))
        val retained = assertIs<PeerMessage.ClientCommand>(room.sentToHost.single())

        coordinator.close()

        assertTrue(retained.payload.all { it == 0.toByte() })
        assertTrue(callerPayload.contentEquals(byteArrayOf(7, 8, 9)))
        assertEquals(
            NetError.NotConnected,
            (coordinator.submit(byteArrayOf(1)) as Result.Failure).error,
        )
    }

    @Test
    fun `peer marks failed send ambiguous without replaying or releasing command`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        room.sendToHostError = NetError.Timeout
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { COMMAND_ONE },
        )

        assertTrue(coordinator.submit(byteArrayOf(1)) is Result.Failure)

        val awaiting = coordinator.commandProgress.value as PeerCommandProgress.Awaiting
        assertEquals(COMMAND_ONE, awaiting.receipt.commandId)
        assertEquals(PeerCommandDelivery.Ambiguous, awaiting.delivery)
        assertEquals(
            NetError.CommandInFlight,
            (coordinator.submit(byteArrayOf(2)) as Result.Failure).error,
        )
        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
        coordinator.close()
    }

    @Test
    fun `peer automatically reconciles a lost result without replaying the action`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { COMMAND_ONE },
            outcomeInitialRetryMs = 10L,
            outcomeMaxRetryMs = 20L,
            outcomeDeadlineMs = 100L,
        )

        assertTrue(coordinator.submit(byteArrayOf(1)) is Result.Success)
        room.sentToHost.clear()
        advanceTimeBy(10L)
        runCurrent()

        assertEquals(0, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.CommandOutcomeRequest>().size)

        room.receive(
            HostMessage.CommandResult(
                header = header(sequence = 1L),
                commandId = COMMAND_ONE,
                status = CommandStatus.Applied,
                authoritativeRevision = 1L,
                nextExpectedClientSequence = 2L,
            ),
        )
        runCurrent()
        val requestsAfterResolution =
            room.sentToHost.filterIsInstance<PeerMessage.CommandOutcomeRequest>().size
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(
            requestsAfterResolution,
            room.sentToHost.filterIsInstance<PeerMessage.CommandOutcomeRequest>().size,
        )
        assertIs<PeerCommandProgress.Resolved>(coordinator.commandProgress.value)
        coordinator.close()
    }

    @Test
    fun `outcome lookup cannot overtake the original command write`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val writeGate = CompletableDeferred<Unit>()
        room.commandSendGate = writeGate
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { COMMAND_ONE },
            outcomeInitialRetryMs = 10L,
            outcomeMaxRetryMs = 20L,
            outcomeDeadlineMs = 100L,
        )

        val submission = async { coordinator.submit(byteArrayOf(1)) }
        runCurrent()
        advanceTimeBy(50L)
        runCurrent()

        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
        assertTrue(room.sentToHost.none { it is PeerMessage.CommandOutcomeRequest })

        writeGate.complete(Unit)
        assertTrue(submission.await() is Result.Success)
        advanceTimeBy(10L)
        runCurrent()

        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.CommandOutcomeRequest>().size)
        coordinator.close()
    }

    @Test
    fun `caller cancellation during command write propagates and starts query-only recovery`() =
        runTest {
            val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
            room.commandSendGate = CompletableDeferred()
            val coordinator = PeerAuthoritativeSessionCoordinator(
                room = room,
                protocol = protocol,
                selfPlayerId = peerId,
                scope = this,
                onSnapshot = { _, _ -> true },
                idGenerator = { COMMAND_ONE },
                outcomeInitialRetryMs = 10L,
                outcomeMaxRetryMs = 20L,
                outcomeDeadlineMs = 100L,
            )

            val submission = async { coordinator.submit(byteArrayOf(1)) }
            runCurrent()
            submission.cancel()
            assertFailsWith<CancellationException> { submission.await() }
            advanceTimeBy(10L)
            runCurrent()

            assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
            assertEquals(
                1,
                room.sentToHost.filterIsInstance<PeerMessage.CommandOutcomeRequest>().size,
            )
            coordinator.close()
        }

    @Test
    fun `outcome reconciliation has a deadline and reports recovery timeout`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { COMMAND_ONE },
            outcomeInitialRetryMs = 10L,
            outcomeMaxRetryMs = 20L,
            outcomeDeadlineMs = 55L,
        )

        assertTrue(coordinator.submit(byteArrayOf(1)) is Result.Success)
        advanceTimeBy(56L)
        runCurrent()

        val awaiting = assertIs<PeerCommandProgress.Awaiting>(coordinator.commandProgress.value)
        assertEquals(PeerCommandDelivery.RecoveryTimedOut, awaiting.delivery)
        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
        assertEquals(3, room.sentToHost.filterIsInstance<PeerMessage.CommandOutcomeRequest>().size)
        assertEquals(
            NetError.CommandInFlight,
            (coordinator.submit(byteArrayOf(2)) as Result.Failure).error,
        )
        coordinator.close()
    }

    @Test
    fun `authenticated host traffic restarts timed out outcome lookup without replay`() = runTest {
        val room = RecordingRoom(isHost = false, selfPlayerId = peerId)
        val coordinator = PeerAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            selfPlayerId = peerId,
            scope = this,
            onSnapshot = { _, _ -> true },
            idGenerator = { COMMAND_ONE },
            outcomeInitialRetryMs = 10L,
            outcomeMaxRetryMs = 20L,
            outcomeDeadlineMs = 55L,
        )

        assertTrue(coordinator.submit(byteArrayOf(1)) is Result.Success)
        advanceTimeBy(56L)
        runCurrent()
        assertEquals(
            PeerCommandDelivery.RecoveryTimedOut,
            assertIs<PeerCommandProgress.Awaiting>(coordinator.commandProgress.value).delivery,
        )
        val requestsBeforeHeartbeat =
            room.sentToHost.filterIsInstance<PeerMessage.CommandOutcomeRequest>().size

        room.receive(
            HostMessage.Heartbeat(
                header = header(sequence = 99L),
                authoritativeRevision = 0L,
            ),
        )
        runCurrent()
        advanceTimeBy(10L)
        runCurrent()

        assertEquals(1, room.sentToHost.filterIsInstance<PeerMessage.ClientCommand>().size)
        assertTrue(
            room.sentToHost.filterIsInstance<PeerMessage.CommandOutcomeRequest>().size >
                requestsBeforeHeartbeat,
        )
        assertEquals(
            PeerCommandDelivery.Reconciling,
            assertIs<PeerCommandProgress.Awaiting>(coordinator.commandProgress.value).delivery,
        )

        room.receive(
            HostMessage.CommandResult(
                header = header(sequence = 100L),
                commandId = COMMAND_ONE,
                status = CommandStatus.Applied,
                authoritativeRevision = 1L,
                nextExpectedClientSequence = 2L,
            ),
        )
        runCurrent()

        assertIs<PeerCommandProgress.Resolved>(coordinator.commandProgress.value)
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
            onSnapshot = { _, _ -> true },
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
            onSnapshot = { _, _ -> true },
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
            requireStartHandshake = false,
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
    fun `host gameplay mutation is suspended while lifecycle mutation remains ordered`() = runTest {
        val room = RecordingRoom(isHost = true, selfPlayerId = PlayerId("host"))
        var domainValue = 0
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = room,
            protocol = protocol,
            remotePlayers = setOf(peerId),
            scope = this,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(domainValue.toByte()), byteArrayOf()) },
            heartbeatIntervalMs = 0,
            requireStartHandshake = false,
        )
        room.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)

        val gameplay = coordinator.applyHostMutation {
            domainValue += 100
            true
        }
        val lifecycle = coordinator.applyLifecycleMutation {
            domainValue += 1
            true
        }

        assertEquals(HostMutationResult.Suspended, gameplay)
        assertEquals(HostMutationResult.Applied, lifecycle)
        assertEquals(1, domainValue)
        assertEquals(1L, coordinator.revision.value)

        room.lifecycleState.value = RoomLifecycleState.Expired
        assertEquals(
            HostMutationResult.NotStarted,
            coordinator.applyLifecycleMutation {
                domainValue += 10
                true
            },
        )
        assertEquals(1, domainValue)
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
            onSnapshot = { _, _ -> true },
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
    private val inbox = Channel<RoomMessage>(capacity = 64)
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
    var commandSendGate: CompletableDeferred<Unit>? = null
    var sendFailure: Throwable? = null

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
        sendFailure?.let { throw it }
        sent += SentMessage(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        sentToHost += message
        if (message is PeerMessage.ClientCommand) commandSendGate?.await()
        val error = sendToHostError
        return if (error == null) Result.Success(Unit) else Result.Failure(error)
    }

    override suspend fun leave() = Unit
}
