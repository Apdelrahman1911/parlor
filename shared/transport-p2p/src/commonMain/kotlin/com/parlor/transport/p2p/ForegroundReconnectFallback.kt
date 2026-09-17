package com.parlor.transport.p2p

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Owned by one session collector, not by the lifetime of the transport kit. */
internal class ForegroundReconnectFallback(scope: CoroutineScope, resume: suspend () -> Unit) {
    private val waiting = MutableStateFlow(true)
    private val job = scope.launch {
        delay(NATIVE_RECONNECT_GRACE_MS)
        if (waiting.compareAndSet(true, false)) resume()
    }

    fun cancelIfWaiting() {
        // Once promotion begins, a late Connected emission must not cancel it
        // between retirement and installing the secure resume job. The room
        // rechecks physical/lifecycle ownership before committing retirement;
        // Leave/replacement still cancel this entire session-owned child.
        if (waiting.compareAndSet(true, false)) job.cancel()
    }

    private companion object {
        const val NATIVE_RECONNECT_GRACE_MS = 1_000L
    }
}
