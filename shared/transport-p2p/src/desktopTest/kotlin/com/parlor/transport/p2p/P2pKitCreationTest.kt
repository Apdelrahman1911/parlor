package com.parlor.transport.p2p

import com.parlor.networking.transport.HostConfig
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.PeerId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class P2pKitCreationTest {
    @Test
    fun successfulCreationTransfersOwnershipWithoutStoppingKit(): Unit = runTest {
        val kit = FakeP2pKit(PeerId("created"))
        try {
            val received = createOwnedP2pKit(StandardTestDispatcher(testScheduler, "factory")) { kit }
            assertSame(kit, received)
            assertEquals(0, kit.stopCalls)
        } finally {
            kit.stop()
        }
    }

    @Test
    fun cancellationBeforeDispatcherEntryDoesNotConstructKit(): Unit = runTest {
        val dispatcher = QueuedCreationDispatcher()
        var constructed = false
        val opening = async {
            createOwnedP2pKit(dispatcher) {
                constructed = true
                FakeP2pKit(PeerId("must-not-be-created"))
            }
        }
        runCurrent()
        assertEquals(1, dispatcher.pending)
        opening.cancel()
        dispatcher.runNext()
        opening.join()
        assertTrue(opening.isCancelled)
        assertFalse(constructed)
        assertEquals(0, dispatcher.pending)
    }

    @Test
    fun cancelledReturnDispatchStopsSuccessfullyConstructedKit(): Unit = runTest {
        val kit = FakeP2pKit(PeerId("cancelled-return"))
        val dispatcher = QueuedCreationDispatcher()
        var constructed = false
        var transferred = false
        val opening = async {
            createOwnedP2pKit(dispatcher) {
                constructed = true
                kit
            }.also { transferred = true }
        }
        try {
            runCurrent()
            dispatcher.runNext()
            assertTrue(constructed)
            assertFalse(transferred)
            assertEquals(0, kit.stopCalls)
            // Creation has finished; the successful result is queued on the
            // caller's dispatcher, where prompt cancellation discards it.
            opening.cancel()
            opening.join()
            assertTrue(opening.isCancelled)
            assertFalse(transferred)
            assertEquals(1, kit.stopCalls)
        } finally {
            if (kit.stopCalls == 0) kit.stop()
        }
    }

    @Test
    fun cancellationDuringSameDispatcherConstructionStopsKit(): Unit = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler, "same-caller-and-factory")
        verifyCancellationDuringCreation(dispatcher, dispatcher)
    }

    @Test
    fun cancellationDuringDifferentDispatcherConstructionStopsKit(): Unit = runTest {
        verifyCancellationDuringCreation(
            StandardTestDispatcher(testScheduler, "caller"),
            StandardTestDispatcher(testScheduler, "factory"),
        )
    }

    @Test
    fun cancelledOwnerAwaitsSuspendingNonCancellableStop(): Unit = runTest {
        val kit = FakeP2pKit(PeerId("suspending-stop"))
        val enteredStop = CompletableDeferred<Unit>()
        val releaseStop = CompletableDeferred<Unit>()
        kit.stopHandler = {
            enteredStop.complete(Unit)
            releaseStop.await()
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellation = CancellationException("synthetic caller cancellation")
        val opening = async(dispatcher) {
            val owner = currentCoroutineContext().job
            createOwnedP2pKit(dispatcher) {
                owner.cancel(cancellation)
                kit
            }
        }
        try {
            enteredStop.await()
            assertFalse(opening.isCompleted)
            assertEquals(0, kit.stopCalls)
            releaseStop.complete(Unit)
            assertPreservesOriginal(cancellation, assertFailsWith<CancellationException> { opening.await() })
            assertEquals(1, kit.stopCalls)
        } finally {
            releaseStop.complete(Unit)
            opening.join()
            if (kit.stopCalls == 0) kit.stop()
        }
    }

    @Test
    fun cleanupFailureDoesNotReplaceOriginalCallerCancellation(): Unit = runTest {
        val kit = FakeP2pKit(PeerId("failed-stop"))
        val cleanupFailure = IllegalStateException("synthetic stop failure")
        var attempts = 0
        kit.stopHandler = {
            attempts++
            throw cleanupFailure
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellation = CancellationException("synthetic caller cancellation")
        var observedAtHelperBoundary: CancellationException? = null
        val opening = async(dispatcher) {
            val owner = currentCoroutineContext().job
            try {
                createOwnedP2pKit(dispatcher) {
                    owner.cancel(cancellation)
                    kit
                }
            } catch (failure: CancellationException) {
                observedAtHelperBoundary = failure
                throw failure
            }
        }
        try {
            val failure = assertFailsWith<CancellationException> { opening.await() }
            assertPreservesOriginal(cancellation, failure)
            // Coroutines' later stacktrace recovery may copy only the original
            // cause, not suppressed exceptions. Inspect this helper's boundary.
            val boundary = checkNotNull(observedAtHelperBoundary)
            assertPreservesOriginal(cancellation, boundary)
            assertEquals(1, boundary.suppressedExceptions.size)
            assertPreservesOriginal(cleanupFailure, boundary.suppressedExceptions.single())
            assertEquals(1, attempts)
        } finally {
            kit.stopHandler = null
            if (kit.stopCalls == 0) kit.stop()
        }
    }

    @Test
    fun failedConstructionPreservesOriginalFailure(): Unit = runTest {
        val failure = IllegalStateException("synthetic construction failure")
        assertPreservesOriginal(
            failure,
            assertFailsWith<IllegalStateException> {
                createOwnedP2pKit(StandardTestDispatcher(testScheduler)) { throw failure }
            },
        )
    }

    @Test
    fun hostAndJoinPreserveFactoryCancellationWithoutStartingOrStoppingTwice(): Unit = runTest {
        for (host in listOf(true, false)) {
            val kit = FakeP2pKit(PeerId("cancelled-opening-$host"))
            val dispatcher = StandardTestDispatcher(testScheduler)
            val cancellation = CancellationException("synthetic opening cancelled")
            val transport = P2pKitRoomTransport(
                appId = AppId("com.parlor.test"),
                deviceName = "cancelled-opening",
                scope = backgroundScope,
                kitFactory = object : P2pKitFactory {
                    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit {
                        val owner = currentCoroutineContext().job
                        return createOwnedP2pKit(dispatcher) {
                            owner.cancel(cancellation)
                            kit
                        }
                    }
                },
            )
            val opening = async(dispatcher) {
                if (host) transport.host(HostConfig("Host")) else transport.join("ABC234", "Peer")
            }
            try {
                assertPreservesOriginal(cancellation, assertFailsWith<CancellationException> { opening.await() })
                assertEquals(listOf("stop"), kit.callLog)
                assertEquals(0, kit.incomingSessionsFlow.subscriptionCount.value)
                transport.notifyAppBackgrounded()
                transport.notifyAppForegrounded()
                runCurrent()
                assertEquals(0, kit.backgroundCalls)
                assertEquals(0, kit.foregroundCalls)
            } finally {
                if (kit.stopCalls == 0) kit.stop()
            }
        }
    }

    private suspend fun verifyCancellationDuringCreation(
        callerDispatcher: CoroutineDispatcher,
        factoryDispatcher: CoroutineDispatcher,
    ) = kotlinx.coroutines.coroutineScope {
        val kit = FakeP2pKit(PeerId("cancelled-construction"))
        val cancellation = CancellationException("synthetic caller cancellation")
        var transferred = false
        val opening = async(callerDispatcher) {
            val owner = currentCoroutineContext().job
            createOwnedP2pKit(factoryDispatcher) {
                owner.cancel(cancellation)
                kit
            }.also { transferred = true }
        }
        try {
            assertPreservesOriginal(cancellation, assertFailsWith<CancellationException> { opening.await() })
            assertFalse(transferred)
            assertEquals(1, kit.stopCalls)
        } finally {
            if (kit.stopCalls == 0) kit.stop()
        }
    }

    private fun assertPreservesOriginal(expected: Throwable, actual: Throwable) {
        // JVM coroutines 1.11.0 can copy an exception for stacktrace recovery;
        // the same class/message and exact original cause must remain intact.
        assertEquals(expected::class, actual::class)
        assertEquals(expected.message, actual.message)
        var cause: Throwable? = actual
        repeat(8) {
            if (cause === expected) return
            cause = cause?.cause
        }
        fail("Original failure was not preserved in the bounded cause chain")
    }
}

/** No threads or native P2pKit resources: explicitly park one dispatch hop. */
private class QueuedCreationDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()
    val pending: Int get() = queue.size

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    fun runNext() {
        queue.removeFirst().run()
    }
}
