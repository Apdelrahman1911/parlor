package com.parlor.session.multidevice

import com.parlor.networking.room.PeerEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeerConnectionTrackerTest {
    @Test
    fun `connection state remains observable without an event collector`() = runTest {
        val tracker = PeerConnectionTracker(this, 100L) {}

        tracker.handle(PeerEvent.HostLost)
        tracker.handle(PeerEvent.SelfOffline)
        assertEquals(PeerConnectionState(hostLost = true, selfOffline = true), tracker.state.value)

        tracker.handle(PeerEvent.HostRestored)
        assertEquals(PeerConnectionState(), tracker.state.value)
        tracker.close()
    }

    @Test
    fun `duplicate host loss does not extend the original deadline`() = runTest {
        var expirations = 0
        val tracker = PeerConnectionTracker(this, 100L) { expirations++ }

        tracker.handle(PeerEvent.HostLost)
        advanceTimeBy(75L)
        tracker.handle(PeerEvent.HostLost)
        advanceTimeBy(26L)
        runCurrent()

        assertEquals(1, expirations)
        assertTrue(tracker.state.value.hostLost)
        tracker.close()
    }

    @Test
    fun `restore cancels expiry and marks the peer online`() = runTest {
        var expired = false
        val tracker = PeerConnectionTracker(this, 100L) { expired = true }
        tracker.handle(PeerEvent.SelfOffline)
        tracker.handle(PeerEvent.HostLost)

        advanceTimeBy(50L)
        tracker.handle(PeerEvent.HostRestored)
        advanceTimeBy(100L)
        runCurrent()

        assertFalse(expired)
        assertFalse(tracker.state.value.hostLost)
        assertFalse(tracker.state.value.selfOffline)
        tracker.close()
    }

    @Test
    fun `close cancels an outstanding host loss deadline`() = runTest {
        var expired = false
        val tracker = PeerConnectionTracker(this, 100L) { expired = true }
        tracker.handle(PeerEvent.HostLost)

        tracker.close()
        advanceTimeBy(101L)
        runCurrent()

        assertFalse(expired)
    }

    @Test
    fun `tracker owns and removes all timer jobs when closed`() = runTest {
        val parent = coroutineContext[Job] ?: error("runTest must provide a parent Job")
        val tracker = PeerConnectionTracker(this, 10_000L) {}

        tracker.handle(PeerEvent.HostLost)
        runCurrent()
        assertTrue(parent.children.any(), "host-loss timer must be a structured child")

        tracker.close()
        runCurrent()

        assertFalse(parent.children.any(), "close must leave no tracker timer in the parent scope")

        tracker.handle(PeerEvent.HostLost)
        runCurrent()
        assertFalse(parent.children.any(), "closed tracker must reject stale callbacks")
    }

    @Test
    fun `close waits for an expiry callback that already started`() = runTest {
        val callbackEntered = CompletableDeferred<Unit>()
        val releaseCallback = CompletableDeferred<Unit>()
        var callbackCompleted = false
        val tracker = PeerConnectionTracker(this, 100L) {
            callbackEntered.complete(Unit)
            // Model a platform callback already inside a non-cancellable
            // cleanup section. close() must not return until it has unwound.
            withContext(NonCancellable) {
                releaseCallback.await()
                callbackCompleted = true
            }
        }

        tracker.handle(PeerEvent.HostLost)
        advanceTimeBy(100L)
        runCurrent()
        assertTrue(callbackEntered.isCompleted)

        val close = launch { tracker.close() }
        runCurrent()
        assertFalse(close.isCompleted, "teardown must join an in-flight stale callback")

        releaseCallback.complete(Unit)
        runCurrent()
        assertTrue(callbackCompleted)
        assertTrue(close.isCompleted)
    }
}
