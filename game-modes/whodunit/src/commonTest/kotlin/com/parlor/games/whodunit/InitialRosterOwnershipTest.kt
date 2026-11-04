package com.parlor.games.whodunit

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class InitialRosterOwnershipTest {
    private val definition = WhodunitDefinition(Json)

    @Test
    fun initialStateDoesNotFollowCallerRosterReplacementOrRemoval() {
        for (mode in definition.supportedModes) {
            for (count in mode.supportedPlayerCounts) {
                val input = roster(count).toMutableList()
                val original = input.toList()
                val state = definition.createInitialState(config(input, mode.id))

                input[0] = Player(PlayerId("replacement"), "Replacement", 0)
                input.removeAt(input.lastIndex)
                input.clear()

                assertEquals(original, state.players, "mode=${mode.id.raw}, count=$count")
                assertEquals(original, state.public.playersAtTable)
                assertEquals(original, definition.projectionPolicy().toPublic(state).state.players)
            }
        }
    }

    @Test
    fun reusingConfigurationDoesNotRewriteAnEarlierInitialState() {
        for (mode in definition.supportedModes) {
            for (count in mode.supportedPlayerCounts) {
                val input = roster(count).toMutableList()
                val original = input.toList()
                val configuration = config(input, mode.id)
                val first = definition.createInitialState(configuration)

                input.indices.forEach { seat ->
                    input[seat] = input[seat].copy(displayName = "Second Player $seat")
                }
                val updated = input.toList()
                val second = definition.createInitialState(configuration)
                input.clear()

                assertEquals(original, first.players, "mode=${mode.id.raw}, first count=$count")
                assertEquals(updated, second.players, "mode=${mode.id.raw}, second count=$count")
                assertEquals(original, first.public.playersAtTable)
                assertEquals(updated, second.public.playersAtTable)
            }
        }
    }

    private fun roster(count: Int): List<Player> = List(count) { seat ->
        Player(PlayerId("player-$seat"), "Player $seat", seat)
    }

    private fun config(players: List<Player>, mode: ModeId): SessionConfig = SessionConfig(
        sessionId = SessionId("ownership-test"),
        caseId = CaseId("last-dinner"),
        modeId = mode,
        players = players,
        randomSeed = 42L,
    )
}
