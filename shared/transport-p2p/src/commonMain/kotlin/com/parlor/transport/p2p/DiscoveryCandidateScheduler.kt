package com.parlor.transport.p2p

/** Transport-neutral snapshot of one currently advertised host candidate. */
internal data class DiscoveryCandidate(
    val key: String,
    /** Changes when the advertised endpoint/service incarnation changes. */
    val endpointVersion: String,
)

internal enum class DiscoveryAttemptResult {
    TransientFailure,
    WrongRoom,
    IncompatibleProtocol,
}

/**
 * Pure, monotonic-time candidate scheduler. It owns no coroutines or sockets;
 * the adapter feeds discovery snapshots and records each completed attempt.
 */
internal class DiscoveryCandidateScheduler(
    private val totalDeadlineMs: Long,
    private val maxAttemptsPerCandidate: Int = MAX_ATTEMPTS_PER_CANDIDATE,
) {
    private data class CandidateState(
        var endpointVersion: String,
        var visible: Boolean,
        var generation: Long,
        var attempts: Int = 0,
        var nextEligibleAtMs: Long = 0L,
        var terminalResult: DiscoveryAttemptResult? = null,
    )

    private val states = mutableMapOf<String, CandidateState>()
    private var sawWrongRoom: Boolean = false
    private var sawIncompatibleProtocol: Boolean = false

    fun update(candidates: List<DiscoveryCandidate>, nowMs: Long) {
        val distinct = candidates.distinctBy(DiscoveryCandidate::key)
        val visibleKeys = distinct.mapTo(mutableSetOf(), DiscoveryCandidate::key)
        states.forEach { (key, state) ->
            if (key !in visibleKeys) state.visible = false
        }
        distinct.forEach { candidate ->
            val existing = states[candidate.key]
            when {
                existing == null -> states[candidate.key] = CandidateState(
                    endpointVersion = candidate.endpointVersion,
                    visible = true,
                    generation = 1L,
                )
                !existing.visible || existing.endpointVersion != candidate.endpointVersion -> {
                    existing.endpointVersion = candidate.endpointVersion
                    existing.visible = true
                    existing.generation += 1L
                    existing.attempts = 0
                    existing.nextEligibleAtMs = nowMs
                    existing.terminalResult = null
                }
                else -> existing.visible = true
            }
        }
    }

    /** Returns one eligible candidate and atomically consumes one attempt. */
    fun next(nowMs: Long): DiscoveryCandidate? {
        if (nowMs >= totalDeadlineMs) return null
        val selected = states.entries
            .asSequence()
            .filter { (_, state) ->
                state.visible &&
                    state.terminalResult == null &&
                    state.attempts < maxAttemptsPerCandidate &&
                    state.nextEligibleAtMs <= nowMs
            }
            .sortedWith(
                compareBy<Map.Entry<String, CandidateState>> { it.value.attempts }
                    .thenBy { it.value.nextEligibleAtMs }
                    .thenBy { it.key },
            )
            .firstOrNull()
            ?: return null
        selected.value.attempts += 1
        return DiscoveryCandidate(selected.key, selected.value.endpointVersion)
    }

    fun recordResult(
        candidate: DiscoveryCandidate,
        result: DiscoveryAttemptResult,
        nowMs: Long,
    ) {
        val state = states[candidate.key] ?: return
        if (state.endpointVersion != candidate.endpointVersion) return
        when (result) {
            DiscoveryAttemptResult.TransientFailure -> {
                if (state.attempts >= maxAttemptsPerCandidate) {
                    state.terminalResult = result
                } else {
                    state.nextEligibleAtMs = nowMs + backoffAfterAttempt(state.attempts)
                }
            }
            DiscoveryAttemptResult.WrongRoom -> {
                sawWrongRoom = true
                state.terminalResult = result
            }
            DiscoveryAttemptResult.IncompatibleProtocol -> {
                sawIncompatibleProtocol = true
                state.terminalResult = result
            }
        }
    }

    /** Delay until retry/deadline; discovery-state changes may wake the adapter sooner. */
    fun nextWakeDelayMs(nowMs: Long): Long {
        if (nowMs >= totalDeadlineMs) return 0L
        val eligibleAt = states.values
            .asSequence()
            .filter {
                it.visible &&
                    it.terminalResult == null &&
                    it.attempts < maxAttemptsPerCandidate
            }
            .map(CandidateState::nextEligibleAtMs)
            .minOrNull()
            ?: totalDeadlineMs
        return (minOf(eligibleAt, totalDeadlineMs) - nowMs).coerceAtLeast(0L)
    }

    fun finalError(): DiscoveryFinalError = when {
        sawIncompatibleProtocol -> DiscoveryFinalError.IncompatibleProtocol
        sawWrongRoom -> DiscoveryFinalError.WrongCode
        else -> DiscoveryFinalError.Timeout
    }

    private fun backoffAfterAttempt(attempt: Int): Long = when (attempt) {
        1 -> 500L
        2 -> 1_000L
        3 -> 2_000L
        4 -> 4_000L
        else -> 5_000L
    }

    companion object {
        const val MAX_ATTEMPTS_PER_CANDIDATE: Int = 4
    }
}

internal enum class DiscoveryFinalError {
    IncompatibleProtocol,
    WrongCode,
    Timeout,
}
