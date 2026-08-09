package com.parlor.app.di

import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.engine.registry.GameRegistry
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.di.mafiaModule
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.di.whodunitModule
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
                contentModule,
            )
        }
        try {
            val shellRegistry = application.koin.get<GameShellRegistry>()
            val domainRegistry = application.koin.get<GameRegistry>()
            val expected = listOf(WhodunitIds.GameId, MafiaIds.GameId)

            assertEquals(expected, shellRegistry.catalog.map { entry -> entry.gameId })
            assertEquals(expected, domainRegistry.all.map { definition -> definition.id })
            expected.forEach { gameId ->
                assertNotNull(shellRegistry.byId(gameId))
                assertNotNull(domainRegistry.byId(gameId))
            }
        } finally {
            application.close()
        }
    }
}
