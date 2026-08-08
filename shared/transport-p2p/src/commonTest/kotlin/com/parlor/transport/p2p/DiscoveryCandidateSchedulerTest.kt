package com.parlor.transport.p2p

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiscoveryCandidateSchedulerTest {

    @Test
    fun wrong_room_is_candidate_local_and_a_late_correct_room_is_still_selected() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val wrong = DiscoveryCandidate("wrong", "endpoint-1")
        scheduler.update(listOf(wrong), nowMs = 0L)
        assertEquals(wrong, scheduler.next(0L))
        scheduler.recordResult(wrong, DiscoveryAttemptResult.WrongRoom, nowMs = 1L)
        assertNull(scheduler.next(1L))

        val correct = DiscoveryCandidate("correct", "endpoint-1")
        scheduler.update(listOf(wrong, correct), nowMs = 5_000L)
        assertEquals(correct, scheduler.next(5_000L))
    }

    @Test
    fun transient_failures_use_bounded_backoff_and_stop_after_four_attempts() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val candidate = DiscoveryCandidate("host", "endpoint-1")
        scheduler.update(listOf(candidate), 0L)

        val attemptTimes = listOf(0L, 500L, 1_500L, 3_500L)
        attemptTimes.forEachIndexed { index, now ->
            assertEquals(candidate, scheduler.next(now))
            scheduler.recordResult(
                candidate,
                DiscoveryAttemptResult.TransientFailure,
                nowMs = now,
            )
            if (index < attemptTimes.lastIndex) assertNull(scheduler.next(now))
        }
        assertNull(scheduler.next(30_000L - 1L))
    }

    @Test
    fun disappearance_and_reappearance_reset_terminal_state_for_a_new_incarnation() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val first = DiscoveryCandidate("host", "endpoint-1")
        scheduler.update(listOf(first), 0L)
        scheduler.next(0L)
        scheduler.recordResult(first, DiscoveryAttemptResult.WrongRoom, 1L)

        scheduler.update(emptyList(), 2L)
        assertNull(scheduler.next(2L))
        val restarted = DiscoveryCandidate("host", "endpoint-2")
        scheduler.update(listOf(restarted), 3L)
        assertEquals(restarted, scheduler.next(3L))
    }

    @Test
    fun incompatible_version_has_deterministic_precedence_over_wrong_code_at_deadline() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val wrong = DiscoveryCandidate("wrong", "one")
        val old = DiscoveryCandidate("old", "one")
        scheduler.update(listOf(wrong, old), 0L)
        scheduler.next(0L)?.let {
            scheduler.recordResult(it, DiscoveryAttemptResult.WrongRoom, 0L)
        }
        scheduler.next(0L)?.let {
            scheduler.recordResult(it, DiscoveryAttemptResult.IncompatibleProtocol, 0L)
        }

        assertEquals(DiscoveryFinalError.IncompatibleProtocol, scheduler.finalError())
    }

    @Test
    fun deadline_and_wake_delays_are_bounded() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val candidate = DiscoveryCandidate("host", "one")
        scheduler.update(listOf(candidate), 0L)
        scheduler.next(0L)
        scheduler.recordResult(candidate, DiscoveryAttemptResult.TransientFailure, 0L)

        assertEquals(500L, scheduler.nextWakeDelayMs(0L))
        assertEquals(candidate, scheduler.next(500L))
        scheduler.recordResult(candidate, DiscoveryAttemptResult.WrongRoom, 500L)
        assertEquals(1L, scheduler.nextWakeDelayMs(29_999L))
        assertEquals(0L, scheduler.nextWakeDelayMs(30_000L))
        assertNull(scheduler.next(30_000L))
    }
}
