package com.parlor.transport.p2p

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** Closed vocabulary: no caller-controlled text can cross this boundary. */
internal enum class P2pDiagnosticEventName {
    SESSION_CREATE_STARTED,
    SESSION_CREATE_SUCCEEDED,
    SESSION_CREATE_FAILED,
    DISCOVERY_STARTED,
    DISCOVERY_CANDIDATES,
    DISCOVERY_ATTEMPTED,
    DISCOVERY_FINISHED,
    CONNECTION_SECURE,
    CONNECTION_CLOSED,
    ADMISSION_REQUESTED,
    ADMISSION_RESERVED,
    ADMISSION_COMMITTED,
    ADMISSION_ROLLED_BACK,
    ADMISSION_REJECTED,
    PROTOCOL_REJECTED,
    COMMAND_SENT,
    COMMAND_RECEIVED,
    COMMAND_ACCEPTED,
    COMMAND_REJECTED,
    COMMAND_DUPLICATE,
    SNAPSHOT_SENT,
    SNAPSHOT_RECEIVED,
    LIFECYCLE_SUSPENDED,
    LIFECYCLE_RESUME_STARTED,
    LIFECYCLE_RESUMED,
    LIFECYCLE_EXPIRED,
    CLEANUP_STARTED,
    CLEANUP_COMPLETED,
    CLEANUP_FAILED,
    FRAME_DROPPED,
    PEER_RATE_LIMITED,
}

internal enum class P2pDiagnosticRole { NONE, HOST, PEER }

internal enum class P2pDiagnosticResult {
    NONE,
    SUCCESS,
    FAILURE,
    TIMEOUT,
    CANCELLED,
    REJECTED,
    DUPLICATE,
}

internal enum class P2pDiagnosticReason {
    NONE,
    TRANSPORT,
    PERMISSION,
    WRONG_ROOM,
    ROOM_FULL,
    SESSION_STARTED,
    INCOMPATIBLE_PROTOCOL,
    UNAUTHORIZED,
    RATE_LIMIT,
    PAYLOAD_LIMIT,
    WRONG_DIRECTION,
    MALFORMED,
    INVALID_ACTION,
    STALE_REVISION,
    SEQUENCE_GAP,
    SESSION_ENDED,
    UNKNOWN_COMMAND,
    DISCONNECTED,
    LIFECYCLE,
    INTERNAL,
}

internal enum class P2pDiagnosticCountBucket { NONE, ZERO, ONE, TWO_TO_FOUR, FIVE_TO_EIGHT, NINE_TO_SEVENTEEN, OVER_LIMIT }

internal data class P2pDiagnosticEvent(
    val name: P2pDiagnosticEventName,
    val role: P2pDiagnosticRole = P2pDiagnosticRole.NONE,
    val result: P2pDiagnosticResult = P2pDiagnosticResult.NONE,
    val reason: P2pDiagnosticReason = P2pDiagnosticReason.NONE,
    val count: P2pDiagnosticCountBucket = P2pDiagnosticCountBucket.NONE,
)

internal data class P2pDiagnosticRecord(
    val sequence: Long,
    val elapsedMillis: Long,
    val event: P2pDiagnosticEvent,
) {
    /** Redacted, deterministic line suitable for adb/Xcode evidence. */
    fun exportLine(): String = buildString {
        append("seq=").append(sequence)
        append(" elapsed_ms=").append(elapsedMillis)
        append(" event=").append(event.name.name.lowercase())
        append(" role=").append(event.role.name.lowercase())
        append(" result=").append(event.result.name.lowercase())
        append(" reason=").append(event.reason.name.lowercase())
        append(" count=").append(event.count.name.lowercase())
    }
}

internal interface P2pDiagnostics {
    fun record(event: P2pDiagnosticEvent)
    fun snapshot(): List<P2pDiagnosticRecord>
    fun export(): String
}

internal data object NoOpP2pDiagnostics : P2pDiagnostics {
    override fun record(event: P2pDiagnosticEvent) = Unit
    override fun snapshot(): List<P2pDiagnosticRecord> = emptyList()
    override fun export(): String = ""
}

internal fun interface P2pDiagnosticWriter {
    fun write(line: String)
}

/**
 * Bounded diagnostic recorder.
 *
 * The ring retains at most [capacity] fixed-shape records. Platform output is
 * fed through a one-record DROP_OLDEST channel and emitted at most once per
 * [outputIntervalMillis], so diagnostics cannot become a second flood path.
 */
internal class BoundedP2pDiagnostics(
    scope: CoroutineScope,
    private val writer: P2pDiagnosticWriter,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val outputIntervalMillis: Long = DEFAULT_OUTPUT_INTERVAL_MS,
) : P2pDiagnostics {
    private val startedAt = TimeSource.Monotonic.markNow()
    private val records = MutableStateFlow<List<P2pDiagnosticRecord>>(emptyList())
    private val output = Channel<P2pDiagnosticRecord>(
        capacity = OUTPUT_QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        require(capacity > 0) { "capacity must be positive" }
        require(outputIntervalMillis >= 0L) { "outputIntervalMillis must not be negative" }
        scope.launch {
            for (record in output) {
                writer.write(record.exportLine())
                if (outputIntervalMillis > 0L) delay(outputIntervalMillis)
            }
        }
    }

    override fun record(event: P2pDiagnosticEvent) {
        var stored: P2pDiagnosticRecord? = null
        records.update { current ->
            val next = P2pDiagnosticRecord(
                sequence = (current.lastOrNull()?.sequence ?: 0L) + 1L,
                elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L),
                event = event,
            )
            stored = next
            if (current.size < capacity) current + next else current.drop(1) + next
        }
        stored?.let(output::trySend)
    }

    override fun snapshot(): List<P2pDiagnosticRecord> = records.value.toList()

    override fun export(): String = snapshot().joinToString(separator = "\n") {
        it.exportLine()
    }

    internal companion object {
        const val DEFAULT_CAPACITY: Int = 256
        const val OUTPUT_QUEUE_CAPACITY: Int = 1
        const val DEFAULT_OUTPUT_INTERVAL_MS: Long = 100L
    }
}

internal fun diagnosticCount(value: Int): P2pDiagnosticCountBucket = when (value) {
    0 -> P2pDiagnosticCountBucket.ZERO
    1 -> P2pDiagnosticCountBucket.ONE
    in 2..4 -> P2pDiagnosticCountBucket.TWO_TO_FOUR
    in 5..8 -> P2pDiagnosticCountBucket.FIVE_TO_EIGHT
    in 9..17 -> P2pDiagnosticCountBucket.NINE_TO_SEVENTEEN
    else -> P2pDiagnosticCountBucket.OVER_LIMIT
}

internal expect fun platformP2pDiagnosticWriter(): P2pDiagnosticWriter
