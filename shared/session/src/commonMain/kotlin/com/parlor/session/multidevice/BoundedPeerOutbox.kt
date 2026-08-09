package com.parlor.session.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.MAX_CONTROL_PAYLOAD_BYTES
import com.parlor.networking.protocol.MAX_ROOM_FRAME_BYTES
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One bounded, independently scheduled host-to-peer lane.
 *
 * Command results are retained in FIFO order, while snapshots and heartbeats
 * are convergent state and are therefore safely conflated to their newest
 * value. A terminal frame has priority and owns a completion so host shutdown
 * waits for one bounded delivery attempt per peer without serialising peers.
 *
 * The queue's conservative byte accounting charges every control frame the
 * full protocol control allowance. Together with one latest snapshot and the
 * frame currently being sent, one peer retains at most
 * [MAX_OUTBOUND_BYTES_PER_PEER] bytes of protocol payload.
 */
internal class BoundedPeerOutbox(
    private val playerId: PlayerId,
    private val room: LocalRoom,
    scope: CoroutineScope,
    private val sendTimeoutMs: Long,
) {
    private data class Terminal(
        val message: HostMessage.SessionEnded,
        val completion: CompletableDeferred<Result<Unit, NetError>>,
    )

    private sealed interface NextFrame {
        val message: HostMessage

        data class Control(override val message: HostMessage.CommandResult) : NextFrame
        data class Snapshot(override val message: HostMessage.PlayerSnapshot) : NextFrame
        data class Heartbeat(override val message: HostMessage.Heartbeat) : NextFrame
        data class End(val terminal: Terminal) : NextFrame {
            override val message: HostMessage = terminal.message
        }
    }

    private val mutex = Mutex()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val commandResults = ArrayDeque<HostMessage.CommandResult>()
    private var latestSnapshot: HostMessage.PlayerSnapshot? = null
    private var latestHeartbeat: HostMessage.Heartbeat? = null
    private var terminal: Terminal? = null
    /**
     * A terminal transaction remains the authoritative result for duplicate
     * callers even after its frame has been taken by the worker. Keeping the
     * transaction separate from this queued bit prevents a second concurrent
     * shutdown request from scheduling a duplicate terminal frame.
     */
    private var terminalQueued = false
    private var controlsSinceSnapshot = 0

    private val worker: Job = scope.launch {
        for (ignored in wakeUp) {
            while (true) {
                val frame = mutex.withLock { takeNextLocked() } ?: break
                var completed = false
                try {
                    val outcome = send(frame.message)
                    (frame as? NextFrame.End)?.terminal?.completion?.complete(outcome)
                    completed = true
                } finally {
                    val completion = (frame as? NextFrame.End)?.terminal?.completion
                    if (!completed && completion != null && !completion.isCompleted) {
                        completion.cancel(
                            CancellationException("Outbound terminal delivery was cancelled"),
                        )
                    }
                }
            }
        }
    }

    init {
        require(sendTimeoutMs > 0L) { "sendTimeoutMs must be positive" }
    }

    /**
     * Enqueues a non-conflatable command acknowledgement. False means this
     * peer exhausted its isolated budget; no other peer's lane is affected.
     */
    suspend fun enqueue(result: HostMessage.CommandResult): Boolean = mutex.withLock {
        if (!worker.isActive || terminal != null || commandResults.size >= MAX_CONTROL_FRAMES) {
            return@withLock false
        }
        commandResults.addLast(result)
        wakeUp.trySend(Unit)
        true
    }

    /** Newer authoritative state replaces an unsent older snapshot. */
    suspend fun enqueue(snapshot: HostMessage.PlayerSnapshot): Boolean = mutex.withLock {
        if (!worker.isActive || terminal != null) return@withLock false
        latestSnapshot = snapshot
        wakeUp.trySend(Unit)
        true
    }

    /** Heartbeats are hints; retaining more than the latest has no value. */
    suspend fun enqueue(heartbeat: HostMessage.Heartbeat): Boolean = mutex.withLock {
        if (!worker.isActive || terminal != null) return@withLock false
        latestHeartbeat = heartbeat
        wakeUp.trySend(Unit)
        true
    }

    /**
     * Queues the terminal frame ahead of pending normal traffic and waits for
     * exactly one bounded delivery attempt. Duplicate terminal requests share
     * the first transaction.
     */
    suspend fun deliverTerminal(
        message: HostMessage.SessionEnded,
    ): Result<Unit, NetError> {
        val completion = mutex.withLock {
            if (!worker.isActive) return Result.Failure(NetError.NotConnected)
            terminal?.completion ?: CompletableDeferred<Result<Unit, NetError>>().also {
                terminal = Terminal(message, it)
                terminalQueued = true
                // Once the session is terminal, stale results/snapshots cannot
                // be useful and must not delay or retain private payloads.
                commandResults.clear()
                latestSnapshot = null
                latestHeartbeat = null
                wakeUp.trySend(Unit)
            }
        }
        return completion.await()
    }

    fun close() {
        // The terminal may still be queued and therefore never reach the
        // worker's finally block. Complete it here so a caller cannot wait
        // forever when the parent session is cancelled during shutdown.
        terminal?.completion?.cancel(
            CancellationException("Outbound terminal delivery was cancelled"),
        )
        wakeUp.close()
        worker.cancel(CancellationException("Peer outbox closed"))
    }

    private fun takeNextLocked(): NextFrame? {
        if (terminalQueued) {
            terminalQueued = false
            return checkNotNull(terminal).let {
                NextFrame.End(it)
            }
        }

        if (latestSnapshot != null && (
                commandResults.isEmpty() || controlsSinceSnapshot >= CONTROL_BURST_BEFORE_SNAPSHOT
            )
        ) {
            controlsSinceSnapshot = 0
            return NextFrame.Snapshot(checkNotNull(latestSnapshot).also { latestSnapshot = null })
        }

        if (commandResults.isNotEmpty()) {
            controlsSinceSnapshot += 1
            return NextFrame.Control(commandResults.removeFirst())
        }

        latestSnapshot?.let {
            latestSnapshot = null
            controlsSinceSnapshot = 0
            return NextFrame.Snapshot(it)
        }

        latestHeartbeat?.let {
            latestHeartbeat = null
            return NextFrame.Heartbeat(it)
        }
        return null
    }

    private suspend fun send(message: HostMessage): Result<Unit, NetError> =
        withTimeoutOrNull(sendTimeoutMs) {
            try {
                room.send(SendTarget.Direct(playerId), message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.Failure(
                    NetError.TransportFailure(failure.message ?: "outbound send failed"),
                )
            }
        } ?: Result.Failure(NetError.Timeout)

    internal companion object {
        const val MAX_CONTROL_FRAMES: Int = 32
        const val CONTROL_BURST_BEFORE_SNAPSHOT: Int = 4

        /**
         * Conservative retained/in-flight protocol payload bound per peer:
         * 32 control frames, one conflated snapshot, and one in-flight frame.
         */
        const val MAX_OUTBOUND_BYTES_PER_PEER: Int =
            MAX_CONTROL_FRAMES * MAX_CONTROL_PAYLOAD_BYTES +
                MAX_ROOM_FRAME_BYTES * 2
    }
}
