package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.GameState
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.room.RoomLifecycleState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerAuthorityTest {
    @Test
    fun dominoes_authenticates_actors_and_rejects_duplicate_stale_reordered_and_cross_game_commands() = runTest {
        verifyAuthority(
            DominoGameShellBinding(DominoDefinition()), { checkNotNull(it.public.turn) }, ::dominoTurnAction,
            { DominoAction.Rematch(it.public.token) },
        )
    }

    @Test
    fun ghamza_authenticates_actors_and_rejects_duplicate_stale_reordered_and_cross_game_commands() = runTest {
        verifyAuthority(
            GhamzaGameShellBinding(GhamzaDefinition()), { it.players[1].id },
            { state, id -> GhamzaAction.Ready(id, state.public.token) }, { GhamzaAction.Rematch(it.public.token) },
        )
    }

    @Test
    fun word_impostor_authenticates_actors_and_rejects_duplicate_stale_reordered_and_cross_game_commands() = runTest {
        verifyAuthority(
            WordImpostorGameShellBinding(WordImpostorDefinition()), { it.players[1].id },
            { state, id -> WordImpostorAction.Ready(id, state.public.token) }, { WordImpostorAction.Rematch(it.public.token) },
        )
    }

    private suspend fun <S : GameState, A : GameAction, E : GameEvent> TestScope.verifyAuthority(
        spec: MultiplayerGameSpec<S, A, E>, actor: (S) -> PlayerId, action: (S, PlayerId) -> A, hostOnly: (S) -> A,
    ) {
        val fixture = MultiplayerGameFixture(this, spec)
        try {
            fixture.attachPeers()
            if (actor(fixture.session.currentState()) == gameHostId) {
                fixture.perform(action(fixture.session.currentState(), gameHostId))
            }
            val before = fixture.session.currentState()
            val sender = actor(before)
            assertNotEquals(gameHostId, sender)
            val legitimate = action(before, sender)
            val foreignHeaders = listOf(
                fixture.header("old-session").copy(sessionId = SessionId("expired-session")),
                fixture.header("wrong-game").copy(gameId = GameId("another-game")),
                fixture.header("wrong-codec").copy(gameVersion = spec.version + 1),
                fixture.header("old-protocol").copy(protocol = ProtocolVersion(4, 1)),
                fixture.header("new-protocol").copy(protocol = ProtocolVersion(4, 3)),
            )
            foreignHeaders.forEach { header ->
                val command = fixture.command(legitimate, sender).copy(header = header, commandId = header.messageId)
                assertEquals(CommandStatus.IncompatibleVersion, fixture.send(sender, command).status)
            }
            assertEquals(before, fixture.session.currentState())

            val forged = fixture.command(action(before, gameHostId), sender).copy(actor = gameHostId)
            assertEquals(CommandStatus.Unauthorized, fixture.send(sender, forged).status)
            fixture.requestSnapshot(sender)
            assertEquals(CommandStatus.Unauthorized, fixture.send(sender, fixture.command(hostOnly(before), sender)).status)
            fixture.requestSnapshot(sender)
            val lifecycle = fixture.command(legitimate, sender).copy(
                payload = """{"version":1,"action":{"type":"abort"}}""".encodeToByteArray(),
            )
            assertEquals(CommandStatus.InvalidAction, fixture.send(sender, lifecycle).status)
            assertEquals(before, fixture.session.currentState())
            fixture.requestSnapshot(sender)

            fixture.hostRoom.lifecycleState.value = RoomLifecycleState.Suspended(120_000L)
            assertEquals(Result.Failure(SubmitError.SessionSuspended), fixture.host.submitHostAction(hostOnly(before)))
            assertEquals(CommandStatus.SessionSuspended, fixture.send(sender, fixture.command(legitimate, sender)).status)
            fixture.hostRoom.lifecycleState.value = RoomLifecycleState.Active
            fixture.requestSnapshot(sender)

            val sequence = fixture.latestSnapshot(sender).nextExpectedClientSequence
            val outOfOrder = fixture.command(legitimate, sender, sequence = sequence + 1)
            assertEquals(CommandStatus.SequenceGap, fixture.send(sender, outOfOrder).status)
            assertEquals(before, fixture.session.currentState())
            val accepted = fixture.command(legitimate, sender, sequence = sequence)
            assertEquals(CommandStatus.Applied, fixture.send(sender, accepted).status)
            val applied = fixture.session.currentState()
            assertNotEquals(before, applied)
            // The exact same envelope returns its recorded outcome; it cannot execute twice.
            assertEquals(CommandStatus.Applied, fixture.send(sender, accepted).status)
            assertEquals(applied, fixture.session.currentState())
            val newHeader = fixture.header("replayed-sequence")
            assertEquals(CommandStatus.Duplicate,
                fixture.send(sender, accepted.copy(header = newHeader, commandId = newHeader.messageId)).status)
            assertEquals(CommandStatus.StaleRevision, fixture.send(sender, outOfOrder).status)
            assertEquals(applied, fixture.session.currentState())
            fixture.assertSynchronized()
        } finally {
            fixture.close()
        }
    }
}

internal fun dominoTurnAction(state: DominoState, actor: PlayerId): DominoAction {
    val tile = state.privatePerPlayer.getValue(actor).hand.firstOrNull { DominoRules.playableEnds(state, actor, it).isNotEmpty() }
    return when {
        tile != null -> DominoAction.Place(actor, state.public.token, state.public.move, tile.id,
            DominoRules.playableEnds(state, actor, tile).first())
        DominoRules.canDraw(state, actor) -> DominoAction.Draw(actor, state.public.token, state.public.move)
        else -> DominoAction.Pass(actor, state.public.token, state.public.move)
    }
}
