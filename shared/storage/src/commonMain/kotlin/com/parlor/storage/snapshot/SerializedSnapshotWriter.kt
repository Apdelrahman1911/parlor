package com.parlor.storage.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.GameState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Observable state of a session's serialized persistence boundary. */
sealed interface SnapshotWriteStatus {
    data object Idle : SnapshotWriteStatus
    data object Writing : SnapshotWriteStatus
    data object Saved : SnapshotWriteStatus
    data object Deleted : SnapshotWriteStatus
    data class Failed(val error: DataError) : SnapshotWriteStatus
}

/**
 * Serializes snapshot replacement, terminal deletion, explicit discard, and
 * retry for one local authoritative session.
 *
 * The caller should collect a conflated canonical [StateFlow] and invoke
 * [persist] for each observed state. This class deliberately owns no scope or
 * unbounded channel: lifecycle remains with the caller, while the mutex makes
 * a concurrent UI flush/discard transactional with the collector. A successful
 * [discard] is final for this writer, so a late collector callback cannot
 * resurrect a session the player explicitly ended.
 */
class SerializedSnapshotWriter<S : GameState>(
    private val store: SnapshotStore,
    private val sessionId: SessionId,
    private val snapshotFor: (S) -> GameSnapshot,
    private val isCompleted: (S) -> Boolean,
    private val writeContext: CoroutineContext = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<SnapshotWriteStatus>(SnapshotWriteStatus.Idle)
    val status: StateFlow<SnapshotWriteStatus> = _status.asStateFlow()

    private var lastSuccessfulState: S? = null
    private var explicitlyDiscarded: Boolean = false

    suspend fun persist(state: S): EmptyResult<DataError> = mutex.withLock {
        if (explicitlyDiscarded) return@withLock EmptyOk
        if (lastSuccessfulState == state) return@withLock EmptyOk

        val previousStatus = _status.value
        _status.value = SnapshotWriteStatus.Writing
        var completed = false
        val result = try {
            withContext(writeContext) {
                try {
                    completed = isCompleted(state)
                    if (completed) {
                        callStore { store.delete(sessionId) }
                    } else {
                        val snapshot = snapshotFor(state)
                        if (snapshot.sessionId != sessionId) {
                            Result.Failure(DataError.CorruptedData)
                        } else {
                            callStore { store.save(snapshot) }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Snapshot construction/validation is an in-process codec
                    // boundary. Ordinary failures here indicate state that
                    // cannot be represented safely, not a filesystem fault.
                    Result.Failure(DataError.CorruptedData)
                }
            }
        } catch (cancelled: CancellationException) {
            _status.value = previousStatus
            throw cancelled
        } catch (_: Exception) {
            Result.Failure(DataError.CorruptedData)
        } catch (fatal: Error) {
            _status.value = previousStatus
            throw fatal
        }

        when (result) {
            is Result.Success -> {
                lastSuccessfulState = state
                _status.value = if (completed) {
                    SnapshotWriteStatus.Deleted
                } else {
                    SnapshotWriteStatus.Saved
                }
            }
            is Result.Failure -> _status.value = SnapshotWriteStatus.Failed(result.error)
        }
        result
    }

    /**
     * Permanently removes this session after an explicit player decision.
     * On failure, persistence remains active and the caller must not navigate
     * away as though deletion succeeded.
     */
    suspend fun discard(): EmptyResult<DataError> = mutex.withLock {
        if (explicitlyDiscarded) return@withLock EmptyOk
        val previousStatus = _status.value
        _status.value = SnapshotWriteStatus.Writing
        val result = try {
            withContext(writeContext) {
                callStore { store.delete(sessionId) }
            }
        } catch (cancelled: CancellationException) {
            _status.value = previousStatus
            throw cancelled
        } catch (_: Exception) {
            Result.Failure(DataError.IoError("snapshot_io"))
        } catch (fatal: Error) {
            _status.value = previousStatus
            throw fatal
        }
        when (result) {
            is Result.Success -> {
                explicitlyDiscarded = true
                lastSuccessfulState = null
                _status.value = SnapshotWriteStatus.Deleted
            }
            is Result.Failure -> _status.value = SnapshotWriteStatus.Failed(result.error)
        }
        result
    }

    /**
     * [SnapshotStore] reports expected persistence failures as [Result]. If a
     * custom/platform implementation instead throws an ordinary exception,
     * keep that failure at the I/O boundary without exposing exception text.
     * Cancellation and fatal runtime errors deliberately escape to their
     * structured owner.
     */
    private suspend fun callStore(
        operation: suspend () -> EmptyResult<DataError>,
    ): EmptyResult<DataError> = try {
        operation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.Failure(DataError.IoError("snapshot_io"))
    }
}
