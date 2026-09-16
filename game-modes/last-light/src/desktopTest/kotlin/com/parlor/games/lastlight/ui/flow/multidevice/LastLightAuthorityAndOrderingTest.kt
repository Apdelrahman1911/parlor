package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.core.result.Result
import com.parlor.engine.session.SubmitError
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.room.RoomLifecycleState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LastLightAuthorityAndOrderingTest {
    @Test
    fun authenticated_seat_rejects_impersonation_host_controls_and_lifecycle_payloads() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val before = fixture.session.currentState()
        val forged = LastLightAction.PlayCards(testBobId, before.privatePerPlayer.getValue(testBobId).hand.take(1).map { it.id })
        val command = fixture.command(forged, testAliceId).copy(actor = testBobId)
        assertEquals(CommandStatus.Unauthorized, fixture.send(testAliceId, command).status)
        assertEquals(before, fixture.session.currentState())

        val forbidden = listOf(
            LastLightAction.NextRound,
            LastLightAction.EndGame,
            LastLightAction.MarkPlayerDisconnected(testHostId),
            LastLightAction.MarkPlayerReconnected(testAliceId),
            LastLightAction.ContinueWithoutPlayer(testHostId),
        )
        forbidden.forEach { action ->
            fixture.requestSnapshot(testAliceId)
            assertEquals(CommandStatus.Unauthorized, fixture.send(testAliceId, fixture.command(action, testAliceId)).status)
            assertEquals(before, fixture.session.currentState())
        }
        assertIs<Result.Failure<SubmitError>>(fixture.host.submitHostAction(forged))
        assertIs<Result.Failure<SubmitError>>(fixture.host.submitHostAction(LastLightAction.MarkPlayerDisconnected(testAliceId)))
        assertEquals(before, fixture.session.currentState())

        fixture.requestSnapshot(testAliceId)
        // Adversarial raw frames intentionally bypass the local peer ledger.
        // The next honest wire command uses the host's advertised sequence.
        val honest = LastLightAction.PlayCards(testAliceId, before.privatePerPlayer.getValue(testAliceId).hand.take(1).map { it.id })
        assertEquals(CommandStatus.Applied, fixture.send(testAliceId, fixture.command(honest, testAliceId)).status)
        assertEquals(1L, fixture.session.currentState().public.acceptedPlaySequence)
        assertEquals(4, fixture.session.currentState().privatePerPlayer.getValue(testAliceId).hand.size)
        fixture.close()
    }

    @Test
    fun duplicate_and_replayed_play_and_challenge_have_one_canonical_effect() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val card = fixture.session.currentState().privatePerPlayer.getValue(testAliceId).hand.first()
        val play = fixture.command(LastLightAction.PlayCards(testAliceId, listOf(card.id)), testAliceId)
        val accepted = fixture.send(testAliceId, play)
        assertEquals(CommandStatus.Applied, accepted.status)
        val afterPlay = fixture.session.currentState()
        assertEquals(CommandStatus.Applied, fixture.send(testAliceId, play).status)
        assertEquals(afterPlay, fixture.session.currentState())

        val replayHeader = fixture.header("play-replay")
        val replay = play.copy(header = replayHeader, commandId = replayHeader.messageId)
        assertEquals(CommandStatus.Duplicate, fixture.send(testAliceId, replay).status)
        assertEquals(afterPlay, fixture.session.currentState())
        assertEquals(accepted.authoritativeRevision, fixture.latestSnapshot(testAliceId).revision)

        fixture.challengeCurrent()
        val challenge = fixture.peerRooms.getValue(testBobId).sent.filterIsInstance<PeerMessage.ClientCommand>().last()
        val afterChallenge = fixture.session.currentState()
        assertEquals(1L, afterChallenge.public.outcomeSequence)
        assertEquals(CommandStatus.Applied, fixture.send(testBobId, challenge).status)
        val challengeReplayHeader = fixture.header("challenge-replay")
        assertEquals(
            CommandStatus.Duplicate,
            fixture.send(testBobId, challenge.copy(header = challengeReplayHeader, commandId = challengeReplayHeader.messageId)).status,
        )
        assertEquals(afterChallenge, fixture.session.currentState())
        fixture.close()
    }

    @Test
    fun reordered_and_stale_commands_resynchronize_without_replaying_the_action() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val initial = fixture.session.currentState()
        val cards = initial.privatePerPlayer.getValue(testAliceId).hand
        val outOfOrder = fixture.command(LastLightAction.PlayCards(testAliceId, listOf(cards[1].id)), testAliceId, sequence = 2)
        val gap = fixture.send(testAliceId, outOfOrder)
        assertEquals(CommandStatus.SequenceGap, gap.status)
        assertEquals(1L, gap.nextExpectedClientSequence)
        assertEquals(initial, fixture.session.currentState())

        val first = fixture.command(LastLightAction.PlayCards(testAliceId, listOf(cards[0].id)), testAliceId, sequence = 1)
        assertEquals(CommandStatus.Applied, fixture.send(testAliceId, first).status)
        val afterFirst = fixture.session.currentState()
        val delayed = fixture.send(testAliceId, outOfOrder)
        assertEquals(CommandStatus.StaleRevision, delayed.status)
        assertEquals(3L, delayed.nextExpectedClientSequence)
        assertEquals(afterFirst, fixture.session.currentState())
        assertTrue(afterFirst.privatePerPlayer.getValue(testAliceId).hand.any { it.id == cards[1].id })
        assertEquals(CommandStatus.StaleRevision, fixture.send(testAliceId, outOfOrder).status)
        assertEquals(afterFirst, fixture.session.currentState())
        assertEquals(afterFirst.public, fixture.peers.getValue(testAliceId).controller.publicState.value.state.public)
        fixture.close()
    }

    @Test
    fun old_session_and_non_exact_protocol_commands_cannot_enter_a_new_match() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val old = LastLightBridgeFixture(this, seed = seed)
        old.attachPeers()
        val action = LastLightAction.PlayCards(testAliceId, old.session.currentState().privatePerPlayer.getValue(testAliceId).hand.take(1).map { it.id })
        val oldCommand = old.command(action, testAliceId)
        old.close()

        val fresh = LastLightBridgeFixture(this, seed = seed)
        fresh.attachPeers()
        val before = fresh.session.currentState()
        assertNotEquals(old.host.protocol.sessionId, fresh.host.protocol.sessionId)
        assertEquals(CommandStatus.IncompatibleVersion, fresh.send(testAliceId, oldCommand).status)
        listOf(ProtocolVersion(4, 1), ProtocolVersion(4, 3)).forEach { version ->
            val command = fresh.command(action, testAliceId)
            assertEquals(
                CommandStatus.IncompatibleVersion,
                fresh.send(testAliceId, command.copy(header = command.header.copy(protocol = version))).status,
            )
        }
        assertEquals(before, fresh.session.currentState())
        fresh.close()
    }

    @Test
    fun eliminated_host_keeps_round_authority_but_cannot_play_a_normal_turn() = runTest {
        val seed = seedWithState { state ->
            state.public.turnPlayerId == testHostId.raw && state.hostOnly.burnoutSteps[testHostId] == 1 &&
                state.privatePerPlayer.getValue(testHostId).hand.any { it.rank != state.public.tableRank && it.rank != CardRank.WILD }
        }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val state = fixture.session.currentState()
        val bluff = state.privatePerPlayer.getValue(testHostId).hand.first { it.rank != state.public.tableRank && it.rank != CardRank.WILD }
        fixture.perform(LastLightAction.PlayCards(testHostId, listOf(bluff.id)))
        fixture.challengeCurrent()
        assertEquals(GamePhase.ROUND_ENDED, fixture.session.currentState().phase)
        assertTrue(fixture.session.currentState().public.roster.first { it.id == testHostId.raw }.eliminated)

        fixture.requestSnapshot(testAliceId)
        assertEquals(CommandStatus.Unauthorized, fixture.send(testAliceId, fixture.command(LastLightAction.NextRound, testAliceId)).status)
        fixture.perform(LastLightAction.NextRound)
        assertEquals(GamePhase.PLAYING, fixture.session.currentState().phase)
        assertEquals(2, fixture.session.currentState().public.roundNumber)
        assertTrue(fixture.session.currentState().privatePerPlayer.getValue(testHostId).hand.isEmpty())
        val before = fixture.session.currentState()
        assertIs<Result.Failure<SubmitError>>(fixture.host.submitHostAction(LastLightAction.Challenge(testHostId)))
        assertEquals(before, fixture.session.currentState())
        fixture.close()
    }

    @Test
    fun room_suspension_blocks_both_authorities_without_state_changes() = runTest {
        val fixture = LastLightBridgeFixture(this)
        fixture.attachPeers()
        val before = fixture.session.currentState()
        fixture.hostRoom.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)
        assertEquals(Result.Failure(SubmitError.SessionSuspended), fixture.host.submitHostAction(LastLightAction.NextRound))
        val action = LastLightAction.Challenge(testAliceId)
        assertEquals(CommandStatus.SessionSuspended, fixture.send(testAliceId, fixture.command(action, testAliceId)).status)
        assertEquals(before, fixture.session.currentState())
        assertFalse(fixture.session.currentState().public.endedEarly)
        fixture.close()
    }
}
