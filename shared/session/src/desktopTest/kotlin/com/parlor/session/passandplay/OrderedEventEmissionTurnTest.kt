package com.parlor.session.passandplay

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OrderedEventEmissionTurnTest {
    @Test
    fun later_batch_waits_for_the_complete_earlier_batch() = runBlocking {
        val root = CompletableDeferred<Unit>().also { it.complete(Unit) }
        val firstDone = CompletableDeferred<Unit>()
        val secondDone = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val emitted = mutableListOf<Int>()
        val first = OrderedEventEmissionTurn(root, firstDone, listOf(1, 2))
        val second = OrderedEventEmissionTurn(firstDone, secondDone, listOf(3))

        val firstJob = async {
            first.emitTo { event ->
                emitted += event
                if (event == 1) releaseFirst.await()
            }
        }
        while (emitted.isEmpty()) yield()
        val secondJob = async { second.emitTo { event -> emitted.add(event) } }
        yield()

        assertEquals(listOf(1), emitted)
        assertFalse(secondJob.isCompleted)
        releaseFirst.complete(Unit)
        firstJob.await()
        secondJob.await()
        assertEquals(listOf(1, 2, 3), emitted)
    }

    @Test
    fun cancelled_batch_releases_its_successor() = runBlocking {
        val root = CompletableDeferred<Unit>()
        val cancelledDone = CompletableDeferred<Unit>()
        val successorDone = CompletableDeferred<Unit>()
        val cancelled = OrderedEventEmissionTurn(root, cancelledDone, listOf(1))
        val successor = OrderedEventEmissionTurn(cancelledDone, successorDone, listOf(2))
        val emitted = mutableListOf<Int>()

        val cancelledJob = async { cancelled.emitTo { event -> emitted.add(event) } }
        yield()
        cancelledJob.cancelAndJoin()
        successor.emitTo { event -> emitted.add(event) }

        assertEquals(listOf(2), emitted)
    }
}
