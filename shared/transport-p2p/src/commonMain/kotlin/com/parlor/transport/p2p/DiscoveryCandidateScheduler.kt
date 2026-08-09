package com.parlor.transport.p2p

/** Transport-neutral snapshot of one currently advertised host candidate. */
internal data class DiscoveryCandidate(
    val key: String,
    /** Changes when the advertised endpoint/service incarnation changes. */
    val endpointVersion: String,
)

/**
 * One scheduler-owned dial lease.
 *
 * [observationGeneration] prevents a late result from an older service
 * incarnation from poisoning a candidate that disappeared/reappeared (or
 * changed endpoint metadata) while the attempt was in flight.
 */
internal data class DiscoveryAttempt(
    val candidate: DiscoveryCandidate,
    val observationGeneration: Long,
    /** Absolute scheduler time; the adapter must cancel the dial at this point. */
    val deadlineAtMs: Long,
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
    private val perAttemptTimeoutMs: Long = DEFAULT_PER_ATTEMPT_TIMEOUT_MS,
    private val maxAttemptsPerCandidate: Int = MAX_ATTEMPTS_PER_CANDIDATE,
) {
    private data class CandidateState(
        var endpointVersion: String,
        var visible: Boolean,
        var generation: Long,
        var totalAttempts: Int = 0,
        var transientFailuresInBurst: Int = 0,
        var nextEligibleAtMs: Long = 0L,
    )

    init {
        require(totalDeadlineMs >= 0L) { "totalDeadlineMs must be non-negative" }
        require(perAttemptTimeoutMs > 0L) { "perAttemptTimeoutMs must be positive" }
        require(maxAttemptsPerCandidate > 0) { "maxAttemptsPerCandidate must be positive" }
    }

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
                    nextEligibleAtMs = nowMs,
                )
                !existing.visible || existing.endpointVersion != candidate.endpointVersion -> {
                    existing.endpointVersion = candidate.endpointVersion
                    existing.visible = true
                    existing.generation += 1L
                    existing.totalAttempts = 0
                    existing.transientFailuresInBurst = 0
                    existing.nextEligibleAtMs = nowMs
                }
                else -> existing.visible = true
            }
        }
    }

    /** Returns one eligible, deadline-bounded dial lease and consumes one attempt. */
    fun next(nowMs: Long): DiscoveryAttempt? {
        if (nowMs >= totalDeadlineMs) return null
        val selected = states.entries
            .asSequence()
            .filter { (_, state) ->
                state.visible &&
                    state.totalAttempts < maxAttemptsPerCandidate &&
                    state.nextEligibleAtMs <= nowMs
            }
            .sortedWith(
                // Candidates that have consumed less of their retry budget go
                // first. A late correct room therefore pre-empts an older wrong
                // or unreachable room without starving either candidate.
                compareBy<Map.Entry<String, CandidateState>> { it.value.totalAttempts }
                    .thenBy { it.value.nextEligibleAtMs }
                    .thenBy { it.key },
            )
            .firstOrNull()
            ?: return null
        selected.value.totalAttempts += 1
        val remainingMs = totalDeadlineMs - nowMs
        return DiscoveryAttempt(
            candidate = DiscoveryCandidate(selected.key, selected.value.endpointVersion),
            observationGeneration = selected.value.generation,
            deadlineAtMs = nowMs + minOf(perAttemptTimeoutMs, remainingMs),
        )
    }

    fun recordResult(
        attempt: DiscoveryAttempt,
        result: DiscoveryAttemptResult,
        nowMs: Long,
    ) {
        val candidate = attempt.candidate
        val state = states[candidate.key] ?: return
        if (
            state.generation != attempt.observationGeneration ||
            state.endpointVersion != candidate.endpointVersion
        ) {
            return
        }
        when (result) {
            DiscoveryAttemptResult.TransientFailure -> {
                state.transientFailuresInBurst += 1
                if (state.transientFailuresInBurst >= TRANSIENT_BURST_ATTEMPTS) {
                    // Four fast retries cover ordinary discovery/dial races. A
                    // longer recovery probe then keeps the same advertised host
                    // eligible if Wi-Fi or its listener recovers without a new
                    // discovery emission.
                    state.transientFailuresInBurst = 0
                    state.nextEligibleAtMs = nowMs + RECOVERY_PROBE_COOLDOWN_MS
                } else {
                    state.nextEligibleAtMs = nowMs +
                        backoffAfterTransientFailure(state.transientFailuresInBurst)
                }
            }
            DiscoveryAttemptResult.WrongRoom -> {
                sawWrongRoom = true
                state.transientFailuresInBurst = 0
                // P2pKit rc2 does not expose a Bonjour service-incarnation id.
                // Re-probing after a long cooldown is therefore the only way to
                // observe a host that recreated a different room under the same
                // stable PeerId/name without first disappearing from the list.
                state.nextEligibleAtMs = nowMs + WRONG_ROOM_REPROBE_COOLDOWN_MS
            }
            DiscoveryAttemptResult.IncompatibleProtocol -> {
                sawIncompatibleProtocol = true
                state.transientFailuresInBurst = 0
                state.nextEligibleAtMs = nowMs + INCOMPATIBLE_REPROBE_COOLDOWN_MS
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
                    it.totalAttempts < maxAttemptsPerCandidate
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

    private fun backoffAfterTransientFailure(attempt: Int): Long = when (attempt) {
        FIRST_TRANSIENT_ATTEMPT -> FIRST_TRANSIENT_BACKOFF_MS
        SECOND_TRANSIENT_ATTEMPT -> SECOND_TRANSIENT_BACKOFF_MS
        THIRD_TRANSIENT_ATTEMPT -> THIRD_TRANSIENT_BACKOFF_MS
        else -> RECOVERY_PROBE_COOLDOWN_MS
    }

    companion object {
        /** Hard bound even when failures return immediately. */
        const val MAX_ATTEMPTS_PER_CANDIDATE: Int = 16
        const val DEFAULT_PER_ATTEMPT_TIMEOUT_MS: Long = 5_000L
        private const val TRANSIENT_BURST_ATTEMPTS: Int = 4
        private const val RECOVERY_PROBE_COOLDOWN_MS: Long = 5_000L
        private const val WRONG_ROOM_REPROBE_COOLDOWN_MS: Long = 5_000L
        private const val INCOMPATIBLE_REPROBE_COOLDOWN_MS: Long = 10_000L
        private const val FIRST_TRANSIENT_ATTEMPT: Int = 1
        private const val SECOND_TRANSIENT_ATTEMPT: Int = 2
        private const val THIRD_TRANSIENT_ATTEMPT: Int = 3
        private const val FIRST_TRANSIENT_BACKOFF_MS: Long = 500L
        private const val SECOND_TRANSIENT_BACKOFF_MS: Long = 1_000L
        private const val THIRD_TRANSIENT_BACKOFF_MS: Long = 2_000L
    }
}

internal enum class DiscoveryFinalError {
    IncompatibleProtocol,
    WrongCode,
    Timeout,
}
