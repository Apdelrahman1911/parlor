package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.core.result.Result
import com.parlor.engine.session.SubmitError
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LastLightLifecycleTest {
    @Test
    fun real_start_requires_all_ready_before_publishing_and_installs_only_after_commit() = runTest {
        val fixture = LastLightBridgeFixture(this, requireStartHandshake = true)
        val readyGate = CompletableDeferred<Unit>()
        fixture.peerRooms.getValue(testBobId).readyGate = readyGate
        val attaching = async { fixture.attachPeers(handshake = true) }
        runCurrent()
        assertFalse(attaching.isCompleted)
        assertTrue(fixture.hostRoom.sent.none { it.second is HostMessage.PlayerSnapshot })
        assertTrue(fixture.hostRoom.sent.none { it.second is HostMessage.SessionStartCommitted })
        readyGate.complete(Unit)
        attaching.await()
        runCurrent()
        val sent = fixture.hostRoom.sent.map { it.second }
        val offers = sent.filterIsInstance<HostMessage.SessionStarting>()
        assertEquals(1, offers.map { it.startId }.distinct().size)
        offers.forEach {
            assertEquals(LastLightIds.GameId, it.header.gameId)
            assertEquals(1, it.header.gameVersion)
            assertEquals(4, it.header.protocol.major)
            assertEquals(2, it.header.protocol.minor)
            assertEquals(LastLightIds.StandardModeId.raw, it.modeId)
            assertEquals(LastLightIds.CaseId.raw, it.caseId)
        }
        assertTrue(sent.any { it is HostMessage.SessionStartCommitted })
        assertTrue(sent.any { it is HostMessage.PlayerSnapshot })
        fixture.peers.values.forEach { assertTrue(it.hasAuthoritativeSnapshot.value) }
        val offer = offers.first()
        val room = fixture.peerRooms.getValue(testAliceId)
        assertFalse(acceptsLastLightStart(offer.copy(modeId = "other-mode"), room))
        assertFalse(acceptsLastLightStart(offer.copy(players = offer.players.dropLast(1)), fixture.peerRooms.getValue(testBobId)))
        fixture.close()
    }

    @Test
    fun empty_hand_claimant_remains_required_and_expiry_ends_without_removing_hands() = runTest {
        val players = lastLightTestPlayers.take(2)
        val seed = seedWithState(players) { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, players = players, seed = seed)
        fixture.attachPeers()
        fixture.playCurrent(3)
        fixture.playCurrent(1)
        fixture.playCurrent(2)
        val pendingClaim = fixture.session.currentState()
        assertTrue(pendingClaim.privatePerPlayer.getValue(testAliceId).hand.isEmpty())
        assertTrue(pendingClaim.public.forcedChallenge)
        assertEquals(testAliceId.raw, pendingClaim.public.latestClaim?.playerId)
        fixture.bus.emitPeerLeft(testAliceId, "Alice")
        runCurrent()
        assertEquals(setOf(testAliceId), fixture.session.currentState().public.disconnectedPlayers)
        assertIs<Result.Failure<SubmitError>>(fixture.host.submitHostAction(LastLightAction.Challenge(testHostId)))
        advanceTimeBy(100)
        fixture.bus.emitPeerLeft(testAliceId, "Alice")
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        val ended = fixture.session.currentState()
        assertEquals(GamePhase.FINISHED, ended.phase)
        assertTrue(ended.public.endedEarly)
        assertNull(ended.public.winnerId)
        assertEquals(pendingClaim.privatePerPlayer, ended.privatePerPlayer)
        assertTrue(fixture.hostRoom.retired.isEmpty())
        assertEquals(SessionEndReason.Cancelled, fixture.host.terminalReason.value)
        assertEquals(SessionEndReason.Cancelled, fixture.peers.getValue(testAliceId).terminalReason.value)
        fixture.close()
    }

    @Test
    fun required_seat_rejoins_only_after_replaying_the_stable_start_barrier() = runTest {
        val fixture = LastLightBridgeFixture(this, requireStartHandshake = true)
        fixture.attachPeers(handshake = true)
        val original = fixture.session.currentState()
        val startId = fixture.starts.getValue(testAliceId).offer.startId
        fixture.bus.emitPeerLeft(testAliceId, "Alice")
        runCurrent()
        assertEquals(setOf(testAliceId), fixture.session.currentState().public.disconnectedPlayers)
        fixture.bus.emitPeerReconnected(testAliceId, "Alice")
        runCurrent()
        assertTrue(fixture.session.currentState().public.disconnectedPlayers.isEmpty())
        assertEquals(original.privatePerPlayer, fixture.session.currentState().privatePerPlayer)
        val replayed = fixture.hostRoom.sent.filter { it.first == SendTarget.Direct(testAliceId) }
            .mapNotNull { it.second as? HostMessage.SessionStarting }
        assertTrue(replayed.size >= 2)
        assertEquals(setOf(startId), replayed.map { it.startId }.toSet())
        advanceTimeBy(201)
        runCurrent()
        assertNull(fixture.host.terminalReason.value)
        fixture.close()
    }

    @Test
    fun failed_rejoin_cannot_extend_grace_or_be_revived_by_late_ready_ack_frames() = runTest {
        val fixture = LastLightBridgeFixture(this, requireStartHandshake = true)
        fixture.attachPeers(handshake = true)
        val startId = fixture.starts.getValue(testAliceId).offer.startId
        fixture.hostRoom.dropStartOffers = true
        fixture.bus.emitPeerLeft(testAliceId, "Alice")
        fixture.bus.emitPeerReconnected(testAliceId, "Alice")
        runCurrent()
        advanceTimeBy(201)
        runCurrent()
        val ended = fixture.session.currentState()
        assertEquals(GamePhase.FINISHED, ended.phase)
        assertEquals(SessionEndReason.Cancelled, fixture.host.terminalReason.value)
        fixture.peerRooms.getValue(testAliceId).sendToHost(PeerMessage.SessionStartReady(fixture.header("late-ready"), testAliceId, startId))
        fixture.peerRooms.getValue(testAliceId).sendToHost(PeerMessage.SessionStartCommitAck(fixture.header("late-ack"), testAliceId, startId))
        runCurrent()
        assertEquals(ended, fixture.session.currentState())
        assertTrue(fixture.hostRoom.retired.isEmpty())
        fixture.close()
    }

    @Test
    fun recreated_peer_resumes_next_sequence_without_sacrificing_its_first_action() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed, requireStartHandshake = true)
        fixture.attachPeers(handshake = true)
        fixture.playCurrent()
        val oldPeer = fixture.peers.getValue(testAliceId)
        oldPeer.close()
        fixture.bus.emitPeerLeft(testAliceId, "Alice")
        runCurrent()
        val replacement = fixture.createPeer(testAliceId)
        fixture.peers[testAliceId] = replacement
        fixture.bus.emitPeerReconnected(testAliceId, "Alice")
        runCurrent()
        assertTrue(replacement.hasAuthoritativeSnapshot.value)
        assertTrue(fixture.session.currentState().public.disconnectedPlayers.isEmpty())
        fixture.playCurrent()
        fixture.playCurrent()
        assertEquals(testAliceId.raw, fixture.session.currentState().public.turnPlayerId)
        val expected = fixture.latestSnapshot(testAliceId).nextExpectedClientSequence
        fixture.playCurrent()
        val firstAfterResume = fixture.peerRooms.getValue(testAliceId).sent.filterIsInstance<PeerMessage.ClientCommand>().last()
        assertEquals(expected, firstAfterResume.clientSequence)
        assertTrue(expected > 1L)
        assertEquals(3, fixture.session.currentState().privatePerPlayer.getValue(testAliceId).hand.size)
        fixture.close()
    }

    @Test
    fun eliminated_seat_disconnect_does_not_pause_surviving_players() = runTest {
        val seed = seedWithState { state ->
            state.public.turnPlayerId == testAliceId.raw && state.hostOnly.burnoutSteps[testAliceId] == 1 &&
                state.privatePerPlayer.getValue(testAliceId).hand.any { it.rank != state.public.tableRank && it.rank != CardRank.WILD }
        }
        val fixture = LastLightBridgeFixture(this, seed = seed, requireStartHandshake = true)
        fixture.attachPeers(handshake = true)
        val before = fixture.session.currentState()
        val bluff = before.privatePerPlayer.getValue(testAliceId).hand.first { it.rank != before.public.tableRank && it.rank != CardRank.WILD }
        fixture.perform(LastLightAction.PlayCards(testAliceId, listOf(bluff.id)))
        fixture.challengeCurrent()
        val eliminated = fixture.session.currentState()
        assertTrue(eliminated.public.roster.first { it.id == testAliceId.raw }.eliminated)
        fixture.bus.emitPeerLeft(testAliceId, "Alice")
        runCurrent()
        advanceTimeBy(201)
        runCurrent()
        assertEquals(eliminated, fixture.session.currentState())
        assertNull(fixture.host.terminalReason.value)
        fixture.perform(LastLightAction.NextRound)
        assertEquals(GamePhase.PLAYING, fixture.session.currentState().phase)
        fixture.bus.emitPeerReconnected(testAliceId, "Alice")
        runCurrent()
        assertTrue(fixture.peers.getValue(testAliceId).controller.privateStateFor(testAliceId).value.state.privatePerPlayer.getValue(testAliceId).hand.isEmpty())
        fixture.close()
    }

    @Test
    fun terminal_delivery_is_awaited_and_blocks_all_later_commands() = runTest {
        val fixture = LastLightBridgeFixture(this)
        fixture.attachPeers()
        val gate = CompletableDeferred<Unit>()
        fixture.hostRoom.terminalGate = gate
        val terminating = async { fixture.host.terminate(SessionEndReason.Completed) }
        runCurrent()
        assertFalse(terminating.isCompleted)
        gate.complete(Unit)
        terminating.await()
        runCurrent()
        fixture.peers.values.forEach { assertEquals(SessionEndReason.Completed, it.terminalReason.value) }
        val before = fixture.session.currentState()
        assertEquals(Result.Failure(SubmitError.SessionClosed), fixture.host.submitHostAction(LastLightAction.NextRound))
        assertEquals(
            Result.Failure(SubmitError.SessionClosed),
            fixture.peers.getValue(testAliceId).controller.submit(LastLightAction.Challenge(testAliceId)),
        )
        val command = fixture.command(LastLightAction.Challenge(testAliceId), testAliceId)
        val messagesAfterEnd = fixture.hostRoom.sent.size
        fixture.peerRooms.getValue(testAliceId).sendToHost(command)
        runCurrent()
        // The terminal coordinator seals its outbound mailbox; stale frames
        // receive no new acknowledgement or snapshot after SessionEnded.
        assertEquals(messagesAfterEnd, fixture.hostRoom.sent.size)
        assertEquals(before, fixture.session.currentState())
        fixture.close()
    }

    @Test
    fun topology_reconciliation_sees_a_survivor_already_offline_when_runtime_is_built() = runTest {
        val fixture = LastLightBridgeFixture(this, reconcileMembers = true, initialOffline = setOf(testAliceId))
        fixture.attachPeers()
        assertEquals(setOf(testAliceId), fixture.session.currentState().public.disconnectedPlayers)
        fixture.hostRoom.memberState.value = fixture.hostRoom.memberState.value.map { it.copy(connected = true) }
        runCurrent()
        assertTrue(fixture.session.currentState().public.disconnectedPlayers.isEmpty())
        fixture.close()
    }

    @Test
    fun brief_peer_loss_retains_a_privacy_epoch_after_connection_is_already_restored() = runTest {
        val fixture = LastLightBridgeFixture(this)
        fixture.attachPeers()
        val peer = fixture.peers.getValue(testAliceId)
        val before = peer.controller.privateStateFor(testAliceId).value
        val epoch = peer.recoveryEpoch.value
        fixture.bus.emitHostLost()
        fixture.bus.emitHostRestored()
        runCurrent()
        assertFalse(peer.connectionState.value.hostLost)
        assertTrue(peer.recoveryEpoch.value > epoch)
        assertEquals(before, peer.controller.privateStateFor(testAliceId).value)
        fixture.close()
    }
}
