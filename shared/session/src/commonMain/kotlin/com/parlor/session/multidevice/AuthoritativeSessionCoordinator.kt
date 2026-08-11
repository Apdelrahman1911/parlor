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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

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
    Suspended,
    NotStarted,
    Closed,
}

enum class HostSessionStartState {
    NotRequired,
    Waiting,
    Started,
    Failed,
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
    private val requireStartHandshake: Boolean = true,
    private val startSendTimeoutMs: Long = DEFAULT_START_FRAME_SEND_TIMEOUT_MS,
    private val outboundSendTimeoutMs: Long = DEFAULT_OUTBOUND_SEND_TIMEOUT_MS,
) {
    private sealed interface Work {
        data class Incoming(val message: PeerMessage) : Work
        data class Publish(val incrementRevision: Boolean) : Work
        data class HostMutation(
            val apply: suspend () -> Boolean,
            val requiresActiveRoom: Boolean,
            val completion: CompletableDeferred<HostMutationResult>,
        ) : Work
        data class End(
            val reason: SessionEndReason,
            val completion: CompletableDeferred<Unit>,
        ) : Work
        data class BeginStart(
            val offer: HostMessage.SessionStarting,
            val initialRetryMs: Long,
            val maxRetryMs: Long,
            val deadlineMs: Long,
            val completion: CompletableDeferred<Result<HostMessage.SessionStarting, NetError>>,
        ) : Work
        data class RetryStart(val startId: String) : Work
        data class StartDeadline(val startId: String, val phase: StartPhase) : Work
        data class AbortStart(val startId: String) : Work
        data class ResendStart(
            val playerId: PlayerId,
            val initialRetryMs: Long,
            val maxRetryMs: Long,
            val readyDeadlineMs: Long,
            val commitAckDeadlineMs: Long,
            val completion: CompletableDeferred<Result<Unit, NetError>>,
        ) : Work
        data class RetryResendStart(
            val playerId: PlayerId,
            val completion: CompletableDeferred<Result<Unit, NetError>>,
        ) : Work
        data class ResendStartDeadline(
            val playerId: PlayerId,
            val phase: StartPhase,
            val completion: CompletableDeferred<Result<Unit, NetError>>,
        ) : Work
        data class AbortResendStart(
            val playerId: PlayerId,
            val completion: CompletableDeferred<Result<Unit, NetError>>,
        ) : Work
        data object Heartbeat : Work
    }

    private enum class StartPhase { AwaitingReady, AwaitingCommitAck }

    private data class StartAttempt(
        val offer: HostMessage.SessionStarting,
        val commit: HostMessage.SessionStartCommitted,
        val completion: CompletableDeferred<Result<HostMessage.SessionStarting, NetError>>,
        val initialRetryMs: Long,
        val maxRetryMs: Long,
        val deadlineMs: Long,
        var nextRetryMs: Long,
        val awaiting: MutableSet<PlayerId>,
        var phase: StartPhase = StartPhase.AwaitingReady,
        var retryJob: Job? = null,
        var deadlineJob: Job? = null,
        var sendJob: Job? = null,
    )

    /** One reconnecting seat's replay of the already-committed start barrier. */
    private data class ResendStartAttempt(
        val playerId: PlayerId,
        val offer: HostMessage.SessionStarting,
        val commit: HostMessage.SessionStartCommitted,
        val completion: CompletableDeferred<Result<Unit, NetError>>,
        val initialRetryMs: Long,
        val maxRetryMs: Long,
        val readyDeadlineMs: Long,
        val commitAckDeadlineMs: Long,
        var nextRetryMs: Long = initialRetryMs,
        var phase: StartPhase = StartPhase.AwaitingReady,
        var retryJob: Job? = null,
        var deadlineJob: Job? = null,
        var sendJob: Job? = null,
    )

    private val mailbox = Channel<Work>(HOST_MAILBOX_CAPACITY)
    private val nextClientSequence = mutableMapOf<PlayerId, Long>()
    private val commandResultsByActor =
        mutableMapOf<PlayerId, LinkedHashMap<String, HostMessage.CommandResult>>()
    private var hostSequence: Long = 0L
    private var startAttempt: StartAttempt? = null
    private var completedStart: Pair<HostMessage.SessionStarting, HostMessage.SessionStartCommitted>? = null
    private val resendStartAttempts = mutableMapOf<PlayerId, ResendStartAttempt>()
    private var ended = false

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()
    private val _startState = MutableStateFlow(
        if (requireStartHandshake) HostSessionStartState.Waiting
        else HostSessionStartState.NotRequired,
    )
    val startState: StateFlow<HostSessionStartState> = _startState.asStateFlow()

    private val coordinatorJob = SupervisorJob(scope.coroutineContext[Job])
    private val coordinatorScope = CoroutineScope(scope.coroutineContext + coordinatorJob)
    private val outboundByPlayer = remotePlayers.associateWith { playerId ->
        BoundedPeerOutbox(
            playerId = playerId,
            room = room,
            scope = coordinatorScope,
            sendTimeoutMs = outboundSendTimeoutMs,
        )
    }
    private val jobs = mutableListOf<Job>()
    private val closeMutex = Mutex()
    private var closed = false

    init {
        require(startSendTimeoutMs > 0L) { "startSendTimeoutMs must be positive" }
        require(outboundSendTimeoutMs > 0L) { "outboundSendTimeoutMs must be positive" }
        coordinatorJob.invokeOnCompletion {
            mailbox.close()
            drainPendingWork(CancellationException("Host coordinator parent scope closed"))
        }
        jobs += coordinatorScope.launch {
            room.incoming.collect { message ->
                if (message is PeerMessage) mailbox.send(Work.Incoming(message))
            }
        }
        jobs += coordinatorScope.launch {
            for (work in mailbox) {
                try {
                    when (work) {
                        is Work.Incoming -> processIncoming(work.message)
                        is Work.Publish -> {
                            if (canProcessGameTraffic()) publishSnapshots(work.incrementRevision)
                        }
                        is Work.HostMutation -> processHostMutation(work)
                        is Work.End -> processEnd(work)
                        is Work.BeginStart -> processBeginStart(work)
                        is Work.RetryStart -> processStartRetry(work.startId)
                        is Work.StartDeadline -> processStartDeadline(work.startId, work.phase)
                        is Work.AbortStart -> processStartAbort(work.startId)
                        is Work.ResendStart -> processStartResend(work)
                        is Work.RetryResendStart -> processResendStartRetry(work)
                        is Work.ResendStartDeadline -> processResendStartDeadline(work)
                        is Work.AbortResendStart -> processResendStartAbort(work)
                        Work.Heartbeat -> {
                            if (canProcessGameTraffic()) sendHeartbeat()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    cancelCompletion(work, cancelled)
                    throw cancelled
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    // A malformed/failed operation must not kill the sole
                    // mailbox worker and strand every later command. Work with
                    // a caller-visible completion receives the exact failure;
                    // fire-and-forget publication/inbound work is isolated and
                    // the next authoritative item remains processable.
                    failCompletion(work, failure)
                }
            }
        }
        if (heartbeatIntervalMs > 0L) {
            jobs += coordinatorScope.launch {
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
     * Starts one immutable, retryable two-phase game-start transaction.
     * Every peer must acknowledge the offer before the host irreversibly
     * commits and this returns success. Commit acknowledgements confirm
     * delivery only; losing one can never roll an already-started game back.
     * The same [HostMessage.SessionStarting.startId] is used for every retry.
     */
    suspend fun startSession(
        caseId: String,
        modeId: String,
        players: List<com.parlor.engine.state.Player>,
        sessionNonce: Long,
        caseVersion: String? = null,
        caseDigest: String? = null,
        initialRetryMs: Long = DEFAULT_START_RETRY_MS,
        maxRetryMs: Long = DEFAULT_MAX_START_RETRY_MS,
        deadlineMs: Long = DEFAULT_START_DEADLINE_MS,
    ): Result<HostMessage.SessionStarting, NetError> {
        require(initialRetryMs > 0L) { "initialRetryMs must be positive" }
        require(maxRetryMs >= initialRetryMs) { "maxRetryMs must be at least initialRetryMs" }
        require(deadlineMs > 0L) { "deadlineMs must be positive" }
        val startId = idGenerator()
        val offer = HostMessage.SessionStarting(
            startId = startId,
            caseId = caseId,
            modeId = modeId,
            players = players,
            sessionNonce = sessionNonce,
            header = SessionEnvelopeHeader(
                protocol = protocol.protocol,
                sessionId = protocol.sessionId,
                gameId = protocol.gameId,
                gameVersion = protocol.gameVersion,
                messageId = startId,
                sequence = 0L,
                connectionEpoch = protocol.connectionEpoch,
            ),
            caseVersion = caseVersion,
            caseDigest = caseDigest,
        )
        check(offer.validateFor(protocol) == ProtocolValidation.Valid) {
            "Host generated an invalid session-start offer"
        }
        val completion = CompletableDeferred<Result<HostMessage.SessionStarting, NetError>>()
        try {
            mailbox.send(
                Work.BeginStart(
                    offer = offer,
                    initialRetryMs = initialRetryMs,
                    maxRetryMs = maxRetryMs,
                    deadlineMs = deadlineMs,
                    completion = completion,
                ),
            )
            return completion.await()
        } catch (cancelled: CancellationException) {
            // Cancellation ownership is recorded on the transaction itself.
            // The best-effort mailbox nudge may be rejected when a malicious
            // inbound burst fills the bounded queue, so every worker path also
            // checks this cancellation bit before it can commit.
            completion.cancel(cancelled)
            mailbox.trySend(Work.AbortStart(startId))
            throw cancelled
        } catch (_: ClosedSendChannelException) {
            return Result.Failure(NetError.NotConnected)
        }
    }

    /** Blocks host-originated game mutation until the start transaction resolves. */
    suspend fun awaitSessionStarted(): Boolean = when (
        startState.first {
            it == HostSessionStartState.NotRequired ||
                it == HostSessionStartState.Started ||
                it == HostSessionStartState.Failed
        }
    ) {
        HostSessionStartState.NotRequired,
        HostSessionStartState.Started -> true
        HostSessionStartState.Waiting -> error("Filtered state cannot be waiting")
        HostSessionStartState.Failed -> false
    }

    /**
     * Re-establishes the committed start barrier for one resumed seat.
     *
     * Success means this exact seat answered the stable offer with Ready and
     * then acknowledged the stable commit. Each phase owns a fresh absolute
     * deadline and bounded exponential retries; another seat can progress or
     * fail independently. Cancellation is recorded on [completion] before a
     * best-effort abort is queued, so a full mailbox cannot later turn a
     * cancelled request into a successful rejoin.
     */
    suspend fun resendStart(
        playerId: PlayerId,
        initialRetryMs: Long = DEFAULT_START_RETRY_MS,
        maxRetryMs: Long = DEFAULT_MAX_START_RETRY_MS,
        readyDeadlineMs: Long = DEFAULT_START_DEADLINE_MS,
        commitAckDeadlineMs: Long = DEFAULT_START_DEADLINE_MS,
    ): Result<Unit, NetError> {
        require(initialRetryMs > 0L) { "initialRetryMs must be positive" }
        require(maxRetryMs >= initialRetryMs) { "maxRetryMs must be at least initialRetryMs" }
        require(readyDeadlineMs > 0L) { "readyDeadlineMs must be positive" }
        require(commitAckDeadlineMs > 0L) { "commitAckDeadlineMs must be positive" }
        val completion = CompletableDeferred<Result<Unit, NetError>>()
        try {
            mailbox.send(
                Work.ResendStart(
                    playerId = playerId,
                    initialRetryMs = initialRetryMs,
                    maxRetryMs = maxRetryMs,
                    readyDeadlineMs = readyDeadlineMs,
                    commitAckDeadlineMs = commitAckDeadlineMs,
                    completion = completion,
                ),
            )
            return completion.await()
        } catch (cancelled: CancellationException) {
            completion.cancel(cancelled)
            mailbox.trySend(Work.AbortResendStart(playerId, completion))
            throw cancelled
        } catch (_: ClosedSendChannelException) {
            return Result.Failure(NetError.NotConnected)
        }
    }

    /**
     * Runs a host UI/lifecycle mutation on the same single-writer queue as
     * remote commands. This keeps the domain state and authoritative protocol
     * revision in one order even when host and peer actions arrive together.
     */
    suspend fun applyHostMutation(apply: suspend () -> Boolean): HostMutationResult =
        applyHostMutation(requiresActiveRoom = true, apply = apply)

    /**
     * Serializes transport/lifecycle recovery with ordinary game commands.
     * Lifecycle mutations may run while the room is suspended, but still
     * require a started, non-terminal authoritative session.
     */
    suspend fun applyLifecycleMutation(apply: suspend () -> Boolean): HostMutationResult =
        applyHostMutation(requiresActiveRoom = false, apply = apply)

    private suspend fun applyHostMutation(
        requiresActiveRoom: Boolean,
        apply: suspend () -> Boolean,
    ): HostMutationResult {
        val completion = CompletableDeferred<HostMutationResult>()
        try {
            mailbox.send(Work.HostMutation(apply, requiresActiveRoom, completion))
            return completion.await()
        } catch (cancelled: CancellationException) {
            // A mutation cancelled before the worker starts must never execute
            // later merely because it was already queued in the mailbox.
            completion.cancel(cancelled)
            throw cancelled
        } catch (_: ClosedSendChannelException) {
            return HostMutationResult.Closed
        }
    }

    suspend fun end(reason: SessionEndReason) {
        val completion = CompletableDeferred<Unit>()
        try {
            mailbox.send(Work.End(reason, completion))
        } catch (_: ClosedSendChannelException) {
            return
        }
        completion.await()
    }

    suspend fun close() = closeMutex.withLock {
        if (closed) return@withLock
        closed = true
        // Seal producers first and resolve every queued waiter before joining
        // children. Returning only after all outboxes/collectors have stopped
        // prevents a retired session from publishing into its replacement.
        mailbox.close()
        _startState.value = HostSessionStartState.Failed
        val cancellation = CancellationException("Host coordinator closed")
        cancelStartAttempt(cancellation)
        cancelResendStartAttempts(cancellation)
        outboundByPlayer.values.forEach { it.close() }
        coordinatorJob.cancel(cancellation)
        drainPendingWork(cancellation)
        withContext(NonCancellable) { coordinatorJob.join() }
        jobs.clear()
    }

    private fun drainPendingWork(cancelled: CancellationException) {
        while (true) {
            val work = mailbox.tryReceive().getOrNull() ?: break
            cancelCompletion(work, cancelled)
        }
    }

    private suspend fun processIncoming(message: PeerMessage) {
        when (message) {
            is PeerMessage.SessionStartReady -> processStartReady(message)
            is PeerMessage.SessionStartCommitAck -> processStartCommitAck(message)
            is PeerMessage.ClientCommand -> {
                if (canProcessGameTraffic()) processCommand(message)
                else sendResult(
                    message.actor,
                    message.commandId,
                    CommandStatus.SessionSuspended,
                    remember = false,
                )
            }
            is PeerMessage.SnapshotRequest -> {
                if (
                    canProcessGameTraffic() &&
                    message.actor in remotePlayers &&
                    message.validateFor(protocol) == ProtocolValidation.Valid
                ) {
                    sendSnapshot(message.actor)
                }
            }
            is PeerMessage.SessionHeartbeat -> {
                if (
                    canProcessGameTraffic() &&
                    message.actor in remotePlayers &&
                    message.validateFor(protocol) == ProtocolValidation.Valid &&
                    message.lastAppliedRevision < _revision.value
                ) {
                    sendSnapshot(message.actor)
                }
            }
            is PeerMessage.CommandOutcomeRequest -> {
                if (canProcessGameTraffic()) processOutcomeRequest(message)
            }
            else -> Unit
        }
    }

    private suspend fun processHostMutation(work: Work.HostMutation) {
        try {
            if (work.completion.isCancelled) return
            if (!canProcessGameTraffic()) {
                work.completion.complete(HostMutationResult.NotStarted)
                return
            }
            if (
                work.requiresActiveRoom &&
                room.lifecycle.value != RoomLifecycleState.Active
            ) {
                work.completion.complete(HostMutationResult.Suspended)
                return
            }
            if (
                !work.requiresActiveRoom &&
                room.lifecycle.value.let {
                    it == RoomLifecycleState.Expired || it == RoomLifecycleState.Closed
                }
            ) {
                work.completion.complete(HostMutationResult.NotStarted)
                return
            }
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            work.completion.completeExceptionally(failure)
        }
    }

    private suspend fun processEnd(work: Work.End) {
        try {
            if (!ended) {
                ended = true
                _startState.value = HostSessionStartState.Failed
                failActiveStart(NetError.NotConnected)
                failResendStartAttempts(NetError.NotConnected)
                sendEnd(work.reason)
            }
            work.completion.complete(Unit)
        } catch (cancelled: CancellationException) {
            work.completion.cancel(cancelled)
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            work.completion.completeExceptionally(failure)
        }
    }

    private fun cancelCompletion(work: Work, cancelled: CancellationException) {
        when (work) {
            is Work.HostMutation -> work.completion.cancel(cancelled)
            is Work.End -> work.completion.cancel(cancelled)
            is Work.BeginStart -> work.completion.cancel(cancelled)
            is Work.ResendStart -> work.completion.cancel(cancelled)
            is Work.Incoming,
            is Work.Publish,
            is Work.RetryStart,
            is Work.StartDeadline,
            is Work.AbortStart,
            is Work.RetryResendStart,
            is Work.ResendStartDeadline,
            is Work.AbortResendStart,
            Work.Heartbeat -> Unit
        }
    }

    private fun failCompletion(work: Work, failure: Throwable) {
        when (work) {
            is Work.HostMutation -> work.completion.completeExceptionally(failure)
            is Work.End -> work.completion.completeExceptionally(failure)
            is Work.BeginStart -> work.completion.completeExceptionally(failure)
            is Work.ResendStart -> work.completion.completeExceptionally(failure)
            is Work.Incoming,
            is Work.Publish,
            is Work.RetryStart,
            is Work.StartDeadline,
            is Work.AbortStart,
            is Work.RetryResendStart,
            is Work.ResendStartDeadline,
            is Work.AbortResendStart,
            Work.Heartbeat -> Unit
        }
    }

    private suspend fun processBeginStart(work: Work.BeginStart) {
        if (work.completion.isCancelled) {
            _startState.value = HostSessionStartState.Failed
            return
        }
        if (ended || room.lifecycle.value != RoomLifecycleState.Active) {
            _startState.value = HostSessionStartState.Failed
            work.completion.complete(Result.Failure(NetError.SessionSuspended))
            return
        }
        completedStart?.let { (offer, _) ->
            work.completion.complete(
                if (offer == work.offer) Result.Success(offer)
                else Result.Failure(NetError.SessionStarted),
            )
            return
        }
        if (startAttempt != null) {
            work.completion.complete(Result.Failure(NetError.CommandInFlight))
            return
        }

        _startState.value = HostSessionStartState.Waiting
        val commit = HostMessage.SessionStartCommitted(
            startId = work.offer.startId,
            header = nextHeader(),
        )
        check(commit.validateFor(protocol) == ProtocolValidation.Valid)
        val attempt = StartAttempt(
            offer = work.offer,
            commit = commit,
            completion = work.completion,
            initialRetryMs = work.initialRetryMs,
            maxRetryMs = work.maxRetryMs,
            deadlineMs = work.deadlineMs,
            nextRetryMs = work.initialRetryMs,
            awaiting = remotePlayers.toMutableSet(),
        )
        startAttempt = attempt

        // Arm the absolute transaction deadline before invoking transport
        // sends. Start sends run in cancellable child jobs, so a cooperative
        // transport that stalls cannot block this sole mailbox worker.
        scheduleStartDeadline(attempt, StartPhase.AwaitingReady)

        if (attempt.awaiting.isEmpty()) {
            commitStart(attempt)
            return
        }
        dispatchStartFrames(attempt, attempt.offer)
        scheduleStartRetry(attempt)
    }

    private suspend fun processStartReady(message: PeerMessage.SessionStartReady) {
        if (
            message.actor !in remotePlayers ||
            message.validateFor(protocol) != ProtocolValidation.Valid
        ) {
            return
        }
        val attempt = startAttempt
        if (
            attempt != null &&
            message.startId == attempt.offer.startId &&
            attempt.phase == StartPhase.AwaitingReady
        ) {
            if (attempt.completion.isCancelled) {
                processStartAbort(attempt.offer.startId)
                return
            }
            attempt.awaiting.remove(message.actor)
            if (attempt.awaiting.isEmpty()) {
                commitStart(attempt)
            }
            return
        }

        val resend = resendStartAttempts[message.actor] ?: return
        if (
            resend.offer.startId != message.startId ||
            resend.phase != StartPhase.AwaitingReady
        ) {
            return
        }
        if (resend.completion.isCancelled) {
            cancelResendStartAttempt(resend)
            return
        }
        commitResendStart(resend)
    }

    private suspend fun processStartCommitAck(message: PeerMessage.SessionStartCommitAck) {
        if (
            message.actor !in remotePlayers ||
            message.validateFor(protocol) != ProtocolValidation.Valid
        ) {
            return
        }
        val attempt = startAttempt
        if (
            attempt != null &&
            attempt.phase == StartPhase.AwaitingCommitAck &&
            message.startId == attempt.offer.startId
        ) {
            attempt.awaiting.remove(message.actor)
            if (attempt.awaiting.isEmpty()) finishCommitDelivery(attempt)
        }

        val resend = resendStartAttempts[message.actor] ?: return
        if (
            resend.phase != StartPhase.AwaitingCommitAck ||
            message.startId != resend.offer.startId
        ) {
            return
        }
        if (resend.completion.isCancelled) {
            cancelResendStartAttempt(resend)
            return
        }
        finishResendStart(resend)
    }

    private suspend fun processStartRetry(startId: String) {
        val attempt = startAttempt?.takeIf { it.offer.startId == startId } ?: return
        if (attempt.completion.isCancelled) {
            processStartAbort(startId)
            return
        }
        attempt.retryJob = null
        when (attempt.phase) {
            StartPhase.AwaitingReady -> dispatchStartFrames(attempt, attempt.offer)
            StartPhase.AwaitingCommitAck -> dispatchStartFrames(attempt, attempt.commit)
        }
        attempt.nextRetryMs = doubledRetryDelay(attempt.nextRetryMs, attempt.maxRetryMs)
        scheduleStartRetry(attempt)
    }

    private suspend fun processStartDeadline(startId: String, phase: StartPhase) {
        val attempt = startAttempt?.takeIf { it.offer.startId == startId } ?: return
        if (attempt.phase != phase) return
        if (attempt.phase == StartPhase.AwaitingCommitAck) {
            // The commit happened when every peer was Ready. Ack delivery is
            // observable/retryable but never a rollback boundary.
            finishCommitDelivery(attempt)
            return
        }
        ended = true
        _startState.value = HostSessionStartState.Failed
        failActiveStart(NetError.Timeout)
        try {
            withTimeoutOrNull(startSendTimeoutMs) { sendEnd(SessionEndReason.Cancelled) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // The bounded local result remains Timeout even when the terminal
            // best-effort frame cannot be written to the failed transport.
        }
    }

    private suspend fun processStartAbort(startId: String) {
        val attempt = startAttempt ?: return
        if (
            attempt.offer.startId != startId ||
            attempt.phase != StartPhase.AwaitingReady ||
            _startState.value == HostSessionStartState.Started
        ) {
            // Caller cancellation can race completion delivery. Once Ready
            // quorum commits, the transaction is irreversible even if the
            // original UI coroutine is cancelled before observing success.
            return
        }
        ended = true
        _startState.value = HostSessionStartState.Failed
        failActiveStart(NetError.NotConnected)
        try {
            withTimeoutOrNull(startSendTimeoutMs) { sendEnd(SessionEndReason.Cancelled) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            Unit
        }
    }

    private suspend fun processStartResend(work: Work.ResendStart) {
        if (work.completion.isCancelled) return
        if (!requireStartHandshake) {
            work.completion.complete(Result.Success(Unit))
            return
        }
        val completed = completedStart
        if (
            completed == null ||
            work.playerId !in remotePlayers ||
            ended ||
            room.lifecycle.value != RoomLifecycleState.Active
        ) {
            work.completion.complete(Result.Failure(NetError.NotConnected))
            return
        }
        val existing = resendStartAttempts[work.playerId]
        if (existing != null) {
            if (existing.completion.isCancelled) {
                cancelResendStartAttempt(existing)
            } else {
                work.completion.complete(Result.Failure(NetError.CommandInFlight))
                return
            }
        }

        val attempt = ResendStartAttempt(
            playerId = work.playerId,
            offer = completed.first,
            commit = completed.second,
            completion = work.completion,
            initialRetryMs = work.initialRetryMs,
            maxRetryMs = work.maxRetryMs,
            readyDeadlineMs = work.readyDeadlineMs,
            commitAckDeadlineMs = work.commitAckDeadlineMs,
        )
        resendStartAttempts[work.playerId] = attempt
        scheduleResendStartDeadline(attempt)
        dispatchResendStartFrame(attempt, attempt.offer)
        scheduleResendStartRetry(attempt)
    }

    private fun processResendStartRetry(work: Work.RetryResendStart) {
        val attempt = resendStartAttempts[work.playerId]
            ?.takeIf { it.completion === work.completion }
            ?: return
        if (attempt.completion.isCancelled) {
            cancelResendStartAttempt(attempt)
            return
        }
        attempt.retryJob = null
        dispatchResendStartFrame(
            attempt,
            when (attempt.phase) {
                StartPhase.AwaitingReady -> attempt.offer
                StartPhase.AwaitingCommitAck -> attempt.commit
            },
        )
        attempt.nextRetryMs = doubledRetryDelay(attempt.nextRetryMs, attempt.maxRetryMs)
        scheduleResendStartRetry(attempt)
    }

    private fun processResendStartDeadline(work: Work.ResendStartDeadline) {
        val attempt = resendStartAttempts[work.playerId]
            ?.takeIf {
                it.completion === work.completion &&
                    it.phase == work.phase
            }
            ?: return
        failResendStartAttempt(attempt, NetError.Timeout)
    }

    private fun processResendStartAbort(work: Work.AbortResendStart) {
        val attempt = resendStartAttempts[work.playerId]
            ?.takeIf { it.completion === work.completion }
            ?: return
        if (attempt.completion.isCancelled) cancelResendStartAttempt(attempt)
    }

    private fun scheduleStartRetry(attempt: StartAttempt) {
        attempt.retryJob?.cancel()
        val delayMs = attempt.nextRetryMs
        attempt.retryJob = coordinatorScope.launch {
            delay(delayMs)
            mailbox.send(Work.RetryStart(attempt.offer.startId))
        }
    }

    private fun scheduleStartDeadline(attempt: StartAttempt, phase: StartPhase) {
        attempt.deadlineJob?.cancel()
        attempt.deadlineJob = coordinatorScope.launch {
            delay(attempt.deadlineMs)
            mailbox.send(Work.StartDeadline(attempt.offer.startId, phase))
        }
    }

    private fun scheduleResendStartRetry(attempt: ResendStartAttempt) {
        attempt.retryJob?.cancel()
        val delayMs = attempt.nextRetryMs
        attempt.retryJob = coordinatorScope.launch {
            delay(delayMs)
            mailbox.send(
                Work.RetryResendStart(
                    playerId = attempt.playerId,
                    completion = attempt.completion,
                ),
            )
        }
    }

    private fun scheduleResendStartDeadline(attempt: ResendStartAttempt) {
        attempt.deadlineJob?.cancel()
        val phase = attempt.phase
        val delayMs = when (phase) {
            StartPhase.AwaitingReady -> attempt.readyDeadlineMs
            StartPhase.AwaitingCommitAck -> attempt.commitAckDeadlineMs
        }
        attempt.deadlineJob = coordinatorScope.launch {
            delay(delayMs)
            mailbox.send(
                Work.ResendStartDeadline(
                    playerId = attempt.playerId,
                    phase = phase,
                    completion = attempt.completion,
                ),
            )
        }
    }

    private fun commitResendStart(attempt: ResendStartAttempt) {
        if (resendStartAttempts[attempt.playerId] !== attempt) return
        if (attempt.completion.isCancelled) {
            cancelResendStartAttempt(attempt)
            return
        }
        attempt.retryJob?.cancel()
        attempt.deadlineJob?.cancel()
        attempt.sendJob?.cancel()
        attempt.phase = StartPhase.AwaitingCommitAck
        attempt.nextRetryMs = attempt.initialRetryMs
        scheduleResendStartDeadline(attempt)
        dispatchResendStartFrame(attempt, attempt.commit)
        scheduleResendStartRetry(attempt)
    }

    private fun finishResendStart(attempt: ResendStartAttempt) {
        if (resendStartAttempts[attempt.playerId] !== attempt) return
        attempt.retryJob?.cancel()
        attempt.deadlineJob?.cancel()
        attempt.sendJob?.cancel()
        resendStartAttempts.remove(attempt.playerId)
        attempt.completion.complete(Result.Success(Unit))
    }

    private fun failResendStartAttempt(attempt: ResendStartAttempt, error: NetError) {
        if (resendStartAttempts[attempt.playerId] !== attempt) return
        attempt.retryJob?.cancel()
        attempt.deadlineJob?.cancel()
        attempt.sendJob?.cancel()
        resendStartAttempts.remove(attempt.playerId)
        attempt.completion.complete(Result.Failure(error))
    }

    private fun cancelResendStartAttempt(attempt: ResendStartAttempt) {
        if (resendStartAttempts[attempt.playerId] !== attempt) return
        attempt.retryJob?.cancel()
        attempt.deadlineJob?.cancel()
        attempt.sendJob?.cancel()
        resendStartAttempts.remove(attempt.playerId)
        if (!attempt.completion.isCancelled) {
            attempt.completion.cancel(CancellationException("Start replay cancelled"))
        }
    }

    private fun failResendStartAttempts(error: NetError) {
        resendStartAttempts.values.toList().forEach { attempt ->
            failResendStartAttempt(attempt, error)
        }
    }

    private fun cancelResendStartAttempts(cancelled: CancellationException) {
        resendStartAttempts.values.toList().forEach { attempt ->
            attempt.retryJob?.cancel()
            attempt.deadlineJob?.cancel()
            attempt.sendJob?.cancel()
            resendStartAttempts.remove(attempt.playerId)
            attempt.completion.cancel(cancelled)
        }
    }

    private suspend fun commitStart(attempt: StartAttempt) {
        if (startAttempt !== attempt) return
        if (attempt.completion.isCancelled) {
            processStartAbort(attempt.offer.startId)
            return
        }
        attempt.retryJob?.cancel()
        attempt.sendJob?.cancel()
        attempt.phase = StartPhase.AwaitingCommitAck
        attempt.awaiting.clear()
        attempt.awaiting += remotePlayers
        attempt.nextRetryMs = attempt.initialRetryMs
        scheduleStartDeadline(attempt, StartPhase.AwaitingCommitAck)
        completedStart = attempt.offer to attempt.commit
        _startState.value = HostSessionStartState.Started
        attempt.completion.complete(Result.Success(attempt.offer))
        if (attempt.awaiting.isEmpty()) {
            finishCommitDelivery(attempt)
        } else {
            dispatchStartFrames(attempt, attempt.commit)
            scheduleStartRetry(attempt)
        }
        // The peer installs a fresh snapshot after attaching its game inbox
        // collector. This eager revision-zero publication is useful when that
        // collector is already attached, but correctness never depends on it.
        publishSnapshots(incrementRevision = false)
    }

    private fun finishCommitDelivery(attempt: StartAttempt) {
        if (startAttempt !== attempt) return
        attempt.retryJob?.cancel()
        attempt.deadlineJob?.cancel()
        attempt.sendJob?.cancel()
        startAttempt = null
    }

    private fun failActiveStart(error: NetError) {
        val attempt = startAttempt ?: return
        attempt.retryJob?.cancel()
        attempt.deadlineJob?.cancel()
        attempt.sendJob?.cancel()
        startAttempt = null
        attempt.completion.complete(Result.Failure(error))
    }

    private fun cancelStartAttempt(cancelled: CancellationException) {
        val attempt = startAttempt ?: return
        attempt.retryJob?.cancel()
        attempt.deadlineJob?.cancel()
        attempt.sendJob?.cancel()
        startAttempt = null
        attempt.completion.cancel(cancelled)
    }

    private suspend fun sendStartFrame(
        playerId: PlayerId,
        message: HostMessage,
    ): Result<Unit, NetError> = withTimeoutOrNull(startSendTimeoutMs) {
        try {
            room.send(SendTarget.Direct(playerId), message)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            Result.Failure(
                NetError.TransportFailure(failure.message ?: "start frame send failed"),
            )
        }
    } ?: Result.Failure(NetError.Timeout)

    private fun dispatchStartFrames(attempt: StartAttempt, message: HostMessage) {
        attempt.sendJob?.cancel()
        attempt.sendJob = dispatchStartFrames(attempt.awaiting.toSet(), message)
    }

    private fun dispatchResendStartFrame(
        attempt: ResendStartAttempt,
        message: HostMessage,
    ) {
        attempt.sendJob?.cancel()
        attempt.sendJob = coordinatorScope.launch {
            sendStartFrame(attempt.playerId, message)
        }
    }

    private fun dispatchStartFrames(
        recipients: Set<PlayerId>,
        message: HostMessage,
    ): Job = coordinatorScope.launch {
        coroutineScope {
            recipients.map { playerId ->
                launch { sendStartFrame(playerId, message) }
            }.joinAll()
        }
    }

    private fun canProcessGameTraffic(): Boolean =
        !ended && (
            !requireStartHandshake ||
                _startState.value == HostSessionStartState.Started ||
                _startState.value == HostSessionStartState.NotRequired
            )

    private suspend fun processOutcomeRequest(request: PeerMessage.CommandOutcomeRequest) {
        if (
            request.actor !in remotePlayers ||
            request.validateFor(protocol) != ProtocolValidation.Valid
        ) {
            return
        }
        val recorded = commandResultsByActor[request.actor]?.get(request.commandId)
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

        val cached = commandResultsByActor[actor]?.get(command.commandId)
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
        outboundByPlayer[playerId]?.enqueue(message)
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
        outboundByPlayer[actor]?.enqueue(result)
    }

    private fun remember(actor: PlayerId, result: HostMessage.CommandResult) {
        val ledger = commandResultsByActor.getOrPut(actor) { linkedMapOf() }
        // A repeated command id keeps its original insertion position and
        // cannot consume the bounded ledger more than once.
        ledger[result.commandId] = result
        while (ledger.size > MAX_REMEMBERED_COMMANDS_PER_PEER) {
            ledger.remove(ledger.keys.first())
        }
    }

    private suspend fun sendHeartbeat() {
        val message = HostMessage.Heartbeat(
            header = nextHeader(),
            authoritativeRevision = _revision.value,
        )
        check(message.validateFor(protocol) == ProtocolValidation.Valid)
        outboundByPlayer.values.forEach { it.enqueue(message) }
    }

    private suspend fun sendEnd(reason: SessionEndReason) {
        val message = HostMessage.SessionEnded(
            header = nextHeader(),
            reason = reason,
            finalRevision = _revision.value,
        )
        check(message.validateFor(protocol) == ProtocolValidation.Valid)
        outboundByPlayer.values
            .map { outbox -> coordinatorScope.async { outbox.deliverTerminal(message) } }
            .awaitAll()
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

    private fun doubledRetryDelay(current: Long, maximum: Long): Long {
        val doubled = if (current > Long.MAX_VALUE / 2L) Long.MAX_VALUE else current * 2L
        return doubled.coerceAtMost(maximum)
    }

    private companion object {
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000L
        const val DEFAULT_START_RETRY_MS = 250L
        const val DEFAULT_MAX_START_RETRY_MS = 2_000L
        const val DEFAULT_START_DEADLINE_MS = 20_000L
        const val DEFAULT_OUTBOUND_SEND_TIMEOUT_MS = 2_000L
        const val HOST_MAILBOX_CAPACITY = 8
        const val MAX_REMEMBERED_COMMANDS_PER_PEER = 256
    }
}

data class PeerCommandReceipt(
    val commandId: String,
    val clientSequence: Long,
)

enum class PeerCommandDelivery {
    Sending,
    Sent,
    /** The send returned an error, so reconciliation must query instead of replaying. */
    Ambiguous,
    /** A bounded command-outcome query is in progress; the action is never replayed. */
    Reconciling,
    /** The bounded query deadline elapsed. The command remains in flight and must not be replayed. */
    RecoveryTimedOut,
}

data class PeerCommandOutcome(
    val commandId: String,
    val status: CommandStatus,
    val authoritativeRevision: Long,
    val nextExpectedClientSequence: Long,
)

sealed interface PeerCommandProgress {
    data object Idle : PeerCommandProgress
    data class Awaiting(
        val receipt: PeerCommandReceipt,
        val expectedRevision: Long,
        val delivery: PeerCommandDelivery,
    ) : PeerCommandProgress
    data class Resolved(val outcome: PeerCommandOutcome) : PeerCommandProgress
}

/**
 * Peer-side ordering, retry, and snapshot validation. It never reduces game
 * state; [onSnapshot] installs the host's atomic player projection.
 */
class PeerAuthoritativeSessionCoordinator(
    private val room: LocalRoom,
    val protocol: SessionProtocol,
    private val selfPlayerId: PlayerId,
    private val scope: CoroutineScope,
    /** Returns true only after both public and private projections are installed. */
    private val onSnapshot: suspend (PlayerSnapshotPayload, revision: Long) -> Boolean,
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
    /** Stable start identity accepted by the peer-lobby barrier. */
    private val acceptedStartId: String? = protocol.startId,
    private val initialSnapshotRetryMs: Long = DEFAULT_INITIAL_SNAPSHOT_RETRY_MS,
    private val maxInitialSnapshotRetryMs: Long = DEFAULT_MAX_INITIAL_SNAPSHOT_RETRY_MS,
    private val initialSnapshotDeadlineMs: Long = DEFAULT_INITIAL_SNAPSHOT_DEADLINE_MS,
    private val snapshotSendTimeoutMs: Long = DEFAULT_SNAPSHOT_SEND_TIMEOUT_MS,
    private val outcomeInitialRetryMs: Long = DEFAULT_OUTCOME_INITIAL_RETRY_MS,
    private val outcomeMaxRetryMs: Long = DEFAULT_OUTCOME_MAX_RETRY_MS,
    private val outcomeDeadlineMs: Long = DEFAULT_OUTCOME_DEADLINE_MS,
) {
    private val requiresInitialSnapshot = acceptedStartId != null
    private val _revision = MutableStateFlow(if (requiresInitialSnapshot) -1L else 0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _hasAuthoritativeSnapshot = MutableStateFlow(!requiresInitialSnapshot)
    val hasAuthoritativeSnapshot: StateFlow<Boolean> =
        _hasAuthoritativeSnapshot.asStateFlow()

    private val _initialSnapshotError = MutableStateFlow<NetError?>(null)
    val initialSnapshotError: StateFlow<NetError?> = _initialSnapshotError.asStateFlow()

    // Compatibility-only observation stream. [commandProgress] is the durable
    // UI contract; this bounded best-effort stream must never suspend the sole
    // authenticated inbound-frame collector when a subscriber is slow.
    private val _results = MutableSharedFlow<HostMessage.CommandResult>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results: SharedFlow<HostMessage.CommandResult> = _results.asSharedFlow()

    private val _commandProgress = MutableStateFlow<PeerCommandProgress>(PeerCommandProgress.Idle)
    val commandProgress: StateFlow<PeerCommandProgress> = _commandProgress.asStateFlow()

    private val pending = linkedMapOf<String, PeerMessage.ClientCommand>()
    private var nextClientSequence = 1L
    private var lastInstalledSnapshotRevision = -1L
    private var terminalAccepted = false
    private var closed = false
    private val seenHostMessageIds = mutableSetOf<String>()
    private val seenHostMessageOrder = ArrayDeque<String>()
    private val stateMutex = Mutex()
    /** Serializes the full submit/send boundary with deterministic close. */
    private val submitMutex = Mutex()
    private val outcomeJobMutex = Mutex()
    private val jobs = mutableListOf<Job>()
    private var pendingOutcomeJob: Job? = null
    private val peerJob = SupervisorJob(scope.coroutineContext[Job])
    private val peerScope = CoroutineScope(scope.coroutineContext + peerJob)

    init {
        require(initialSnapshotRetryMs > 0L) { "initialSnapshotRetryMs must be positive" }
        require(maxInitialSnapshotRetryMs >= initialSnapshotRetryMs) {
            "maxInitialSnapshotRetryMs must be at least initialSnapshotRetryMs"
        }
        require(initialSnapshotDeadlineMs > 0L) {
            "initialSnapshotDeadlineMs must be positive"
        }
        require(snapshotSendTimeoutMs > 0L) { "snapshotSendTimeoutMs must be positive" }
        require(outcomeInitialRetryMs > 0L) { "outcomeInitialRetryMs must be positive" }
        require(outcomeMaxRetryMs >= outcomeInitialRetryMs) {
            "outcomeMaxRetryMs must be at least outcomeInitialRetryMs"
        }
        require(outcomeDeadlineMs > outcomeInitialRetryMs) {
            "outcomeDeadlineMs must exceed outcomeInitialRetryMs"
        }
        jobs += peerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            room.incoming.collect { message ->
                when (message) {
                    is HostMessage.PlayerSnapshot -> acceptSnapshot(message)
                    is HostMessage.CommandResult -> acceptResult(message)
                    is HostMessage.Heartbeat -> acceptHeartbeat(message)
                    is HostMessage.SessionEnded -> acceptSessionEnded(message)
                    is HostMessage.SessionStartCommitted -> acknowledgeDuplicateStartCommit(message)
                    else -> Unit
                }
            }
        }
        // The start commit may precede game-specific content/controller
        // construction. Always reconcile after this collector is attached;
        // correctness never depends on retaining an eager revision-zero frame.
        if (requiresInitialSnapshot) {
            jobs += peerScope.launch { recoverInitialSnapshot() }
        }
        jobs += peerScope.launch {
            room.peerEvents.collect { event ->
                if (event == com.parlor.networking.room.PeerEvent.HostRestored) {
                    requestSnapshot()
                    requestPendingOutcomes()
                }
            }
        }
    }

    suspend fun submit(payload: ByteArray): Result<PeerCommandReceipt, NetError> =
        submitMutex.withLock submit@{
            if (isClosed()) return@submit Result.Failure(NetError.NotConnected)
            if (room.lifecycle.value != RoomLifecycleState.Active) {
                return@submit Result.Failure(NetError.SessionSuspended)
            }
            if (!_hasAuthoritativeSnapshot.value) {
                return@submit Result.Failure(NetError.SessionSuspended)
            }
            if (payload.size > com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES) {
                return@submit Result.Failure(NetError.PayloadTooLarge)
            }
            val command = stateMutex.withLock {
                if (pending.isNotEmpty()) {
                    return@submit Result.Failure(NetError.CommandInFlight)
                }
                val commandId = idGenerator()
                val clientSequence = nextClientSequence++
                PeerMessage.ClientCommand(
                    header = peerHeader(commandId),
                    actor = selfPlayerId,
                    commandId = commandId,
                    clientSequence = clientSequence,
                    expectedRevision = _revision.value,
                    payload = payload.copyOf(),
                ).also {
                    pending[commandId] = it
                    _commandProgress.value = PeerCommandProgress.Awaiting(
                        receipt = PeerCommandReceipt(commandId, clientSequence),
                        expectedRevision = it.expectedRevision,
                        delivery = PeerCommandDelivery.Sending,
                    )
                }
            }
            val sent = try {
                sendPeerFrame(command)
            } catch (cancelled: CancellationException) {
                // Cancellation of an in-progress transport write is ambiguous.
                // Establish coordinator-owned recovery before propagating it, but
                // never let a query overtake a write that is still in progress.
                establishOutcomeRecovery(command.commandId, PeerCommandDelivery.Ambiguous)
                throw cancelled
            }
            when (sent) {
                is Result.Success -> {
                    establishOutcomeRecovery(command.commandId, PeerCommandDelivery.Sent)
                    Result.Success(PeerCommandReceipt(command.commandId, command.clientSequence))
                }
                is Result.Failure -> {
                    // A transport error does not prove that the host did not apply
                    // the action. Keep it pending and reconcile by command id after
                    // recovery; never replay a non-idempotent game action.
                    establishOutcomeRecovery(command.commandId, PeerCommandDelivery.Ambiguous)
                    Result.Failure(sent.error)
                }
            }
        }

    /**
     * Once a command write settles, ownership of its unresolved outcome must
     * move atomically to this coordinator even if the submitting UI coroutine
     * is cancelled at that boundary.
     */
    private suspend fun establishOutcomeRecovery(
        commandId: String,
        delivery: PeerCommandDelivery,
    ) = withContext(NonCancellable) {
        updateDelivery(commandId, delivery)
        startOutcomeReconciliation(commandId)
    }

    suspend fun acknowledgeCommandOutcome(commandId: String) {
        stateMutex.withLock {
            val resolved = _commandProgress.value as? PeerCommandProgress.Resolved
            if (resolved?.outcome?.commandId == commandId) {
                _commandProgress.value = PeerCommandProgress.Idle
            }
        }
    }

    private suspend fun updateDelivery(commandId: String, delivery: PeerCommandDelivery) {
        stateMutex.withLock {
            val awaiting = _commandProgress.value as? PeerCommandProgress.Awaiting
            if (awaiting?.receipt?.commandId == commandId && commandId in pending) {
                _commandProgress.value = awaiting.copy(delivery = delivery)
            }
        }
    }

    suspend fun requestSnapshot(): Result<Unit, NetError> {
        val revision = stateMutex.withLock {
            if (closed) return Result.Failure(NetError.NotConnected)
            _revision.value
        }
        return sendPeerFrame(
            PeerMessage.SnapshotRequest(
                header = peerHeader(idGenerator()),
                actor = selfPlayerId,
                lastAppliedRevision = revision,
            ),
        )
    }

    private suspend fun recoverInitialSnapshot() {
        var retryMs = initialSnapshotRetryMs
        val installed = withTimeoutOrNull(initialSnapshotDeadlineMs) {
            while (!_hasAuthoritativeSnapshot.value && !isTerminalAccepted()) {
                requestSnapshot()
                if (_hasAuthoritativeSnapshot.value || isTerminalAccepted()) break
                delay(retryMs)
                retryMs = (retryMs * 2L).coerceAtMost(maxInitialSnapshotRetryMs)
            }
            _hasAuthoritativeSnapshot.value
        } ?: false
        if (!installed && !isTerminalAccepted() && !_hasAuthoritativeSnapshot.value) {
            _initialSnapshotError.value = NetError.Timeout
        }
    }

    suspend fun close() {
        submitMutex.withLock {
            stateMutex.withLock {
                closed = true
                pending.values.forEach { it.payload.fill(0) }
                pending.clear()
                _commandProgress.value = PeerCommandProgress.Idle
            }
        }
        cancelOutcomeReconciliation()
        peerJob.cancelAndJoin()
        pendingOutcomeJob = null
        jobs.clear()
    }

    private suspend fun acceptSnapshot(snapshot: HostMessage.PlayerSnapshot) {
        val validation = snapshot.validateFor(protocol)
        if (validation != ProtocolValidation.Valid) {
            onProtocolViolation(validation)
            return
        }
        val eligible = stateMutex.withLock {
            if (
                closed ||
                terminalAccepted ||
                snapshot.header.messageId in seenHostMessageIds ||
                snapshot.revision <= lastInstalledSnapshotRevision
            ) {
                false
            } else true
        }
        if (!eligible) return
        val installed = try {
            onSnapshot(
                PlayerSnapshotPayload(
                    publicPayload = snapshot.publicPayload.copyOf(),
                    privatePayload = snapshot.privatePayload.copyOf(),
                ),
                snapshot.revision,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            false
        }
        if (!installed) {
            // Do not consume the message id or revision. A corrected snapshot
            // for this same authoritative revision must remain installable.
            onProtocolViolation(ProtocolValidation.SnapshotPayloadInvalid)
            return
        }
        stateMutex.withLock {
            rememberHostMessage(snapshot.header.messageId)
            lastInstalledSnapshotRevision = snapshot.revision
            _revision.value = snapshot.revision
            _hasAuthoritativeSnapshot.value = true
            _initialSnapshotError.value = null
        }
        restartTimedOutOutcomeReconciliationIfNeeded()
    }

    private suspend fun acceptResult(result: HostMessage.CommandResult) {
        val validation = result.validateFor(protocol)
        if (validation != ProtocolValidation.Valid) {
            onProtocolViolation(validation)
            return
        }
        val accepted = stateMutex.withLock {
            if (
                closed ||
                terminalAccepted ||
                !rememberHostMessage(result.header.messageId) ||
                result.commandId !in pending
            ) {
                false
            } else {
                nextClientSequence = result.nextExpectedClientSequence
                pending.remove(result.commandId)?.payload?.fill(0)
                _commandProgress.value = PeerCommandProgress.Resolved(
                    PeerCommandOutcome(
                        commandId = result.commandId,
                        status = result.status,
                        authoritativeRevision = result.authoritativeRevision,
                        nextExpectedClientSequence = result.nextExpectedClientSequence,
                    ),
                )
                true
            }
        }
        if (!accepted) return
        cancelOutcomeReconciliation()
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
        val heartbeatAccepted = stateMutex.withLock {
            if (
                closed ||
                terminalAccepted ||
                !rememberHostMessage(heartbeat.header.messageId)
            ) {
                null
            } else {
                // Revision -1 means this peer has never installed a host
                // snapshot, so even a revision-zero heartbeat must recover it.
                heartbeat.authoritativeRevision > _revision.value
            }
        }
        if (heartbeatAccepted == null) return
        restartTimedOutOutcomeReconciliationIfNeeded()
        if (heartbeatAccepted) requestSnapshot()
    }

    private suspend fun acceptSessionEnded(ended: HostMessage.SessionEnded) {
        val validation = ended.validateFor(protocol)
        if (validation != ProtocolValidation.Valid) {
            onProtocolViolation(validation)
            return
        }
        val accepted = stateMutex.withLock {
            if (
                closed ||
                terminalAccepted ||
                !rememberHostMessage(ended.header.messageId) ||
                ended.finalRevision < _revision.value
            ) {
                false
            } else {
                terminalAccepted = true
                _revision.value = ended.finalRevision
                pending.keys.singleOrNull()?.let { commandId ->
                    _commandProgress.value = PeerCommandProgress.Resolved(
                        PeerCommandOutcome(
                            commandId = commandId,
                            status = CommandStatus.SessionEnded,
                            authoritativeRevision = ended.finalRevision,
                            nextExpectedClientSequence = nextClientSequence,
                        ),
                    )
                }
                pending.values.forEach { it.payload.fill(0) }
                pending.clear()
                true
            }
        }
        if (!accepted) return
        cancelOutcomeReconciliation()
        onSessionEnded(ended)
    }

    private suspend fun acknowledgeDuplicateStartCommit(
        committed: HostMessage.SessionStartCommitted,
    ) {
        val startId = acceptedStartId ?: return
        if (
            committed.startId != startId ||
            committed.validateFor(protocol) != ProtocolValidation.Valid
        ) {
            return
        }
        try {
            sendPeerFrame(
                PeerMessage.SessionStartCommitAck(
                    header = peerStartHeader(protocol, idGenerator()),
                    actor = selfPlayerId,
                    startId = startId,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Best effort: the host will retry the stable commit frame.
        }
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
        val commands = stateMutex.withLock {
            if (closed) return Result.Failure(NetError.NotConnected)
            pending.values.toList()
        }
        for (command in commands) {
            when (val sent = requestCommandOutcome(command.commandId)) {
                is Result.Success -> Unit
                is Result.Failure -> return Result.Failure(sent.error)
            }
            startOutcomeReconciliation(command.commandId)
        }
        return Result.Success(Unit)
    }

    private suspend fun startOutcomeReconciliation(commandId: String) {
        val replacement = peerScope.launch(start = CoroutineStart.LAZY) {
            val resolved = withTimeoutOrNull(outcomeDeadlineMs) {
                var retryMs = outcomeInitialRetryMs
                delay(retryMs)
                while (isCommandPending(commandId)) {
                    updateDelivery(commandId, PeerCommandDelivery.Reconciling)
                    requestCommandOutcome(commandId)
                    if (!isCommandPending(commandId)) return@withTimeoutOrNull true
                    delay(retryMs)
                    retryMs = doubledRetryDelay(retryMs, outcomeMaxRetryMs)
                }
                true
            } ?: false
            if (!resolved && isCommandPending(commandId)) {
                updateDelivery(commandId, PeerCommandDelivery.RecoveryTimedOut)
            }
        }
        val previous = outcomeJobMutex.withLock {
            pendingOutcomeJob.also { pendingOutcomeJob = replacement }
        }
        previous?.cancel()
        replacement.start()
    }

    private suspend fun cancelOutcomeReconciliation() {
        val active = outcomeJobMutex.withLock {
            pendingOutcomeJob.also { pendingOutcomeJob = null }
        }
        active?.cancel()
    }

    /**
     * A timed-out outcome lookup never releases or replays the action. Fresh,
     * authenticated host traffic proves that the resumed channel is usable,
     * so it may open another bounded lookup window for the same command id.
     */
    private suspend fun restartTimedOutOutcomeReconciliationIfNeeded() {
        val commandId = stateMutex.withLock {
            val awaiting = _commandProgress.value as? PeerCommandProgress.Awaiting
            awaiting
                ?.takeIf {
                    it.delivery == PeerCommandDelivery.RecoveryTimedOut &&
                        it.receipt.commandId in pending &&
                        !terminalAccepted
                }
                ?.receipt
                ?.commandId
        } ?: return
        startOutcomeReconciliation(commandId)
    }

    private suspend fun isCommandPending(commandId: String): Boolean =
        stateMutex.withLock { !terminalAccepted && commandId in pending }

    private suspend fun isTerminalAccepted(): Boolean =
        stateMutex.withLock { terminalAccepted }

    private suspend fun isClosed(): Boolean = stateMutex.withLock { closed }

    private suspend fun requestCommandOutcome(commandId: String): Result<Unit, NetError> =
        sendPeerFrame(
            PeerMessage.CommandOutcomeRequest(
                header = peerHeader(commandId),
                actor = selfPlayerId,
                commandId = commandId,
            ),
        )

    /** Every peer-originated frame has one bounded, cancellation-safe send. */
    private suspend fun sendPeerFrame(message: PeerMessage): Result<Unit, NetError> =
        withTimeoutOrNull(snapshotSendTimeoutMs) {
            try {
                room.sendToHost(message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                Result.Failure(
                    NetError.TransportFailure(failure.message ?: "peer frame send failed"),
                )
            }
        } ?: Result.Failure(NetError.Timeout)

    private fun doubledRetryDelay(current: Long, maximum: Long): Long {
        val doubled = if (current > Long.MAX_VALUE / 2L) Long.MAX_VALUE else current * 2L
        return doubled.coerceAtMost(maximum)
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
        const val DEFAULT_INITIAL_SNAPSHOT_RETRY_MS = 250L
        const val DEFAULT_MAX_INITIAL_SNAPSHOT_RETRY_MS = 2_000L
        const val DEFAULT_INITIAL_SNAPSHOT_DEADLINE_MS = 20_000L
        const val DEFAULT_SNAPSHOT_SEND_TIMEOUT_MS = 2_000L
        const val DEFAULT_OUTCOME_INITIAL_RETRY_MS = 500L
        const val DEFAULT_OUTCOME_MAX_RETRY_MS = 2_000L
        const val DEFAULT_OUTCOME_DEADLINE_MS = 20_000L
    }
}
