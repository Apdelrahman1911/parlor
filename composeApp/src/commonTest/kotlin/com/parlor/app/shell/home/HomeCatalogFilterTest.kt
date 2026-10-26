package com.parlor.app.shell.home

import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellCapabilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeCatalogFilterTest {
    @Test
    fun multiplayer_only_games_never_advertise_one_device_play() {
        val capabilities = GameShellCapabilities(setOf(GameEntryMode.Host, GameEntryMode.Join))
        assertFalse(capabilities.supportsLocalPlay())
        assertTrue(capabilities.supportsNearbyPlay())
        assertFalse(HomeCatalogFilter.Local.accepts(capabilities))
        assertTrue(HomeCatalogFilter.Nearby.accepts(capabilities))
        assertTrue(HomeCatalogFilter.All.accepts(capabilities))
    }

    @Test
    fun capabilities_not_game_ids_drive_filters_including_future_solo_only_games() {
        for (mode in GameEntryMode.entries) {
            val capabilities = GameShellCapabilities(setOf(mode))
            assertTrue(HomeCatalogFilter.All.accepts(capabilities))
            if (mode == GameEntryMode.Solo || mode == GameEntryMode.PassAndPlay) {
                assertTrue(HomeCatalogFilter.Local.accepts(capabilities))
                assertFalse(HomeCatalogFilter.Nearby.accepts(capabilities))
            } else {
                assertFalse(HomeCatalogFilter.Local.accepts(capabilities))
                assertTrue(HomeCatalogFilter.Nearby.accepts(capabilities))
            }
        }
    }
}
