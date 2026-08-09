package com.parlor.session.multidevice

import com.parlor.networking.room.PeerEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
