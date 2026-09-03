package com.parlor.app

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals

class AppBackPolicyTest {
    @Test
    fun home_allows_the_platform_to_finish_the_application() {
        assertEquals(AppBackAction.AllowPlatformExit, appBackAction(AppRoute.Home))
    }

    @Test
    fun settings_navigates_to_the_games_tab() {
        assertEquals(AppBackAction.NavigateGames, appBackAction(AppRoute.Settings))
    }

    @Test
    fun unreadable_save_recovery_is_owned_by_nav_display() {
        val recovery = AppRoute.LocalResumeFailure(SessionId("damaged-save"))

        assertEquals(AppBackAction.HandledByNavDisplay, appBackAction(recovery))
    }

    @Test
    fun guarded_game_exposes_only_its_current_entry_to_predictive_back() {
        val entries = listOf("catalog", "game")

        assertEquals(
            listOf("game"),
            visibleEntriesForBack(AppRoute.Game(GameId("fixture"), entryId = 1L), entries),
        )
        assertEquals(
            entries,
            visibleEntriesForBack(
                AppRoute.LocalResumeFailure(SessionId("damaged-save")),
                entries,
            ),
        )
    }

    @Test
    fun registered_game_content_receives_back_and_owns_transactional_cleanup() {
        val game = AppRoute.Game(GameId("fixture"), entryId = 1L)

        assertEquals(AppBackAction.DelegateToGame, appBackAction(game))
    }
}
