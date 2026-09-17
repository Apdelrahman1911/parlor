package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerMalformedSnapshotTest {
    @Test
    fun malformed_payloads_wrong_recipient_slices_and_roster_replacement_never_install_in_any_game() = runTest {
        verify(DominoGameShellBinding(DominoDefinition()))
        verify(GhamzaGameShellBinding(GhamzaDefinition()))
        verify(WordImpostorGameShellBinding(WordImpostorDefinition()))
    }

    private suspend fun <S : GameState, A : GameAction, E : GameEvent> TestScope.verify(spec: MultiplayerGameSpec<S, A, E>) {
        repeat(3) { mutation ->
            val fixture = MultiplayerGameFixture(this, spec)
            try {
                fixture.attachPeers()
                val id = fixture.players[1].id
                val peer = fixture.peers.getValue(id)
                val before = peer.controller.privateStateFor(id).value
                val snapshot = fixture.latestSnapshot(id)
                val replacement = snapshot.publicPayload.decodeToString()
                    .replace("\"displayName\":\"Player 1\"", "\"displayName\":\"Unexpected seat\"")
                assertNotEquals(snapshot.publicPayload.decodeToString(), replacement)
                // This third mutation is otherwise valid game JSON, not merely a parser error.
                spec.decodePublic(replacement.encodeToByteArray())
                val malicious = snapshot.copy(
                    header = fixture.header("invalid-snapshot"), revision = snapshot.revision + 1,
                    publicPayload = when (mutation) {
                        0 -> "{}".encodeToByteArray()
                        2 -> replacement.encodeToByteArray()
                        else -> snapshot.publicPayload
                    },
                    privatePayload = if (mutation == 1) fixture.latestSnapshot(fixture.players[2].id).privatePayload
                        else snapshot.privatePayload,
                )
                fixture.deliver(SendTarget.Direct(id), malicious)
                runCurrent()
                assertEquals(SessionEndReason.IncompatibleVersion, peer.terminalReason.value)
                assertEquals(before, peer.controller.privateStateFor(id).value)
                assertEquals(fixture.session.publicState.value, peer.controller.publicState.value)
            } finally {
                fixture.close()
            }
        }
    }
}
