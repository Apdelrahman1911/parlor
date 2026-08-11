package com.parlor.transport.p2p

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryCandidateSchedulerTest {

    @Test
    fun sustained_candidate_flood_cannot_create_an_unbounded_retry_ledger() {
        val scheduler = DiscoveryCandidateScheduler(
            totalDeadlineMs = 30_000L,
            maxAttemptsPerCandidate = 1,
        )
        scheduler.update(
            (0 until 10_000).map { index ->
                DiscoveryCandidate("attacker-$index", "endpoint-$index")
            },
            nowMs = 0L,
        )

        var attempts = 0
        while (scheduler.next(0L) != null && attempts <= 64) {
            attempts += 1
        }

        assertEquals(
            DiscoveryCandidateScheduler.MAX_NEW_CANDIDATES_PER_UPDATE,
            attempts,
            "one discovery observation exceeded its candidate-admission throttle",
        )
        assertTrue(
            scheduler.trackedCandidateCount <= DiscoveryCandidateScheduler.MAX_TRACKED_CANDIDATES,
        )
    }

    @Test
    fun repeated_churn_never_exceeds_the_persistent_candidate_budget() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)

        repeat(1_000) { observation ->
            scheduler.update(
                (0 until 100).map { index ->
                    DiscoveryCandidate(
                        key = "candidate-${observation * 100 + index}",
                        endpointVersion = "endpoint-$observation-$index",
                    )
                },
                nowMs = observation.toLong(),
            )
            assertTrue(
                scheduler.trackedCandidateCount <=
                    DiscoveryCandidateScheduler.MAX_TRACKED_CANDIDATES,
            )
        }
    }

    @Test
    fun a_late_candidate_displaces_an_exhausted_candidate_at_capacity() {
        val scheduler = DiscoveryCandidateScheduler(
            totalDeadlineMs = 30_000L,
            maxAttemptsPerCandidate = 1,
            maxTrackedCandidates = 4,
            maxNewCandidatesPerUpdate = 4,
        )
        val existing = (0 until 4).map { index ->
            DiscoveryCandidate("existing-$index", "endpoint-$index")
        }
        scheduler.update(existing, nowMs = 0L)
        repeat(4) { assertNotNull(scheduler.next(0L)) }
        assertNull(scheduler.next(0L))

        val late = DiscoveryCandidate("late-correct-room", "endpoint-late")
        scheduler.update(existing + late, nowMs = 1L)

        assertEquals(late, assertNotNull(scheduler.next(1L)).candidate)
        assertEquals(4, scheduler.trackedCandidateCount)
    }

    @Test
    fun wrong_room_is_candidate_local_and_a_late_correct_room_is_selected_first() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val wrong = DiscoveryCandidate("wrong", "endpoint-1")
        scheduler.update(listOf(wrong), nowMs = 0L)
        val wrongAttempt = assertNotNull(scheduler.next(0L))
        assertEquals(wrong, wrongAttempt.candidate)
        scheduler.recordResult(wrongAttempt, DiscoveryAttemptResult.WrongRoom, nowMs = 1L)
        assertNull(scheduler.next(1L))

        val correct = DiscoveryCandidate("correct", "endpoint-1")
        scheduler.update(listOf(wrong, correct), nowMs = 1_000L)
        assertEquals(correct, assertNotNull(scheduler.next(1_000L)).candidate)
    }

    @Test
    fun transient_failures_back_off_then_reprobe_an_unchanged_candidate() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val candidate = DiscoveryCandidate("host", "endpoint-1")
        scheduler.update(listOf(candidate), 0L)

        val burstAttemptTimes = listOf(0L, 500L, 1_500L, 3_500L)
        burstAttemptTimes.forEach { now ->
            val attempt = assertNotNull(scheduler.next(now))
            assertEquals(candidate, attempt.candidate)
            scheduler.recordResult(attempt, DiscoveryAttemptResult.TransientFailure, nowMs = now)
            assertNull(scheduler.next(now))
        }

        // Four fast failures do not permanently blacklist a host for the rest
        // of the 30-second discovery window. A bounded recovery probe follows.
        assertNull(scheduler.next(8_499L))
        assertEquals(candidate, assertNotNull(scheduler.next(8_500L)).candidate)
    }

    @Test
    fun retry_budget_is_hard_bounded_for_one_unchanged_candidate() {
        val scheduler = DiscoveryCandidateScheduler(
            totalDeadlineMs = 60_000L,
            maxAttemptsPerCandidate = 5,
        )
        val candidate = DiscoveryCandidate("host", "endpoint-1")
        scheduler.update(listOf(candidate), 0L)

        repeat(5) { index ->
            val now = index * 5_000L
            val attempt = assertNotNull(scheduler.next(now))
            scheduler.recordResult(attempt, DiscoveryAttemptResult.WrongRoom, now)
        }

        assertNull(scheduler.next(59_999L))
        assertEquals(1L, scheduler.nextWakeDelayMs(59_999L))
    }

    @Test
    fun disappearance_and_reappearance_reset_budget_even_when_metadata_is_unchanged() {
        val scheduler = DiscoveryCandidateScheduler(
            totalDeadlineMs = 30_000L,
            maxAttemptsPerCandidate = 1,
        )
        val candidate = DiscoveryCandidate("host", "endpoint-1")
        scheduler.update(listOf(candidate), 0L)
        val first = assertNotNull(scheduler.next(0L))
        scheduler.recordResult(first, DiscoveryAttemptResult.WrongRoom, 1L)
        assertNull(scheduler.next(2L))

        scheduler.update(emptyList(), 3L)
        scheduler.update(listOf(candidate), 4L)

        assertEquals(candidate, assertNotNull(scheduler.next(4L)).candidate)
    }

    @Test
    fun changed_endpoint_version_is_a_new_incarnation_without_disappearance() {
        val scheduler = DiscoveryCandidateScheduler(
            totalDeadlineMs = 30_000L,
            maxAttemptsPerCandidate = 1,
        )
        val old = DiscoveryCandidate("host", "endpoint-1")
        scheduler.update(listOf(old), 0L)
        val oldAttempt = assertNotNull(scheduler.next(0L))
        scheduler.recordResult(oldAttempt, DiscoveryAttemptResult.IncompatibleProtocol, 1L)

        val restarted = DiscoveryCandidate("host", "endpoint-2")
        scheduler.update(listOf(restarted), 2L)

        assertEquals(restarted, assertNotNull(scheduler.next(2L)).candidate)
    }

    @Test
    fun unchanged_wrong_room_is_periodically_reprobed_for_an_unobservable_room_restart() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val candidate = DiscoveryCandidate("host", "stable-metadata")
        scheduler.update(listOf(candidate), 0L)
        val first = assertNotNull(scheduler.next(0L))
        scheduler.recordResult(first, DiscoveryAttemptResult.WrongRoom, 0L)

        assertNull(scheduler.next(4_999L))
        assertEquals(candidate, assertNotNull(scheduler.next(5_000L)).candidate)
    }

    @Test
    fun late_result_from_an_old_observation_cannot_back_off_the_new_incarnation() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val candidate = DiscoveryCandidate("host", "endpoint-1")
        scheduler.update(listOf(candidate), 0L)
        val staleAttempt = assertNotNull(scheduler.next(0L))

        scheduler.update(emptyList(), 1L)
        scheduler.update(listOf(candidate), 2L)
        scheduler.recordResult(staleAttempt, DiscoveryAttemptResult.TransientFailure, 3L)

        assertEquals(candidate, assertNotNull(scheduler.next(3L)).candidate)
    }

    @Test
    fun candidates_with_unused_budget_are_not_starved_by_an_older_retry() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val first = DiscoveryCandidate("a-host", "one")
        val second = DiscoveryCandidate("b-host", "one")
        scheduler.update(listOf(first, second), 0L)

        val firstAttempt = assertNotNull(scheduler.next(0L))
        assertEquals(first, firstAttempt.candidate)
        scheduler.recordResult(firstAttempt, DiscoveryAttemptResult.TransientFailure, 0L)

        assertEquals(second, assertNotNull(scheduler.next(0L)).candidate)
    }

    @Test
    fun each_attempt_deadline_is_clipped_by_the_total_deadline() {
        val scheduler = DiscoveryCandidateScheduler(
            totalDeadlineMs = 1_000L,
            perAttemptTimeoutMs = 5_000L,
        )
        val candidate = DiscoveryCandidate("host", "one")
        scheduler.update(listOf(candidate), 900L)

        val attempt = assertNotNull(scheduler.next(900L))
        assertEquals(1_000L, attempt.deadlineAtMs)
        assertEquals(100L, attempt.deadlineAtMs - 900L)
        assertNull(scheduler.next(1_000L))
        assertEquals(0L, scheduler.nextWakeDelayMs(1_000L))
    }

    @Test
    fun incompatible_version_has_deterministic_precedence_over_wrong_code_at_deadline() {
        val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = 30_000L)
        val wrong = DiscoveryCandidate("wrong", "one")
        val old = DiscoveryCandidate("old", "one")
        scheduler.update(listOf(wrong, old), 0L)
        val oldAttempt = assertNotNull(scheduler.next(0L))
        scheduler.recordResult(oldAttempt, DiscoveryAttemptResult.IncompatibleProtocol, 0L)
        val wrongAttempt = assertNotNull(scheduler.next(0L))
        scheduler.recordResult(wrongAttempt, DiscoveryAttemptResult.WrongRoom, 0L)

        assertEquals(DiscoveryFinalError.IncompatibleProtocol, scheduler.finalError())
    }
}
