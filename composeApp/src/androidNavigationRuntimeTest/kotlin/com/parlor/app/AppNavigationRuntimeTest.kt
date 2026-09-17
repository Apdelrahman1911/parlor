package com.parlor.app

import android.test.InstrumentationTestCase
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId

/**
 * Run on Android below API 35: JDK 21 unit tests cannot detect calls to the
 * newer java.util.List.removeLast member emitted for NavBackStack on Android.
 * CI runs these internal-API assertions against Debug on API 34, alongside
 * (not instead of) the black-box smoke tests of the fully optimized Release.
 * All routes and launches are synthetic; this test never reads or resets saves.
 */
@Suppress("DEPRECATION")
class AppNavigationRuntimeTest : InstrumentationTestCase() {
    fun testAuthorizedGameExitClearsLaunchAndPreservesRoot() {
        val navigator = AppNavigator()
        val route = navigator.openGame(GameShellLaunch.New(GameId("back-runtime-fixture")))

        assertEquals(AppNavigationMutation.RejectedActiveFlow, navigator.navigateBack(route))
        assertEquals(route, navigator.currentRoute)
        assertEquals(AppNavigationMutation.Applied, navigator.navigateHome(route))
        assertEquals(listOf(AppRoute.Home), navigator.backStack(AppTopLevelDestination.Games))
        assertNull(navigator.launchFor(route))
        assertEquals(AppNavigationMutation.RejectedStaleRoute, navigator.navigateHome(route))
        assertEquals(AppNavigationMutation.NoChange, navigator.navigateBack(AppRoute.Home))
    }

    fun testRecoveryBackPopsOnceWithoutChangingSettingsStack() {
        val navigator = AppNavigator()
        val route = AppRoute.LocalResumeFailure(SessionId("back-runtime-save"))
        navigator.showLocalResumeFailure(route.sessionId)

        assertEquals(AppNavigationMutation.Applied, navigator.navigateBack(route))
        assertEquals(listOf(AppRoute.Home), navigator.backStack(AppTopLevelDestination.Games))
        assertEquals(listOf(AppRoute.Settings), navigator.backStack(AppTopLevelDestination.Settings))
        assertEquals(AppNavigationMutation.RejectedStaleRoute, navigator.navigateBack(route))
    }

    fun testReplacingGameClearsOldLaunchAndRejectsItsExit() {
        val navigator = AppNavigator()
        val oldRoute = navigator.openGame(GameShellLaunch.New(GameId("back-runtime-fixture")))
        val launch = GameShellLaunch.New(GameId("back-runtime-replacement"))
        val replacement = navigator.openGame(launch)

        assertNull(navigator.launchFor(oldRoute))
        assertSame(launch, navigator.launchFor(replacement))
        assertEquals(listOf(AppRoute.Home, replacement), navigator.backStack(AppTopLevelDestination.Games))
        assertEquals(AppNavigationMutation.RejectedStaleRoute, navigator.navigateHome(oldRoute))
        assertEquals(replacement, navigator.currentRoute)
    }
}
