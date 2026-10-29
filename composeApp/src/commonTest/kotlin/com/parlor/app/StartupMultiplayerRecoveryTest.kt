package com.parlor.app

import com.parlor.app.lifecycle.AppVisibility
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupMultiplayerRecoveryTest {
    @Test
    fun a_process_can_claim_only_one_automatic_attempt() {
        val gate = StartupMultiplayerRecoveryGate()
        assertTrue(gate.claim())
        repeat(32) { assertFalse(gate.claim()) }
    }

    @Test
    fun concurrent_roots_cannot_claim_two_automatic_attempts() = runTest {
        val gate = StartupMultiplayerRecoveryGate()
        val claims = List(64) { async(Dispatchers.Default) { gate.claim() } }.awaitAll()
        assertEquals(1, claims.count { it })
    }

    @Test
    fun startup_is_allowed_only_for_an_active_untouched_idle_unblocked_entry() {
        for (visibility in AppVisibility.entries) {
            for (untouched in listOf(false, true)) {
                for (idle in listOf(false, true)) {
                    for (blocked in listOf(false, true)) {
                        assertEquals(visibility == AppVisibility.Active && untouched && idle && !blocked,
                            permitsStartupMultiplayerRecovery(visibility, untouched, idle, blocked))
                    }
                }
            }
        }
    }

    @Test
    fun settings_and_return_to_games_do_not_rearm_startup() {
        val navigator = AppNavigator()
        assertTrue(navigator.permitsStartupRecovery)
        navigator.selectTopLevel(AppTopLevelDestination.Settings)
        assertFalse(navigator.permitsStartupRecovery)
        navigator.navigateBack(AppRoute.Settings)
        assertEquals(AppRoute.Home, navigator.currentRoute)
        assertFalse(navigator.permitsStartupRecovery)
    }

    @Test
    fun selecting_the_current_catalog_is_an_explicit_navigation_decision() {
        val navigator = AppNavigator()
        assertEquals(AppNavigationMutation.NoChange, navigator.selectTopLevel(AppTopLevelDestination.Games))
        assertFalse(navigator.permitsStartupRecovery)
    }

    @Test
    fun game_and_recovery_navigation_cannot_be_overridden_after_return_to_home() {
        val navigator = AppNavigator()
        val game = navigator.openGame(GameShellLaunch.New(GameId("fixture-game")))
        navigator.navigateHome(game)
        assertFalse(navigator.permitsStartupRecovery)
        val fresh = AppNavigator()
        fresh.showLocalResumeFailure(SessionId("fixture-local"))
        fresh.navigateHome(fresh.currentRoute)
        assertFalse(fresh.permitsStartupRecovery)
    }

    @Test
    fun an_initial_owned_launch_is_never_an_eligible_startup_catalog() {
        val navigator = AppNavigator(GameShellLaunch.New(GameId("fixture-owned-game")))
        assertFalse(navigator.permitsStartupRecovery)
    }
}
