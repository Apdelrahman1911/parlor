package com.parlor.session.multidevice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Starts one session-owned operation exactly once and retains its observable
 * result independently of any UI collector.
 *
 * The caller's coroutine only installs the operation. Actual work runs in
 * [scope], which must be owned by the physical multiplayer session. Cancelling
 * a screen therefore cannot cancel a start handshake after a frame has already
 * been consumed. Session cancellation still propagates normally and is never
 * converted into an ordinary failure.
 */
class RetainedSessionOperation<T>(initialState: T) {
    private val startMutex = Mutex()
    private var started = false
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<T> = _state.asStateFlow()

    suspend fun start(
        scope: CoroutineScope,
        onUnexpectedFailure: (Exception) -> T,
        operation: suspend () -> T,
    ) {
        val shouldLaunch = startMutex.withLock {
            if (started) {
                false
            } else {
                started = true
                true
            }
        }
        if (!shouldLaunch) return

        scope.launch {
            _state.value = try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                onUnexpectedFailure(failure)
            }
        }
    }
}
