package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LastLightRecoveryTest {
    @Test
    fun conflated_membership_preserves_raw_interruption_without_replaying_domain_topology() = runTest {
        val fixture = LastLightBridgeFixture(this, reconcileMembers = true, requireStartHandshake = true)
        fixture.attachPeers(handshake = true)
        val before = fixture.session.currentState()
        val epoch = fixture.host.recoveryEpoch.value
        val offers = fixture.hostRoom.sent.count { it.second is HostMessage.SessionStarting }
        val revision = fixture.latestSnapshot(testAliceId).revision

        fixture.hostRoom.memberState.value = fixture.hostRoom.memberState.value.map {
            it.copy(connected = it.playerId != testAliceId)
        }
        fixture.bus.emitPeerLeft(testAliceId, "Alice")
        fixture.hostRoom.memberState.value = fixture.hostRoom.memberState.value.map { it.copy(connected = true) }
        fixture.bus.emitPeerReconnected(testAliceId, "Alice")
        // Neither source collector has run: members has already returned to
        // its original value, while the raw event queue retains the interruption.
        assertEquals(epoch, fixture.host.recoveryEpoch.value)
        runCurrent()

        assertTrue(fixture.host.recoveryEpoch.value > epoch)
        assertEquals(before, fixture.session.currentState())
        assertEquals(revision, fixture.latestSnapshot(testAliceId).revision)
        assertEquals(offers, fixture.hostRoom.sent.count { it.second is HostMessage.SessionStarting })
        assertEquals(0, fixture.bus.droppedPeerEventCount)
        fixture.close()
    }

    @Test
    fun connected_peer_cancels_queued_action_after_another_survivor_recovers() =
        verifyConnectedPeerRecovery(conflatePausedSnapshot = false)

    @Test
    fun connected_peer_cancels_queued_action_when_host_outbox_omits_the_paused_snapshot() =
        verifyConnectedPeerRecovery(conflatePausedSnapshot = true)

    private fun verifyConnectedPeerRecovery(conflatePausedSnapshot: Boolean) = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed, requireStartHandshake = true)
        val uiScheduler = TestCoroutineScheduler()
        val uiJob = SupervisorJob()
        val uiScope = CoroutineScope(uiJob + StandardTestDispatcher(uiScheduler))
        val gate = CompletableDeferred<Unit>()
        try {
            fixture.attachPeers(handshake = true)
            val peer = fixture.peers.getValue(testAliceId)
            val initial = fixture.session.currentState()
            val ownBefore = peer.controller.privateStateFor(testAliceId).value
            val epoch = peer.recoveryEpoch.value
            val revision = fixture.latestSnapshot(testAliceId).revision
            val actions = LastLightActionSubmission(uiScope, peer.controller::submit, peer.commandProgress)
            val ownCard = initial.privatePerPlayer.getValue(testAliceId).hand.first().id
            assertTrue(actions.trySubmit(
                PendingAction.PLAY_CARDS,
                LastLightAction.PlayCards(testAliceId, listOf(ownCard)),
                enabled = true,
                stillAllowed = { peer.recoveryEpoch.value == epoch },
            ))

            if (conflatePausedSnapshot) {
                // Block a same-revision snapshot already in Alice's send lane.
                // The real bounded outbox then replaces the pending paused
                // snapshot with the recovered revision before this lane resumes.
                fixture.hostRoom.snapshotSendGate = testAliceId to gate
                fixture.requestSnapshot(testAliceId)
            }
            fixture.bus.emitPeerLeft(testBobId, "Bob")
            runCurrent()
            assertEquals(setOf(testBobId), fixture.session.currentState().public.disconnectedPlayers)
            fixture.bus.emitPeerReconnected(testBobId, "Bob")
            runCurrent()
            assertTrue(fixture.session.currentState().public.disconnectedPlayers.isEmpty())
            if (conflatePausedSnapshot) assertEquals(epoch, peer.recoveryEpoch.value)
            gate.complete(Unit)
            runCurrent()

            val deliveredRevisions = fixture.hostRoom.sent
                .filter { it.first == SendTarget.Direct(testAliceId) }
                .mapNotNull { (it.second as? HostMessage.PlayerSnapshot)?.revision }
            assertEquals(!conflatePausedSnapshot, revision + 1L in deliveredRevisions)
            assertEquals(revision + 2L, deliveredRevisions.last())
            assertFalse(peer.connectionState.value.hostLost)
            assertFalse(peer.connectionState.value.selfOffline)
            assertTrue(peer.recoveryEpoch.value > epoch)
            assertEquals(ownBefore, peer.controller.privateStateFor(testAliceId).value)

            // The UI dispatcher has not run during either transition. Its
            // previously reserved callback now sees only a recovered table.
            uiScheduler.runCurrent()
            runCurrent()
            uiScheduler.runCurrent()
            assertNull(actions.pendingAction.value)
            assertEquals(initial, fixture.session.currentState())
            assertTrue(fixture.peerRooms.getValue(testAliceId).sent.none { it is PeerMessage.ClientCommand })
        } finally {
            gate.complete(Unit)
            uiJob.cancel()
            uiScheduler.runCurrent()
            fixture.close()
        }
    }
}
