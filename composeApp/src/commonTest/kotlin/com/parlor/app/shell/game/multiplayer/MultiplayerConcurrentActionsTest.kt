package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.PeerMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerConcurrentActionsTest {
    @Test
    fun same_revision_physical_winks_are_serialized_without_losing_or_duplicating_attempts() = runTest {
        val fixture = MultiplayerGameFixture(this, GhamzaGameShellBinding(GhamzaDefinition()),
            gamePlayers(5), caseId = GhamzaSettings(attempts = 3).caseId)
        try {
            fixture.attachPeers()
            fixture.players.forEach { fixture.perform(GhamzaAction.Ready(it.id, 1), it.id) }
            val before = fixture.session.currentState()
            val guests = fixture.peerRooms.keys.filter { it != before.hostOnly.winker }.take(2)
            val a = fixture.command(GhamzaAction.Winked(guests[0], 1, 1), guests[0])
            val b = fixture.command(GhamzaAction.Winked(guests[1], 1, 1), guests[1])
            assertEquals(a.expectedRevision, b.expectedRevision)
            assertEquals(CommandStatus.Applied, fixture.send(guests[0], a).status)
            assertEquals(CommandStatus.StaleRevision, fixture.send(guests[1], b).status)
            assertEquals(1, fixture.session.currentState().public.reports.values.sum())
            val submitted = fixture.peerRooms.getValue(guests[1]).sent.filterIsInstance<PeerMessage.ClientCommand>().size
            runCurrent()
            assertEquals(submitted, fixture.peerRooms.getValue(guests[1]).sent.filterIsInstance<PeerMessage.ClientCommand>().size)
            assertEquals(CommandStatus.Applied, fixture.send(guests[0], a).status)
            assertEquals(1, fixture.session.currentState().public.reports.values.sum())
            fixture.requestSnapshot(guests[1])
            val explicitRetry = fixture.command(GhamzaAction.Winked(guests[1], 1, 1), guests[1])
            assertEquals(CommandStatus.Applied, fixture.send(guests[1], explicitRetry).status)
            assertEquals(2, fixture.session.currentState().public.reports.values.sum())
            assertEquals(listOf(1, 2), fixture.session.currentState().public.recentReports.map { it.number })
            fixture.assertSynchronized()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun simultaneous_private_votes_require_deliberate_retry_and_do_not_publish_partial_totals() = runTest {
        val fixture = MultiplayerGameFixture(this, WordImpostorGameShellBinding(WordImpostorDefinition()), gamePlayers(5))
        try {
            fixture.attachPeers()
            fixture.players.forEach { fixture.perform(WordImpostorAction.Ready(it.id, 1), it.id) }
            fixture.session.currentState().public.interactions.forEachIndexed { index, pair ->
                fixture.perform(WordImpostorAction.Answered(pair.asker, 1, index), pair.asker)
            }
            fixture.perform(WordImpostorAction.OpenVoting(1))
            val peers = fixture.peerRooms.keys.take(2)
            val a = fixture.command(WordImpostorAction.Vote(peers[0], 1, gameHostId), peers[0])
            val b = fixture.command(WordImpostorAction.Vote(peers[1], 1, gameHostId), peers[1])
            assertEquals(CommandStatus.Applied, fixture.send(peers[0], a).status)
            assertEquals(CommandStatus.StaleRevision, fixture.send(peers[1], b).status)
            assertEquals(setOf(peers[0]), fixture.session.currentState().public.voted)
            assertNull(fixture.session.currentState().public.voteCounts)
            fixture.requestSnapshot(peers[1])
            assertEquals(CommandStatus.Applied, fixture.send(peers[1],
                fixture.command(WordImpostorAction.Vote(peers[1], 1, gameHostId), peers[1])).status)
            assertEquals(peers.toSet(), fixture.session.currentState().public.voted)
            assertNull(fixture.session.currentState().public.voteCounts)
            fixture.peers.forEach { (id, peer) ->
                val own = peer.controller.privateStateFor(id).value.state.privatePerPlayer.getValue(id)
                assertEquals(gameHostId.takeIf { id in peers }, own.vote)
            }
            fixture.assertSynchronized()
        } finally {
            fixture.close()
        }
    }
}
