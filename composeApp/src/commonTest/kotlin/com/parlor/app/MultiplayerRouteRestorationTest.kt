package com.parlor.app

import com.parlor.app.shell.game.DefaultGameShellRegistry
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.core.ids.GameId
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.session.multidevice.MultiplayerSessionRoute
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MultiplayerRouteRestorationTest {
    private val json = Json { encodeDefaults = true }
    private val router = GameShellRouter(
        DefaultGameShellRegistry(
            listOf(
                WhodunitGameShellBinding(WhodunitDefinition(json)),
                MafiaGameShellBinding(MafiaDefinition(json)),
            ),
        ),
    )

    @Test
    fun resumableWhodunitRouteRestoresRegisteredBindingAndName() {
        val route = MultiplayerSessionRoute.peer(
            gameId = GameId("whodunit"),
            displayName = "Detective",
            roomCode = "",
            resumeExistingSession = true,
        )

        val launch = assertIs<GameShellLaunch.RestoreOwnedMultiplayer>(
            router.restoreOwned(route),
        )

        assertEquals("Detective", launch.route.displayName)
        assertEquals(GameId("whodunit"), router.bindingFor(launch)?.definition?.id)
    }

    @Test
    fun resumableMafiaRouteRestoresRegisteredBindingAndName() {
        val route = MultiplayerSessionRoute.peer(
            gameId = GameId("mafia"),
            displayName = "Villager",
            roomCode = "",
            resumeExistingSession = true,
        )

        val launch = assertIs<GameShellLaunch.RestoreOwnedMultiplayer>(
            router.restoreOwned(route),
        )

        assertEquals("Villager", launch.route.displayName)
        assertEquals(GameId("mafia"), router.bindingFor(launch)?.definition?.id)
    }

    @Test
    fun unknown_or_unsupported_game_route_does_not_fall_through() {
        val route = MultiplayerSessionRoute.peer(
            gameId = GameId("modified-client-game"),
            displayName = "Peer",
            roomCode = "A23456",
        )

        assertNull(router.restoreOwned(route))
    }
}
