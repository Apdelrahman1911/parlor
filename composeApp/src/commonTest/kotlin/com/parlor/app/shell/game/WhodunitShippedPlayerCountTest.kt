package com.parlor.app.shell.game

import com.parlor.games.whodunit.WhodunitDefinition
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WhodunitShippedPlayerCountTest {
    @Test
    fun releaseMultiplayerRangeMatchesTheOnlyBundledPlayerCount() {
        assertEquals(6..6, SHIPPED_WHODUNIT_PLAYER_COUNTS)
    }

    @Test
    fun releaseMultiplayerRangeRemainsWithinEngineCapabilities() {
        val definition = WhodunitDefinition(Json { encodeDefaults = true })

        SHIPPED_WHODUNIT_PLAYER_COUNTS.forEach { playerCount ->
            assertEquals(true, playerCount in definition.supportedPlayerCounts)
        }
    }
}
