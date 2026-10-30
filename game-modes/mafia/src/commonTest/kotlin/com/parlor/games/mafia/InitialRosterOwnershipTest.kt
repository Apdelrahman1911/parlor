package com.parlor.games.mafia

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class InitialRosterOwnershipTest {
    private val definition = MafiaDefinition(Json)

    @Test
    fun initialStateDoesNotFollowCallerRosterReplacementOrRemoval() {
        for (count in definition.supportedPlayerCounts) {
            val input = roster(count).toMutableList()
            val original = input.toList()
            val state = definition.createInitialState(config(input))

            input[0] = Player(PlayerId("replacement"), "Replacement", 0)
            input.removeAt(input.lastIndex)
            input.clear()

            assertEquals(original, state.players, "count=$count")
            assertEquals(original.map(Player::displayName), state.public.roster.map { it.displayName })
            assertEquals(original, definition.projectionPolicy().toPublic(state).state.players)
        }
    }

    @Test
    fun reusingConfigurationDoesNotRewriteAnEarlierInitialState() {
        for (count in definition.supportedPlayerCounts) {
            val input = roster(count).toMutableList()
            val original = input.toList()
            val configuration = config(input)
            val first = definition.createInitialState(configuration)

            input.indices.forEach { seat ->
                input[seat] = input[seat].copy(displayName = "Second Player $seat")
            }
            val updated = input.toList()
            val second = definition.createInitialState(configuration)
            input.clear()

            assertEquals(original, first.players, "first count=$count")
            assertEquals(updated, second.players, "second count=$count")
            assertEquals(original.map(Player::displayName), first.public.roster.map { it.displayName })
            assertEquals(updated.map(Player::displayName), second.public.roster.map { it.displayName })
        }
    }

    private fun roster(count: Int): List<Player> = List(count) { seat ->
        Player(PlayerId("player-$seat"), "Player $seat", seat)
    }

    private fun config(players: List<Player>): SessionConfig = SessionConfig(
        sessionId = SessionId("ownership-test"),
        caseId = CaseId("classic"),
        modeId = MafiaIds.ClassicModeId,
        players = players,
        randomSeed = 42L,
    )
}
