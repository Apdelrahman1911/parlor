package com.parlor.app.shell.game.multiplayer

import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.GameState
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import com.parlor.session.multidevice.PlayerSnapshotPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiplayerProjectionTest {
    @Test
    fun projections_are_recipient_bound_and_host_entropy_never_leaves_the_authority() {
        verifyProjections(DominoGameShellBinding(DominoDefinition()))
        verifyProjections(GhamzaGameShellBinding(GhamzaDefinition()))
        verifyProjections(WordImpostorGameShellBinding(WordImpostorDefinition()))
    }

    private fun <S : GameState, A : GameAction, E : GameEvent> verifyProjections(spec: MultiplayerGameSpec<S, A, E>) {
        val seed = 928_184_317_445_978_313L
        val state = spec.definition.createInitialState(SessionConfig(
            SessionId("synthetic-wire-test"), spec.defaultCaseId, spec.modeId, gamePlayers(3), seed,
        ))
        val payloads = state.players.associate { it.id to spec.snapshotFor(state, it.id) }
        payloads.forEach { (id, payload) ->
            val public = spec.decodePublic(payload.publicPayload)
            assertEquals(spec.definition.projectionPolicy().toPublic(state).state, public)
            assertEquals(spec.definition.projectionPolicy().toPlayer(state, id).state,
                spec.decodePlayer(public, payload.privatePayload, id))
            for (bytes in listOf(payload.publicPayload, payload.privatePayload)) {
                val text = bytes.decodeToString()
                assertFalse(text.contains(seed.toString()))
                assertFalse(text.contains("hostOnly"))
                assertFalse(text.contains("history"))
                assertFalse(text.contains("privatePerPlayer"))
            }
            val other = state.players.first { it.id != id }.id
            assertFailsWith<IllegalArgumentException> { spec.decodePlayer(public, payload.privatePayload, other) }
        }
        assertFailsWith<IllegalArgumentException> { spec.snapshotFor(state, PlayerId("unadmitted-seat")) }
        assertTrue(payloads.values.map(PlayerSnapshotPayload::publicPayload).zipWithNext().all { (a, b) -> a.contentEquals(b) })
    }
}
