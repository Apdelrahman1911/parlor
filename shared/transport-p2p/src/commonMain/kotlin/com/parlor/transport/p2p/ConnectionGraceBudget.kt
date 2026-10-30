package com.parlor.transport.p2p

/** Immutable, sleep-aware short-grace stamp; safe for command getters outside the room mutex. */
internal class ConnectionGraceBudget(
    private val startedAt: Long,
    private val deadline: Long,
    private val observedAtStart: Long,
    private val elapsedMillis: () -> Long,
) {
    private val elapsedAtStart = elapsedMillis()
    private val budget = if (observedAtStart in startedAt until deadline) {
        (deadline - observedAtStart).coerceIn(0L, AppLifecycleRoomCoordinator.CONNECTION_GRACE_MS)
    } else 0L

    fun remaining(epochHint: Long): Long {
        val spent = spent()
        if (spent < 0L) return 0L
        if (epochHint !in startedAt until deadline) return 0L
        return minOf(deadline - epochHint, budget - spent).coerceAtLeast(0L)
    }

    /** Pass elapsed time to hard recovery too, so a delayed timer cannot renew its total budget. */
    fun observedAt(epochHint: Long): Long {
        val spent = spent()
        if (spent < 0L) return Long.MAX_VALUE
        val projected = if (observedAtStart > Long.MAX_VALUE - spent) Long.MAX_VALUE else observedAtStart + spent
        return maxOf(epochHint, projected)
    }

    private fun spent(): Long {
        val now = elapsedMillis()
        val spent = now - elapsedAtStart
        return if (elapsedAtStart >= 0L && now >= elapsedAtStart && spent >= 0L) spent else -1L
    }
}
