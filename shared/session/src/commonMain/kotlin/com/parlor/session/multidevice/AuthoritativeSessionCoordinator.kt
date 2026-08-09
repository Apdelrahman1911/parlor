package com.parlor.session.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolValidation
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.protocol.validateFor
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.SendTarget
import com.parlor.networking.security.SecureIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Wire-ready public and player-private state captured from one domain revision. */
data class PlayerSnapshotPayload(
    val publicPayload: ByteArray,
    val privatePayload: ByteArray,
)

sealed interface CommandApplication {
    data object Applied : CommandApplication
    data object InvalidAction : CommandApplication
    data object Unauthorized : CommandApplication
}

/** Result of serializing a host-originated domain mutation with peer commands. */
enum class HostMutationResult {
    Applied,
    Unchanged,
    Closed,
}

/**
 * Transport-independent single-writer coordinator for a host-authoritative
 * game. All remote commands, host-originated state publications, resyncs, and
 * terminal messages pass through one mailbox.
 */
class HostAuthoritativeSessionCoordinator(
    private val room: LocalRoom,
    val protocol: SessionProtocol,
    private val remotePlayers: Set<PlayerId>,
    private val scope: CoroutineScope,
    private val applyCommand: suspend (actor: PlayerId, payload: ByteArray) -> CommandApplication,
    private val snapshotFor: suspend (playerId: PlayerId) -> PlayerSnapshotPayload,
    private val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS,
    private val idGenerator: () -> String = SecureIds::id128,
) {
    private sealed interface Work {
        data class Incoming(val message: PeerMessage) : Work
        data class Publish(val incrementRevision: Boolean) : Work
        data class HostMutation(
            val apply: suspend () -> Boolean,
            val completion: CompletableDeferred<HostMutationResult>,
        ) : Work
        data class End(
            val reason: SessionEndReason,
            val completion: CompletableDeferred<Unit>,
        ) : Work
        data object Heartbeat : Work
    }

    private val mailbox = Channel<Work>(HOST_MAILBOX_CAPACITY)
    private val nextClientSequence = mutableMapOf<PlayerId, Long>()
    private data class CommandKey(val actor: PlayerId, val commandId: String)
    private val commandResults = mutableMapOf<CommandKey, HostMessage.CommandResult>()
    private val commandOrder = ArrayDeque<CommandKey>()
    private var hostSequence: Long = 0L

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val jobs = mutableListOf<Job>()

    init {
        jobs += scope.launch {
            room.incoming.collect { message ->
                if (message is PeerMessage) mailbox.send(Work.Incoming(message))
            }
        }
        jobs += scope.launch {
            for (work in mailbox) {
                when (work) {
                    is Work.Incoming -> processIncoming(work.message)
                    is Work.Publish -> publishSnapshots(work.incrementRevision)
                    is Work.HostMutation -> processHostMutation(work)
                    is Work.End -> {
                        sendEnd(work.reason)
                        work.completion.complete(Unit)
                    }
                    Work.Heartbeat -> sendHeartbeat()
                }
            }
        }
        if (heartbeatIntervalMs > 0L) {
            jobs += scope.launch {
                while (true) {
                    delay(heartbeatIntervalMs)
                    mailbox.send(Work.Heartbeat)
                }
            }
        }
    }

    /**
     * Publish the latest domain state. Host UI/timer mutations call this with
     * the default increment; initial startup uses `false` at revision zero.
     */
    suspend fun publishState(incrementRevision: Boolean = true) {
        mailbox.send(Work.Publish(incrementRevision))
    }

    /**
     * Runs a host UI/lifecycle mutation on the same single-writer queue as
     * remote commands. This keeps the domain state and authoritative protocol
     * revision in one order even when host and peer actions arrive together.
     */
    suspend fun applyHostMutation(apply: suspend () -> Boolean): HostMutationResult {
        val completion = CompletableDeferred<HostMutationResult>()
        try {
            mailbox.send(Work.HostMutation(apply, completion))
        } catch (_: ClosedSendChannelException) {
            return HostMutationResult.Closed
        }
        return completion.await()
    }

    suspend fun end(reason: SessionEndReason) {
        val completion = CompletableDeferred<Unit>()
        mailbox.send(Work.End(reason, completion))
        completion.await()
    }

    fun close() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        mailbox.close()
    }

    private suspend fun processIncoming(message: PeerMessage) {
        when (message) {
            is PeerMessage.ClientCommand -> processCommand(message)
            is PeerMessage.SnapshotRequest -> {
                if (
                    message.actor in remotePlayers &&
                    message.validateFor(protocol) == ProtocolValidation.Valid
                ) {
                    sendSnapshot(message.actor)
                }
            }
            is PeerMessage.SessionHeartbeat -> {
                if (
                    message.actor in remotePlayers &&
                    message.validateFor(protocol) == ProtocolValidation.Valid &&
                    message.lastAppliedRevision < _revision.value
                ) {
                    sendSnapshot(message.actor)
                }
            }
            is PeerMessage.CommandOutcomeRequest -> processOutcomeRequest(message)
            else -> Unit
        }
    }

    private suspend fun processHostMutation(work: Work.HostMutation) {
        try {
            val changed = work.apply()
            if (changed) {
                _revision.value += 1L
                publishSnapshots(incrementRevision = false)
            }
            work.completion.complete(
                if (changed) HostMutationResult.Applied else HostMutationResult.Unchanged,
            )
        } catch (cancelled: CancellationException) {
            work.completion.cancel(cancelled)
            throw cancelled
        } catch (failure: Throwable) {
            work.completion.completeExceptionally(failure)
        }
    }

    private suspend fun processOutcomeRequest(request: PeerMessage.CommandOutcomeRequest) {
        if (
            request.actor !in remotePlayers ||
            request.validateFor(protocol) != ProtocolValidation.Valid
        ) {
            return
        }
        val recorded = commandResults[CommandKey(request.actor, request.commandId)]
        if (recorded == null) {
            sendResult(
                actor = request.actor,
                commandId = request.commandId,
                status = CommandStatus.UnknownCommand,
                remember = false,
            )
        } else {
            sendResult(
                actor = request.actor,
                commandId = request.commandId,
                status = recorded.status,
                authoritativeRevision = recorded.authoritativeRevision,
                nextExpectedClientSequence = recorded.nextExpectedClientSequence,
                remember = false,
            )
        }
    }

    private suspend fun processCommand(command: PeerMessage.ClientCommand) {
        val actor = command.actor
        if (room.lifecycle.value != RoomLifecycleState.Active) {
            sendResult(actor, command.commandId, CommandStatus.SessionSuspended, remember = false)
            return
        }
        val validation = command.validateFor(protocol)
        if (validation != ProtocolValidation.Valid) {
            val status = when (validation) {
                ProtocolValidation.IncompatibleProtocol,
                ProtocolValidation.IncompatibleGameVersion,
                ProtocolValidation.WrongGame,
                ProtocolValidation.WrongSession -> CommandStatus.IncompatibleVersion
                ProtocolValidation.CommandPayloadTooLarge -> CommandStatus.PayloadTooLarge
                else -> CommandStatus.InvalidAction
            }
            sendResult(actor, command.commandId, status, remember = false)
            return
        }
        if (actor !in remotePlayers) {
            sendResult(actor, command.commandId, CommandStatus.Unauthorized, remember = false)
            return
        }

        val commandKey = CommandKey(actor, command.commandId)
        val cached = commandResults[commandKey]
        if (cached != null) {
            sendResult(
                actor = actor,
                commandId = command.commandId,
                status = cached.status,
                authoritativeRevision = cached.authoritativeRevision,
                nextExpectedClientSequence = cached.nextExpectedClientSequence,
                remember = false,
            )
            return
        }

        val expectedSequence = nextClientSequence[actor] ?: 1L
        if (command.clientSequence != expectedSequence) {
            val status = if (command.clientSequence < expectedSequence) {
                CommandStatus.Duplicate
            } else {
                CommandStatus.SequenceGap
            }
            sendResult(actor, command.commandId, status, remember = false)
            sendSnapshot(actor)
            return
        }
        nextClientSequence[actor] = expectedSequence + 1L

        if (command.expectedRevision != _revision.value) {
            sendResult(actor, command.commandId, CommandStatus.StaleRevision, remember = true)
            sendSnapshot(actor)
            return
        }

        when (applyCommand(actor, command.payload)) {
            CommandApplication.Applied -> {
                _revision.value += 1L
                sendResult(actor, command.commandId, CommandStatus.Applied, remember = true)
                publishSnapshots(incrementRevision = false)
            }
            CommandApplication.InvalidAction ->
                sendResult(actor, command.commandId, CommandStatus.InvalidAction, remember = true)
            CommandApplication.Unauthorized ->
                sendResult(actor, command.commandId, CommandStatus.Unauthorized, remember = true)
        }
    }

    private suspend fun publishSnapshots(incrementRevision: Boolean) {
        if (incrementRevision) _revision.value += 1L
        remotePlayers.forEach { playerId -> sendSnapshot(playerId) }
    }

    private suspend fun sendSnapshot(playerId: PlayerId) {
        if (playerId !in remotePlayers) return
        val snapshot = snapshotFor(playerId)
        val message = HostMessage.PlayerSnapshot(
            header = nextHeader(),
            revision = _revision.value,
            publicPayload = snapshot.publicPayload,
            privatePayload = snapshot.privatePayload,
        )
        check(message.validateFor(protocol) == ProtocolValidation.Valid) {
            "Snapshot exceeds the protocol limit or has invalid metadata"
        }
        room.send(SendTarget.Direct(playerId), message)
    }

    private suspend fun sendResult(
        actor: PlayerId,
        commandId: String,
        status: CommandStatus,
        remember: Boolean,
        authoritativeRevision: Long = _revision.value,
        nextExpectedClientSequence: Long = nextClientSequence[actor] ?: 1L,
    ) {
        if (actor !in remotePlayers) return
        val result = HostMessage.CommandResult(
            header = nextHeader(),
            commandId = commandId,
            status = status,
            authoritativeRevision = authoritativeRevision,
            nextExpectedClientSequence = nextExpectedClientSequence,
        )
        when (val validation = result.validateFor(protocol)) {
            ProtocolValidation.Valid -> Unit
            // The command id is supplied by an untrusted peer. There is no
            // safe correlation id for a rejection when it is malformed, so
            // drop it instead of crashing the host's single-writer loop.
            ProtocolValidation.InvalidMessageId -> return
            else -> error("Host generated an invalid command result: $validation")
        }
        if (remember) remember(actor, result)
        room.send(SendTarget.Direct(actor), result)
    }

    private fun remember(actor: PlayerId, result: HostMessage.CommandResult) {
        val key = CommandKey(actor, result.commandId)
        commandResults[key] = result
        commandOrder.addLast(key)
        while (commandOrder.size > MAX_REMEMBERED_COMMANDS) {
            commandResults.remove(commandOrder.removeFirst())
        }
    }

    private suspend fun sendHeartbeat() {
        val message = HostMessage.Heartbeat(
            header = nextHeader(),
            authoritativeRevision = _revision.value,
        )
        check(message.validateFor(protocol) == ProtocolValidation.Valid)
        room.send(SendTarget.Broadcast, message)
    }

    private suspend fun sendEnd(reason: SessionEndReason) {
        val message = HostMessage.SessionEnded(
            header = nextHeader(),
            reason = reason,
            finalRevision = _revision.value,
        )
        check(message.validateFor(protocol) == ProtocolValidation.Valid)
        room.send(SendTarget.Broadcast, message)
    }

    private fun nextHeader(): SessionEnvelopeHeader = SessionEnvelopeHeader(
        protocol = protocol.protocol,
        sessionId = protocol.sessionId,
        gameId = protocol.gameId,
        gameVersion = protocol.gameVersion,
        messageId = idGenerator(),
        sequence = ++hostSequence,
        connectionEpoch = protocol.connectionEpoch,
    )

    private companion object {
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000L
        const val HOST_MAILBOX_CAPACITY = 8
        const val MAX_REMEMBERED_COMMANDS = 256
    }
}

