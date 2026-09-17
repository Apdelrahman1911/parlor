package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.GameState
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
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
class MultiplayerRecoveryTest {
    @Test
    fun dominoes_preserves_hands_and_board_through_secure_start_rejoin_recreation_and_terminal_expiry() = runTest {
        verifyRecovery(DominoGameShellBinding(DominoDefinition()))
    }

    @Test
    fun ghamza_preserves_private_roles_and_attempts_through_secure_start_rejoin_recreation_and_terminal_expiry() = runTest {
        verifyRecovery(GhamzaGameShellBinding(GhamzaDefinition()))
    }

    @Test
    fun word_impostor_preserves_word_team_and_phase_through_secure_start_rejoin_recreation_and_terminal_expiry() = runTest {
        verifyRecovery(WordImpostorGameShellBinding(WordImpostorDefinition()))
    }

    private suspend fun <S : GameState, A : GameAction, E : GameEvent> TestScope.verifyRecovery(
        spec: MultiplayerGameSpec<S, A, E>,
    ) {
        val fixture = MultiplayerGameFixture(this, spec)
        val alice = fixture.players[1].id
        val bob = fixture.players[2].id
        val gate = CompletableDeferred<Unit>()
        try {
            fixture.peerRooms.getValue(bob).readyGate = gate
            val attaching = async { fixture.attachPeers() }
            runCurrent()
            assertFalse(attaching.isCompleted)
            assertTrue(fixture.hostRoom.sent.none { it.second is HostMessage.PlayerSnapshot })
            assertTrue(fixture.hostRoom.sent.none { it.second is HostMessage.SessionStartCommitted })
            gate.complete(Unit)
            attaching.await()
            val start = fixture.starts.getValue(alice).offer
            val room = fixture.peerRooms.getValue(alice)
            assertTrue(spec.acceptsStart(start, room))
            assertFalse(spec.acceptsStart(start.copy(modeId = "wrong-mode"), room))
            assertFalse(spec.acceptsStart(start.copy(caseId = "corrupt-settings"), room))
            assertFalse(spec.acceptsStart(start.copy(players = start.players.filter { it.id != alice }), room))
            val before = spec.definition.projectionPolicy().toPlayer(fixture.session.currentState(), alice)
            fixture.bus.emitPeerLeft(alice, "Alice")
            runCurrent()
            assertEquals(setOf(alice), spec.disconnected(fixture.session.currentState()))
            fixture.bus.emitPeerReconnected(alice, "Alice")
            runCurrent()
            assertTrue(spec.disconnected(fixture.session.currentState()).isEmpty())
            assertEquals(before, fixture.peers.getValue(alice).controller.privateStateFor(alice).value)
            val replayed = fixture.hostRoom.sent.filter { it.first == SendTarget.Direct(alice) }
                .mapNotNull { it.second as? HostMessage.SessionStarting }
            assertTrue(replayed.size >= 2)
            assertEquals(setOf(start.startId), replayed.map { it.startId }.toSet())
            advanceTimeBy(201)
            runCurrent()
            assertNull(fixture.host.terminalReason.value)

            fixture.peers.getValue(alice).close()
            fixture.hostRoom.holdSnapshots = true
            fixture.bus.emitPeerLeft(alice, "Alice")
            runCurrent()
            val replacement = fixture.createPeer(alice)
            fixture.peers[alice] = replacement
            // The canonical argument to the peer constructor is stripped before a snapshot arrives.
            assertEquals(spec.definition.projectionPolicy().toPublic(fixture.session.currentState()).state,
                replacement.controller.privateStateFor(alice).value.state)
            assertFalse(replacement.hasAuthoritativeSnapshot.value)
            fixture.bus.emitPeerReconnected(alice, "Alice")
            runCurrent()
            fixture.hostRoom.releaseSnapshots()
            runCurrent()
            fixture.assertSynchronized()
            assertEquals(before, replacement.controller.privateStateFor(alice).value)
            val epoch = replacement.recoveryEpoch.value
            fixture.bus.emitHostLost()
            fixture.bus.emitHostRestored()
            runCurrent()
            assertFalse(replacement.connectionState.value.hostLost)
            assertTrue(replacement.recoveryEpoch.value > epoch)

            // Duplicate loss and failed start replays cannot extend the original deadline.
            fixture.hostRoom.dropStartOffers = true
            fixture.bus.emitPeerLeft(alice, "Alice")
            runCurrent()
            advanceTimeBy(100)
            fixture.bus.emitPeerLeft(alice, "Alice")
            fixture.bus.emitPeerReconnected(alice, "Alice")
            runCurrent()
            advanceTimeBy(101)
            runCurrent()
            assertTrue(spec.isAborted(fixture.session.currentState()))
            assertEquals(SessionEndReason.Cancelled, fixture.host.terminalReason.value)
            fixture.peers.values.forEach { assertEquals(SessionEndReason.Cancelled, it.terminalReason.value) }
            val ended = fixture.session.currentState()
            fixture.peerRooms.getValue(alice).sendToHost(PeerMessage.SessionStartReady(fixture.header("late"), alice, start.startId))
            runCurrent()
            assertEquals(ended, fixture.session.currentState())
            assertTrue(fixture.hostRoom.retired.isEmpty()) // No host migration or converting the remaining seats to another game.
        } finally {
            gate.complete(Unit)
            fixture.close()
        }

        val leaving = MultiplayerGameFixture(this, spec)
        try {
            leaving.attachPeers()
            val before = leaving.session.currentState()
            leaving.host.terminate(SessionEndReason.HostLeft)
            runCurrent()
            leaving.peers.values.forEach {
                assertEquals(SessionEndReason.HostLeft, it.terminalReason.value)
                assertEquals(Result.Failure(SubmitError.SessionClosed), it.controller.submit(spec.abortAction))
            }
            assertIs<Result.Failure<SubmitError>>(leaving.host.submitHostAction(spec.abortAction))
            assertEquals(before, leaving.session.currentState())
        } finally {
            leaving.close()
        }
    }
}
