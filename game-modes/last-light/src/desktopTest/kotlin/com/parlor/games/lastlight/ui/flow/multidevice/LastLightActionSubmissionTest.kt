package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.PeerMessage
import com.parlor.session.multidevice.PeerCommandProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LastLightActionSubmissionTest {
    @Test
    fun rapid_host_clicks_reserve_synchronously_and_commit_one_play() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testHostId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val actions = LastLightActionSubmission(backgroundScope, fixture.host::submitHostAction)
        val card = fixture.session.currentState().privatePerPlayer.getValue(testHostId).hand.first()
        val play = LastLightAction.PlayCards(testHostId, listOf(card.id))
        assertTrue(actions.trySubmit(PendingAction.PLAY_CARDS, play, enabled = true))
        assertFalse(actions.trySubmit(PendingAction.PLAY_CARDS, play, enabled = true))
        runCurrent()
        assertEquals(1L, fixture.session.currentState().public.acceptedPlaySequence)
        assertEquals(4, fixture.session.currentState().privatePerPlayer.getValue(testHostId).hand.size)
        assertNull(actions.pendingAction.value)
        fixture.close()
    }

    @Test
    fun queued_actions_recheck_live_foreground_epoch_and_route_before_reducing() = runTest {
        repeat(4) { interruption ->
            val seed = seedWithState { it.public.turnPlayerId == testHostId.raw }
            val fixture = LastLightBridgeFixture(this, seed = seed)
            fixture.attachPeers()
            val actions = LastLightActionSubmission(backgroundScope, fixture.host::submitHostAction)
            val initial = fixture.session.currentState()
            val visibility = MutableStateFlow(LastLightProcessVisibility(true, 5L))
            val mounted = MutableStateFlow(true)
            val recovery = LastLightRecoveryEpoch()
            val card = initial.privatePerPlayer.getValue(testHostId).hand.first()
            val permitted = {
                mounted.value && visibility.value.isForeground && visibility.value.concealmentEpoch == 5L &&
                    recovery.value.value == 0L
            }
            assertTrue(actions.trySubmit(PendingAction.PLAY_CARDS, LastLightAction.PlayCards(testHostId, listOf(card.id)), true, permitted))
            when (interruption) {
                0 -> visibility.value = LastLightProcessVisibility(false, 6L)
                1 -> visibility.value = LastLightProcessVisibility(true, 6L)
                2 -> recovery.advance()
                else -> mounted.value = false
            }
            runCurrent()
            assertEquals(initial, fixture.session.currentState())
            assertNull(actions.pendingAction.value)
            assertFalse(actions.trySubmit(PendingAction.PLAY_CARDS, LastLightAction.PlayCards(testHostId, listOf(card.id)), true, permitted))
            fixture.close()
        }
    }

    @Test
    fun peer_stays_pending_until_the_receipt_and_new_recipient_snapshot_are_installed() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val peer = fixture.peers.getValue(testAliceId)
        val actions = LastLightActionSubmission(backgroundScope, peer.controller::submit, peer.commandProgress)
        val initial = peer.controller.privateStateFor(testAliceId).value.state
        val card = initial.privatePerPlayer.getValue(testAliceId).hand.first()
        val play = LastLightAction.PlayCards(testAliceId, listOf(card.id))
        fixture.hostRoom.holdSnapshots = true
        assertTrue(actions.trySubmit(PendingAction.PLAY_CARDS, play, true))
        assertFalse(actions.trySubmit(PendingAction.PLAY_CARDS, play, true))
        runCurrent()
        val outcome = assertIs<PeerCommandProgress.Resolved>(peer.commandProgress.value)
        assertEquals(CommandStatus.Applied, outcome.outcome.status)
        assertEquals(1L, fixture.session.currentState().public.acceptedPlaySequence)
        assertEquals(initial, peer.controller.privateStateFor(testAliceId).value.state)
        assertEquals(PendingAction.PLAY_CARDS, actions.pendingAction.value)
        peer.acknowledgeCommandOutcome(outcome.outcome.commandId)
        runCurrent()
        assertEquals(PendingAction.PLAY_CARDS, actions.pendingAction.value)
        assertFalse(actions.trySubmit(PendingAction.PLAY_CARDS, play, true))
        fixture.hostRoom.releaseSnapshots()
        runCurrent()
        assertEquals(PeerCommandProgress.Idle, peer.commandProgress.value)
        assertNull(actions.pendingAction.value)
        assertEquals(4, peer.controller.privateStateFor(testAliceId).value.state.privatePerPlayer.getValue(testAliceId).hand.size)
        assertEquals(1, fixture.peerRooms.getValue(testAliceId).sent.filterIsInstance<PeerMessage.ClientCommand>().size)
        fixture.close()
    }

    @Test
    fun rejected_peer_action_preserves_private_hand_and_allows_a_fresh_corrected_action() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val peer = fixture.peers.getValue(testAliceId)
        val actions = LastLightActionSubmission(backgroundScope, peer.controller::submit, peer.commandProgress)
        val before = peer.controller.privateStateFor(testAliceId).value
        val stolenCard = fixture.session.currentState().privatePerPlayer.getValue(testHostId).hand.first().id
        assertTrue(actions.trySubmit(PendingAction.PLAY_CARDS, LastLightAction.PlayCards(testAliceId, listOf(stolenCard)), true))
        runCurrent()
        val rejected = assertIs<PeerCommandProgress.Resolved>(peer.commandProgress.value)
        assertEquals(CommandStatus.InvalidAction, rejected.outcome.status)
        assertEquals(before, peer.controller.privateStateFor(testAliceId).value)
        peer.acknowledgeCommandOutcome(rejected.outcome.commandId)
        runCurrent()
        assertNull(actions.pendingAction.value)
        val own = before.state.privatePerPlayer.getValue(testAliceId).hand.first().id
        assertTrue(actions.trySubmit(PendingAction.PLAY_CARDS, LastLightAction.PlayCards(testAliceId, listOf(own)), true))
        runCurrent()
        val accepted = assertIs<PeerCommandProgress.Resolved>(peer.commandProgress.value)
        assertEquals(CommandStatus.Applied, accepted.outcome.status)
        assertEquals(4, peer.controller.privateStateFor(testAliceId).value.state.privatePerPlayer.getValue(testAliceId).hand.size)
        peer.acknowledgeCommandOutcome(accepted.outcome.commandId)
        runCurrent()
        assertNull(actions.pendingAction.value)
        fixture.close()
    }

    @Test
    fun ambiguous_transport_failure_recovers_the_receipt_without_replaying_the_play() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val peer = fixture.peers.getValue(testAliceId)
        val room = fixture.peerRooms.getValue(testAliceId)
        val actions = LastLightActionSubmission(backgroundScope, peer.controller::submit, peer.commandProgress)
        val card = fixture.session.currentState().privatePerPlayer.getValue(testAliceId).hand.first().id
        room.failNextCommandAfterDelivery = true
        assertTrue(actions.trySubmit(PendingAction.PLAY_CARDS, LastLightAction.PlayCards(testAliceId, listOf(card)), true))
        runCurrent()
        fixture.bus.emitHostRestored()
        runCurrent()
        val resolved = assertIs<PeerCommandProgress.Resolved>(peer.commandProgress.value)
        assertEquals(CommandStatus.Applied, resolved.outcome.status)
        assertEquals(1L, fixture.session.currentState().public.acceptedPlaySequence)
        assertEquals(1, room.sent.filterIsInstance<PeerMessage.ClientCommand>().size)
        peer.acknowledgeCommandOutcome(resolved.outcome.commandId)
        runCurrent()
        assertNull(actions.pendingAction.value)
        fixture.close()
    }
}
