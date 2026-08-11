package com.parlor.app

import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.core.ids.GameId
import kotlin.test.Test
import kotlin.test.assertEquals

class AppBackPolicyTest {
    @Test
    fun home_allows_the_platform_to_finish_the_application() {
        assertEquals(AppBackAction.AllowPlatformExit, appBackAction(AppScreen.Home))
    }

    @Test
    fun settings_navigates_home() {
        assertEquals(AppBackAction.NavigateHome, appBackAction(AppScreen.Settings))
    }

    @Test
    fun registered_game_content_receives_back_and_owns_transactional_cleanup() {
        val game = AppScreen.Game(GameShellLaunch.New(GameId("fixture")))

        assertEquals(AppBackAction.DelegateToGame, appBackAction(game))
    }
}