data class PeerCommandReceipt(
    val commandId: String,
    val clientSequence: Long,
)

/**
 * Peer-side ordering, retry, and snapshot validation. It never reduces game
 * state; [onSnapshot] installs the host's atomic player projection.
 */
class PeerAuthoritativeSessionCoordinator(
    private val room: LocalRoom,
    val protocol: SessionProtocol,
    private val selfPlayerId: PlayerId,
    private val scope: CoroutineScope,
    private val onSnapshot: suspend (PlayerSnapshotPayload, revision: Long) -> Unit,
    /**
     * Called from this coordinator's single room-inbox collector after a
     * valid terminal envelope is received. Keeping terminal delivery
     * here avoids racing a second collector against Channel-backed transports.
     */
    private val onSessionEnded: suspend (HostMessage.SessionEnded) -> Unit = {},
    /**
     * Surfaces malformed or incompatible authoritative envelopes. Callers can
     * fail closed instead of silently rendering stale state.
     */
    private val onProtocolViolation: suspend (ProtocolValidation) -> Unit = {},
    private val idGenerator: () -> String = SecureIds::id128,
) {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _results = MutableSharedFlow<HostMessage.CommandResult>(extraBufferCapacity = 16)
    val results: SharedFlow<HostMessage.CommandResult> = _results.asSharedFlow()

    private val pending = linkedMapOf<String, PeerMessage.ClientCommand>()
    private var nextClientSequence = 1L
    private var lastInstalledSnapshotRevision = -1L
    private var terminalAccepted = false
    private val seenHostMessageIds = mutableSetOf<String>()
    private val seenHostMessageOrder = ArrayDeque<String>()
    private val stateMutex = Mutex()
    private val jobs = mutableListOf<Job>()

    init {
        jobs += scope.launch {
            room.incoming.collect { message ->
                when (message) {
                    is HostMessage.PlayerSnapshot -> acceptSnapshot(message)
                    is HostMessage.CommandResult -> acceptResult(message)
                    is HostMessage.Heartbeat -> acceptHeartbeat(message)
                    is HostMessage.SessionEnded -> acceptSessionEnded(message)
                    else -> Unit
                }
            }
        }
        jobs += scope.launch {
            room.peerEvents.collect { event ->
                if (event == com.parlor.networking.room.PeerEvent.HostRestored) {
                    requestSnapshot()
                    requestPendingOutcomes()
                }
            }
        }
    }

    suspend fun submit(payload: ByteArray): Result<PeerCommandReceipt, NetError> {
        if (room.lifecycle.value != RoomLifecycleState.Active) {
            return Result.Failure(NetError.SessionSuspended)
        }
        if (payload.size > com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES) {
            return Result.Failure(NetError.PayloadTooLarge)
        }
        val command = stateMutex.withLock {
            if (pending.isNotEmpty()) return Result.Failure(NetError.CommandInFlight)
            val commandId = idGenerator()
            val clientSequence = nextClientSequence++
            PeerMessage.ClientCommand(
                header = peerHeader(commandId),
                actor = selfPlayerId,
                commandId = commandId,
                clientSequence = clientSequence,
                expectedRevision = _revision.value,
                payload = payload.copyOf(),
            ).also { pending[commandId] = it }
        }
        return when (val sent = room.sendToHost(command)) {
            is Result.Success -> Result.Success(
                PeerCommandReceipt(command.commandId, command.clientSequence),
            )
            is Result.Failure -> Result.Failure(sent.error)
        }
    }

    suspend fun requestSnapshot(): Result<Unit, NetError> {
        val revision = stateMutex.withLock { _revision.value }
        return room.sendToHost(
            PeerMessage.SnapshotRequest(
                header = peerHeader(idGenerator()),
                actor = selfPlayerId,
                lastAppliedRevision = revision,
            ),
        )
    }

    fun close() {
        jobs.forEach(Job::cancel)
        jobs.clear()
    }

    private suspend fun acceptSnapshot(snapshot: HostMessage.PlayerSnapshot) {
        val validation = snapshot.validateFor(protocol)
        if (validation != ProtocolValidation.Valid) {
            onProtocolViolation(validation)
            return
        }
        val accepted = stateMutex.withLock {
            if (
                terminalAccepted ||
                !rememberHostMessage(snapshot.header.messageId) ||
                snapshot.revision <= lastInstalledSnapshotRevision
            ) {
                false
            } else {
                lastInstalledSnapshotRevision = snapshot.revision
                _revision.value = snapshot.revision
                true
            }
        }
        if (!accepted) return
        onSnapshot(
            PlayerSnapshotPayload(
                publicPayload = snapshot.publicPayload.copyOf(),
                privatePayload = snapshot.privatePayload.copyOf(),
            ),
            snapshot.revision,
        )
    }

    private suspend fun acceptResult(result: HostMessage.CommandResult) {
        val validation = result.validateFor(protocol)
        if (validation != ProtocolValidation.Valid) {
            onProtocolViolation(validation)
            return
        }
        val accepted = stateMutex.withLock {
            if (
                terminalAccepted ||
                !rememberHostMessage(result.header.messageId) ||
                result.commandId !in pending
            ) {
                false
            } else {
                nextClientSequence = result.nextExpectedClientSequence
                pending.remove(result.commandId)
                true
            }
        }
        if (!accepted) return
        _results.tryEmit(result)
        if (
            result.status == CommandStatus.SequenceGap ||
            result.status == CommandStatus.StaleRevision
        ) {
            requestSnapshot()
        }
    }

    private suspend fun acceptHeartbeat(heartbeat: HostMessage.Heartbeat) {
        val validation = heartbeat.validateFor(protocol)
        if (validation != ProtocolValidation.Valid) {
            onProtocolViolation(validation)
            return
        }
        val needsSnapshot = stateMutex.withLock {
            if (
                terminalAccepted ||
                !rememberHostMessage(heartbeat.header.messageId)
            ) {
                false
            } else {
                heartbeat.authoritativeRevision > _revision.value
            }
        }
        if (needsSnapshot) requestSnapshot()
    }

    private suspend fun acceptSessionEnded(ended: HostMessage.SessionEnded) {
        val validation = ended.validateFor(protocol)
        if (validation != ProtocolValidation.Valid) {
            onProtocolViolation(validation)
            return
        }
        val accepted = stateMutex.withLock {
            if (
                terminalAccepted ||
                !rememberHostMessage(ended.header.messageId) ||
                ended.finalRevision < _revision.value
            ) {
                false
            } else {
                terminalAccepted = true
                _revision.value = ended.finalRevision
                pending.clear()
                true
            }
        }
        if (!accepted) return
        onSessionEnded(ended)
    }

    /**
     * Host sequence numbers are global across broadcasts and all direct
     * recipients, so a single peer legitimately observes gaps and reordering.
     * Replay protection is therefore message-id based; each message type then
     * applies its own revision/command-id idempotency rule.
     */
    private fun rememberHostMessage(messageId: String): Boolean {
        if (!seenHostMessageIds.add(messageId)) return false
        seenHostMessageOrder.addLast(messageId)
        while (seenHostMessageOrder.size > MAX_SEEN_HOST_MESSAGES) {
            seenHostMessageIds.remove(seenHostMessageOrder.removeFirst())
        }
        return true
    }

    /**
     * Reconcile an ambiguous local send after reconnect without replaying a
     * potentially non-idempotent game action.
     */
    suspend fun requestPendingOutcomes(): Result<Unit, NetError> {
        val commands = stateMutex.withLock { pending.values.toList() }
        for (command in commands) {
            val request = PeerMessage.CommandOutcomeRequest(
                header = peerHeader(command.commandId),
                actor = selfPlayerId,
                commandId = command.commandId,
            )
            when (val sent = room.sendToHost(request)) {
                is Result.Success -> Unit
                is Result.Failure -> return Result.Failure(sent.error)
            }
        }
        return Result.Success(Unit)
    }

    private fun peerHeader(messageId: String): SessionEnvelopeHeader = SessionEnvelopeHeader(
        protocol = protocol.protocol,
        sessionId = protocol.sessionId,
        gameId = protocol.gameId,
        gameVersion = protocol.gameVersion,
        messageId = messageId,
        sequence = 0L,
        connectionEpoch = protocol.connectionEpoch,
    )

    private companion object {
        const val MAX_SEEN_HOST_MESSAGES = 2_048
    }
}
