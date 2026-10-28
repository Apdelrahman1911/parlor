package com.parlor.app.shell.home

import androidx.compose.runtime.saveable.SaverScope
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HomeCatalogScrollStateTest {
    @Test
    fun public_anchor_restores_against_current_recovery_rows_not_an_old_index() {
        val games = listOf(DominoGameShellBinding(DominoDefinition()), GhamzaGameShellBinding(GhamzaDefinition()))
        val rows = listOf(LocalRecoveryEntry(SessionId("synthetic-save"), GameId("whodunit")))
        val ready = homeCatalogItemKeys(games, rows, true, false, true)
        assertEquals(
            listOf("home-top-bar", "home-lead", "recovery-unavailable", "continue-label",
                "local:synthetic-save", "multiplayer-resume", "games-label",
                "game:egyptian-dominoes", "game:ghamza"),
            ready,
        )
        val anchor = HomeCatalogAnchor("game:ghamza", 23)
        assertEquals(8 to 23, anchor.target(ready))
        assertEquals(4 to 23, anchor.target(homeCatalogItemKeys(games, emptyList(), false, false, false)))
        assertEquals(5 to 23, anchor.target(homeCatalogItemKeys(games, emptyList(), false, true, true)))
    }

    @Test
    fun absent_or_filtered_anchor_returns_to_the_catalog_heading_without_an_old_offset() {
        val empty = homeCatalogItemKeys(emptyList(), emptyList(), false, false, false)
        assertEquals(listOf("home-top-bar", "home-lead", "games-label", "catalog-empty"), empty)
        assertEquals(2 to 0, HomeCatalogAnchor("game:removed-game", 240).target(empty))
        assertEquals(0 to 0, HomeCatalogAnchor("game:removed-game", 240).target(emptyList()))
    }

    @Test
    fun platform_saver_retains_the_original_pending_anchor_during_loading() {
        val state = assertNotNull(HomeCatalogScrollState.saver.restore(listOf("game:ghamza", 23)))
        // Its new LazyListState has no measured items yet. Saving again must not lose the key.
        assertEquals(listOf("game:ghamza", 23), save(state))
        state.cancelPendingRestoration()
        assertEquals(listOf("home-top-bar", 0), save(state))
    }

    @Test
    fun platform_saved_state_contains_no_recovery_identifier_or_arbitrary_ui_key() {
        for (key in listOf("local:synthetic-save", "multiplayer-resume", "recovery-loading", "game:Invalid",
            "game:" + "a".repeat(128))) {
            val state = assertNotNull(HomeCatalogScrollState.saver.restore(listOf(key, 23)))
            assertEquals(listOf("games-label", 0), save(state))
        }
        assertEquals(HomeCatalogAnchor("game:ghamza", 0), publicCatalogAnchor("game:ghamza", -1))
    }

    @Test
    fun malformed_platform_values_restore_a_safe_public_origin_without_throwing() {
        for (values in listOf(emptyList(), listOf(8), listOf(8, 23), listOf("game:ghamza", "23"),
            listOf("game:ghamza", 23, "extra"))) {
            val state = assertNotNull(HomeCatalogScrollState.saver.restore(values))
            assertEquals(listOf("home-top-bar", 0), save(state))
        }
    }

    private fun save(state: HomeCatalogScrollState): Any? = with(HomeCatalogScrollState.saver) {
        object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = value is String || value is Int
        }.save(state)
    }
}
