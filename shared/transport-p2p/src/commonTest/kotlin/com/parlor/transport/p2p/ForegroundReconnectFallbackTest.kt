package com.parlor.transport.p2p

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundReconnectFallbackTest {
    @Test
    fun nativeRecoveryBeforeDeadlineCancelsTheFallback() = runTest {
        var calls = 0
        val fallback = ForegroundReconnectFallback(backgroundScope) { calls++ }
        runCurrent()
        advanceTimeBy(999L)
        fallback.cancelIfWaiting()
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(0, calls)
    }

    @Test
    fun lateConnectedCannotInterruptAClaimedRetirementTransaction() = runTest {
        val release = CompletableDeferred<Unit>()
        var started = false
        var completed = false
        val fallback = ForegroundReconnectFallback(backgroundScope) {
            started = true
            release.await()
            completed = true
        }
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(started)
        fallback.cancelIfWaiting()
        release.complete(Unit)
        runCurrent()
        assertTrue(completed)
    }

    @Test
    fun closingTheOwningSessionStillCancelsAnAlreadyClaimedFallback() = runTest {
        val release = CompletableDeferred<Unit>()
        var started = false
        var completed = false
        val owner = backgroundScope.launch {
            ForegroundReconnectFallback(this) {
                started = true
                release.await()
                completed = true
            }
            awaitCancellation()
        }
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(started)
        owner.cancelAndJoin()
        release.complete(Unit)
        runCurrent()
        assertFalse(completed)
    }
}
