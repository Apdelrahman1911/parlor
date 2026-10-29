package com.parlor.transport.p2p

/** Room-mutex-owned, constant-space guard against renewing an existing recovery timer. */
internal class RecoveryWindowBudget(private val elapsedMillis: () -> Long) {
    private data class Window(val deadline: Long, val startedAt: Long, val budget: Long)
    private var window: Window? = null

    fun remaining(deadline: Long, requestedDelay: Long, newWindow: Boolean): Long {
        val requested = requestedDelay.coerceAtLeast(0L)
        val now = elapsedMillis()
        if (now < 0L) return 0L // Platform failure sentinel must not grant a new window.
        val current = window?.takeIf { !newWindow && it.deadline == deadline }
            ?: Window(deadline, now, requested).also { window = it }
        val elapsed = now - current.startedAt
        // A platform counter must not roll back; an impossible/overflowed reading fails closed.
        if (now < current.startedAt || elapsed < 0L) return 0L
        return minOf(requested, (current.budget - elapsed).coerceAtLeast(0L))
    }
}

/** Monotonic process-local elapsed time; mobile implementations include device sleep. */
internal expect fun platformRecoveryElapsedMillis(): Long
