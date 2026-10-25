package com.parlor.app.di

import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.engine.registry.GameRegistry
import com.parlor.games.dominoes.DominoIds
import com.parlor.games.dominoes.di.dominoModule
import com.parlor.games.ghamza.GhamzaIds
import com.parlor.games.ghamza.di.ghamzaModule
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.di.lastLightModule
import com.parlor.games.mafia.di.mafiaModule
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.di.whodunitModule
import com.parlor.games.wordimpostor.WordImpostorIds
import com.parlor.games.wordimpostor.di.wordImpostorModule
import kotlinx.serialization.json.Json
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GameShellCompositionTest {
    @Test
    fun composition_root_builds_shell_and_domain_registries_from_the_same_bindings() {
        val application = koinApplication {
            modules(
                module { single { Json { encodeDefaults = true } } },
                whodunitModule,
                mafiaModule,
                lastLightModule,
                dominoModule,
                ghamzaModule,
                wordImpostorModule,
                contentModule,
            )
        }
        try {
            val shellRegistry = application.koin.get<GameShellRegistry>()
            val domainRegistry = application.koin.get<GameRegistry>()
            val expected = listOf(
                WhodunitIds.GameId, MafiaIds.GameId, LastLightIds.GameId,
                DominoIds.Game, GhamzaIds.Game, WordImpostorIds.Game,
            )

            assertEquals(expected, shellRegistry.catalog.map { entry -> entry.gameId })
            assertEquals(expected, domainRegistry.all.map { definition -> definition.id })
            expected.forEach { gameId ->
                assertNotNull(shellRegistry.byId(gameId))
                assertNotNull(domainRegistry.byId(gameId))
            }
            mapOf(DominoIds.Game to 2..4, GhamzaIds.Game to 3..12, WordImpostorIds.Game to 3..12)
                .forEach { (gameId, counts) ->
                    val binding = assertNotNull(shellRegistry.byId(gameId))
                    assertEquals(setOf(GameEntryMode.Host, GameEntryMode.Join), binding.capabilities.entryModes)
                    assertEquals(counts, binding.definition.supportedPlayerCounts)
                    val multiplayer = assertNotNull(binding.multiplayerContract)
                    assertEquals(gameId, multiplayer.gameId)
                    assertEquals(counts, multiplayer.supportedPlayerCounts)
                }
        } finally {
            application.close()
        }
    }
}
