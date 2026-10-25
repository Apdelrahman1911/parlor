package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.PeerMessage
import com.parlor.session.multidevice.PeerCommandProgress
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameActionSubmissionTest {
    @Test
    fun one_hundred_thousand_rapid_clicks_reserve_only_one_command_until_receipt_and_snapshot_are_installed() = runTest {
        val fixture = MultiplayerGameFixture(this, GhamzaGameShellBinding(GhamzaDefinition()))
        try {
            fixture.attachPeers()
            val id = fixture.players[1].id
            val peer = fixture.peers.getValue(id)
            val actions = GameActionSubmission(backgroundScope, peer.controller::submit, peer.commandProgress)
            val before = peer.controller.privateStateFor(id).value
            val action = GhamzaAction.Ready(id, before.state.public.token)
            fixture.hostRoom.holdSnapshots = true
            assertTrue(actions.trySubmit(action, true))
            repeat(100_000) { assertFalse(actions.trySubmit(action, true)) }
            runCurrent()
            val resolved = assertIs<PeerCommandProgress.Resolved>(peer.commandProgress.value)
            assertEquals(CommandStatus.Applied, resolved.outcome.status)
            assertEquals(setOf(id), fixture.session.currentState().public.ready)
            assertEquals(before, peer.controller.privateStateFor(id).value)
            assertTrue(actions.pending.value)
            peer.acknowledgeCommandOutcome(resolved.outcome.commandId)
            runCurrent()
            assertTrue(actions.pending.value)
            fixture.hostRoom.releaseSnapshots()
            runCurrent()
            assertFalse(actions.pending.value)
            assertEquals(1, fixture.peerRooms.getValue(id).sent.filterIsInstance<PeerMessage.ClientCommand>().size)
            fixture.assertSynchronized()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun ambiguous_transport_failure_recovers_the_receipt_without_automatically_replaying_the_action() = runTest {
        val fixture = MultiplayerGameFixture(this, GhamzaGameShellBinding(GhamzaDefinition()))
        try {
            fixture.attachPeers()
            val id = fixture.players[1].id
            val peer = fixture.peers.getValue(id)
            val room = fixture.peerRooms.getValue(id)
            val actions = GameActionSubmission(backgroundScope, peer.controller::submit, peer.commandProgress)
            room.failNextCommandAfterDelivery = true
            assertTrue(actions.trySubmit(GhamzaAction.Ready(id, fixture.session.currentState().public.token), true))
            runCurrent()
            fixture.bus.emitHostRestored()
            runCurrent()
            val resolved = assertIs<PeerCommandProgress.Resolved>(peer.commandProgress.value)
            assertEquals(CommandStatus.Applied, resolved.outcome.status)
            assertEquals(setOf(id), fixture.session.currentState().public.ready)
            assertEquals(1, room.sent.filterIsInstance<PeerMessage.ClientCommand>().size)
            peer.acknowledgeCommandOutcome(resolved.outcome.commandId)
            runCurrent()
            assertFalse(actions.pending.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun connected_peer_cancels_queued_action_if_another_seat_recovers_before_the_ui_dispatcher_observes_it() =
        verifyQueuedActionRecovery(false)

    @Test
    fun skipped_paused_snapshot_still_invalidates_a_queued_action_after_recovery() = verifyQueuedActionRecovery(true)

    private fun verifyQueuedActionRecovery(skipPause: Boolean) = runTest {
        val fixture = MultiplayerGameFixture(this, GhamzaGameShellBinding(GhamzaDefinition()))
        val scheduler = TestCoroutineScheduler()
        val uiJob = SupervisorJob()
        val uiScope = CoroutineScope(uiJob + StandardTestDispatcher(scheduler))
        val gate = CompletableDeferred<Unit>()
        try {
            fixture.attachPeers()
            val alice = fixture.players[1].id
            val bob = fixture.players[2].id
            val peer = fixture.peers.getValue(alice)
            val epoch = peer.recoveryEpoch.value
            val before = peer.controller.privateStateFor(alice).value
            val actions = GameActionSubmission(uiScope, peer.controller::submit, peer.commandProgress)
            assertTrue(actions.trySubmit(GhamzaAction.Ready(alice, before.state.public.token), true) {
                peer.recoveryEpoch.value == epoch
            })
            if (skipPause) {
                fixture.hostRoom.snapshotSendGate = alice to gate
                fixture.requestSnapshot(alice)
            }
            fixture.bus.emitPeerLeft(bob, "Bob")
            runCurrent()
            fixture.bus.emitPeerReconnected(bob, "Bob")
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertTrue(peer.recoveryEpoch.value > epoch)
            assertEquals(before, peer.controller.privateStateFor(alice).value)
            scheduler.runCurrent()
            runCurrent()
            scheduler.runCurrent()
            assertFalse(actions.pending.value)
            assertTrue(fixture.session.currentState().public.ready.isEmpty())
            assertTrue(fixture.peerRooms.getValue(alice).sent.none { it is PeerMessage.ClientCommand })
        } finally {
            gate.complete(Unit)
            uiJob.cancel()
            scheduler.runCurrent()
            fixture.close()
        }
    }
}
