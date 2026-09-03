package com.parlor.networking.testing

import com.parlor.core.ids.PlayerId
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InMemoryRoomBusTest {
    @Test
    fun `direct send to an unknown peer fails instead of disappearing`() = runTest {
        val bus = InMemoryRoomBus()

        assertFailsWith<NoSuchElementException> {
            bus.fromHost(
                SendTarget.Direct(PlayerId("missing")),
                HostMessage.AdmissionPending(PlayerId("missing")),
            )
        }
    }

    @Test
    fun `peer to host overflow is non blocking and observable`() = runTest {
        val bus = InMemoryRoomBus()

        repeat(64) { bus.fromPeer(PeerMessage.LeaveNotice) }
        bus.fromPeer(PeerMessage.LeaveNotice)

        assertEquals(1, bus.droppedHostMessageCount)
    }

    @Test
    fun `broadcast skips a full peer without blocking other peers`() = runTest {
        val bus = InMemoryRoomBus()
        val fullPeer = PlayerId("full")
        val availablePeer = PlayerId("available")
        bus.registerPeer(fullPeer)
        bus.registerPeer(availablePeer)
        val filler = HostMessage.AdmissionPending(fullPeer)
        repeat(32) { bus.fromHost(SendTarget.Direct(fullPeer), filler) }

        val broadcast = HostMessage.AdmissionPending(availablePeer)
        bus.fromHost(SendTarget.Broadcast, broadcast)

        assertEquals(1, bus.droppedPeerMessageCount)
        assertEquals(broadcast, bus.peerMessagesIn(availablePeer).first())
    }

    @Test
    fun `peer event overflow is non blocking and observable`() = runTest {
        val bus = InMemoryRoomBus()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.peerEvents.collect { awaitCancellation() }
        }

        repeat(33) { bus.emitHostLost() }

        assertTrue(bus.peerEventSubscriberCount > 0)
        assertEquals(1, bus.droppedPeerEventCount)
        collector.cancel()
    }
}
