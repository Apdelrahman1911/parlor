package com.parlor.session.passandplay

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QueuedEventCancellationTest {
    @Test
    fun cancelling_a_queued_batch_cannot_release_events_past_its_active_predecessor() = runTest {
        val root = EventEmissionCompletion().also { it.complete() }
        val firstDone = EventEmissionCompletion()
        val cancelledDone = EventEmissionCompletion()
        val lastDone = EventEmissionCompletion()
        val releaseFirst = CompletableDeferred<Unit>()
        val emitted = mutableListOf<Int>()
        val first = OrderedEventEmissionTurn(root, firstDone, listOf(1, 2))
        val cancelled = OrderedEventEmissionTurn(firstDone, cancelledDone, listOf(3))
        val last = OrderedEventEmissionTurn(cancelledDone, lastDone, listOf(4))

        backgroundScope.launch {
            first.emitTo { event ->
                emitted += event
                if (event == 1) releaseFirst.await()
            }
        }
        val cancelledJob = backgroundScope.launch { cancelled.emitTo { emitted += it } }
        val lastJob = backgroundScope.launch { last.emitTo { emitted += it } }
        runCurrent()
        assertEquals(listOf(1), emitted)

        cancelledJob.cancelAndJoin()
        runCurrent()
        assertFalse(lastJob.isCompleted)
        assertEquals(listOf(1), emitted)

        releaseFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2, 4), emitted)
        assertTrue(lastJob.isCompleted)
    }

    @Test
    fun consecutive_cancelled_batches_preserve_the_original_ordering_gate() = runTest {
        val root = EventEmissionCompletion()
        var previous = root
        val emitted = mutableListOf<Int>()
        repeat(32) {
            val completion = EventEmissionCompletion()
            val turn = OrderedEventEmissionTurn(previous, completion, listOf(-1))
            val job = backgroundScope.launch { turn.emitTo { emitted += it } }
            runCurrent()
            job.cancelAndJoin()
            previous = completion
        }
        val lastDone = EventEmissionCompletion()
        val last = OrderedEventEmissionTurn(previous, lastDone, listOf(1))
        val lastJob = backgroundScope.launch { last.emitTo { emitted += it } }
        runCurrent()
        assertEquals(emptyList(), emitted)
        assertFalse(lastJob.isCompleted)

        root.complete()
        runCurrent()
        assertEquals(listOf(1), emitted)
        assertTrue(lastJob.isCompleted)
    }

    @Test
    fun cancellation_after_own_emission_starts_releases_the_successor_without_replaying() = runTest {
        val root = EventEmissionCompletion().also { it.complete() }
        val cancelledDone = EventEmissionCompletion()
        val lastDone = EventEmissionCompletion()
        val release = CompletableDeferred<Unit>()
        val emitted = mutableListOf<Int>()
        val cancelled = OrderedEventEmissionTurn(root, cancelledDone, listOf(1, 2))
        val last = OrderedEventEmissionTurn(cancelledDone, lastDone, listOf(3))
        val job = backgroundScope.launch {
            cancelled.emitTo { event ->
                emitted += event
                release.await()
            }
        }
        val lastJob = backgroundScope.launch { last.emitTo { emitted += it } }
        runCurrent()
        assertEquals(listOf(1), emitted)

        job.cancelAndJoin()
        runCurrent()
        assertEquals(listOf(1, 3), emitted)
        assertTrue(lastJob.isCompleted)
    }

    @Test
    fun a_deep_skipped_gate_chain_is_followed_without_recursive_completion_callbacks() = runTest {
        val root = EventEmissionCompletion()
        var previous = root
        // Direct gate construction stresses traversal depth, not 10,000 real submits.
        repeat(10_000) {
            previous = EventEmissionCompletion().also { it.complete(after = previous) }
        }
        val emitted = mutableListOf<Int>()
        val last = OrderedEventEmissionTurn(previous, EventEmissionCompletion(), listOf(1))
        val job = backgroundScope.launch { last.emitTo { emitted += it } }
        runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(emptyList(), emitted)
        root.complete()
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(listOf(1), emitted)
    }

    @Test
    fun a_failed_emitter_preserves_the_exception_and_releases_the_next_batch() = runTest {
        val root = EventEmissionCompletion().also { it.complete() }
        val failedDone = EventEmissionCompletion()
        val failed = OrderedEventEmissionTurn(root, failedDone, listOf(1, 2))
        val emitted = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            failed.emitTo { event ->
                emitted += event
                error("Synthetic emitter failure")
            }
        }
        OrderedEventEmissionTurn(failedDone, EventEmissionCompletion(), listOf(3)).emitTo { emitted += it }
        assertEquals(listOf(1, 3), emitted)
    }
}
