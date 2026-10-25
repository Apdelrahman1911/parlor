package com.parlor.app

import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.transport.ResumableSessionSelection
import com.parlor.networking.transport.RoomTransport
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One confirmation owns one deletion. Presentation never contains snapshot bytes or credentials. */
internal class ResumeDeletionRequest(val target: ResumeDeletionTarget, val label: String)

internal sealed interface ResumeDeletionTarget {
    data class Local(val sessionId: SessionId) : ResumeDeletionTarget
    class Multiplayer(val selection: ResumableSessionSelection?) : ResumeDeletionTarget
}

internal data class ResumeDeletionState(
    val request: ResumeDeletionRequest? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

internal class ResumeDeletionCoordinator(
    private val scope: CoroutineScope,
    private val store: SnapshotStore,
    private val transport: RoomTransport,
    private val isHome: () -> Boolean,
    private val onDeleted: () -> Unit,
) {
    private val mutableState = MutableStateFlow(ResumeDeletionState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null

    fun request(target: ResumeDeletionTarget, label: String) {
        if (!isHome() || mutableState.value.request != null) return
        mutableState.value = ResumeDeletionState(ResumeDeletionRequest(target, label))
    }

    fun cancel(request: ResumeDeletionRequest) {
        if (mutableState.value.request !== request || mutableState.value.busy) return
        mutableState.value = ResumeDeletionState()
    }

    fun confirm(request: ResumeDeletionRequest) {
        if (!isHome() || mutableState.value.request !== request || mutableState.value.busy) return
        mutableState.value = ResumeDeletionState(request, busy = true)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val deleted = delete(request.target)
                if (isHome() && mutableState.value.request === request) {
                    mutableState.value = if (deleted) ResumeDeletionState() else {
                        ResumeDeletionState(request, failed = true)
                    }
                    if (deleted) onDeleted()
                }
            } finally {
                if (mutableState.value.request === request && mutableState.value.busy) {
                    mutableState.value = ResumeDeletionState(request, failed = true)
                }
            }
        }
        operation = job
        job.start()
    }

    private suspend fun delete(target: ResumeDeletionTarget): Boolean = try {
        when (target) {
            is ResumeDeletionTarget.Local -> store.delete(target.sessionId) is Result.Success
            is ResumeDeletionTarget.Multiplayer -> target.selection?.let {
                transport.discardResumableSession(it) is Result.Success
            } == true
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        // Storage implementations normally return Result; do not expose platform exception text.
        false
    }

    /** Navigation invalidates callbacks, even when a storage operation ignores cancellation. */
    fun invalidate() {
        mutableState.value = ResumeDeletionState()
        operation?.cancel()
        operation = null
    }
}
