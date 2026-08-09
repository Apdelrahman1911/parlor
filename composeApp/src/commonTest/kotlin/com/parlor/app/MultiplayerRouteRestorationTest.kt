package com.parlor.app

import com.parlor.core.ids.GameId
import com.parlor.session.multidevice.MultiplayerSessionRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MultiplayerRouteRestorationTest {
    @Test
    fun resumableWhodunitRouteRestoresScreenAndNameWithoutTransportMetadata() {
        val route = MultiplayerSessionRoute.peer(
            gameId = GameId("whodunit"),
            displayName = "Detective",
            roomCode = "",
            resumeExistingSession = true,
        )

        assertEquals(AppScreen.ResumeWhodunitPeer, route.toOwnedAppScreen())
        assertEquals("Detective", route.ownedPeerDisplayName("whodunit"))
        assertNull(route.ownedPeerDisplayName("mafia"))
    }

    @Test
    fun resumableMafiaRouteRestoresScreenAndNameWithoutTransportMetadata() {
        val route = MultiplayerSessionRoute.peer(
            gameId = GameId("mafia"),
            displayName = "Villager",
            roomCode = "",
            resumeExistingSession = true,
        )

        assertEquals(AppScreen.ResumeMafiaPeer, route.toOwnedAppScreen())
        assertEquals("Villager", route.ownedPeerDisplayName("mafia"))
        assertNull(route.ownedPeerDisplayName("whodunit"))
    }
}
