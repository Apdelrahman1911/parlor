package com.parlor.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEvent
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppNavigationTest {
    private val gameId = GameId("fixture-game")

    @Test
    fun every_route_has_one_explicit_top_level_owner() {
        val routes = listOf(
            AppRoute.Home,
            AppRoute.Game(gameId, entryId = 1L),
            AppRoute.LocalResumeFailure(SessionId("saved-game")),
            AppRoute.Settings,
        )

        assertEquals(
            listOf(
                AppTopLevelDestination.Games,
                AppTopLevelDestination.Games,
                AppTopLevelDestination.Games,
                AppTopLevelDestination.Settings,
            ),
            routes.map(AppRoute::owner),
        )
    }

    @Test
    fun top_level_stacks_are_non_empty_and_switch_independently() {
        val navigator = AppNavigator()

        assertEquals(
            listOf(AppRoute.Home),
            navigator.backStack(AppTopLevelDestination.Games).toList(),
        )
        assertEquals(
            listOf(AppRoute.Settings),
            navigator.backStack(AppTopLevelDestination.Settings).toList(),
        )

        assertEquals(
            AppNavigationMutation.Applied,
            navigator.selectTopLevel(AppTopLevelDestination.Settings),
        )
        assertEquals(AppRoute.Settings, navigator.currentRoute)
        assertEquals(
            AppNavigationMutation.Applied,
            navigator.navigateBack(AppRoute.Settings),
        )
        assertEquals(AppRoute.Home, navigator.currentRoute)
    }

    @Test
    fun a_game_launch_is_process_local_and_cannot_be_bypassed_by_tab_selection() {
        val launch = GameShellLaunch.ResumeMultiplayer(gameId, displayName = "Private player")
        val navigator = AppNavigator()

        val route = navigator.openGame(launch)

        assertSame(launch, navigator.launchFor(route))
        assertEquals(
            listOf(AppRoute.Home, route),
            navigator.backStack(AppTopLevelDestination.Games).toList(),
        )
        assertEquals(
            AppNavigationMutation.RejectedActiveFlow,
            navigator.selectTopLevel(AppTopLevelDestination.Settings),
        )
        assertEquals(
            AppNavigationMutation.RejectedActiveFlow,
            navigator.navigateBack(route),
        )
        assertEquals(route, navigator.currentRoute)

        val serializedRoute = Json.encodeToString<AppRoute>(route)
        assertEquals(
            """{"type":"game","gameId":"fixture-game","entryId":1}""",
            serializedRoute,
        )
        assertFalse(serializedRoute.contains("Private player"))
    }

    @Test
    fun authorized_game_exit_clears_the_transient_launch_and_returns_home() {
        val navigator = AppNavigator()
        val route = navigator.openGame(GameShellLaunch.New(gameId))

        assertEquals(
            AppNavigationMutation.Applied,
            navigator.navigateHome(route),
        )

        assertEquals(AppRoute.Home, navigator.currentRoute)
        assertEquals(
            listOf(AppRoute.Home),
            navigator.backStack(AppTopLevelDestination.Games).toList(),
        )
        assertNull(navigator.launchFor(route))
    }

    @Test
    fun recovery_back_pops_only_the_games_stack() {
        val navigator = AppNavigator()
        navigator.showLocalResumeFailure(SessionId("damaged-save"))

        assertEquals(
            AppNavigationMutation.Applied,
            navigator.navigateBack(AppRoute.LocalResumeFailure(SessionId("damaged-save"))),
        )

        assertEquals(AppRoute.Home, navigator.currentRoute)
        assertEquals(
            listOf(AppRoute.Settings),
            navigator.backStack(AppTopLevelDestination.Settings).toList(),
        )
    }

    @Test
    fun stale_callbacks_cannot_leave_a_replacement_route() {
        val navigator = AppNavigator()
        val firstRoute = navigator.openGame(GameShellLaunch.New(gameId))
        val replacementLaunch = GameShellLaunch.New(gameId)
        val replacementRoute = navigator.openGame(replacementLaunch)

        assertEquals(
            AppNavigationMutation.RejectedStaleRoute,
            navigator.navigateHome(firstRoute),
        )
        assertEquals(replacementRoute, navigator.currentRoute)
        assertSame(replacementLaunch, navigator.launchFor(replacementRoute))
    }

    @Test
    fun duplicate_back_completion_cannot_pop_a_new_recovery_route() {
        val firstRoute = AppRoute.LocalResumeFailure(SessionId("first-save"))
        val secondRoute = AppRoute.LocalResumeFailure(SessionId("second-save"))
        val navigator = AppNavigator().also {
            it.showLocalResumeFailure(firstRoute.sessionId)
        }

        navigator.showLocalResumeFailure(secondRoute.sessionId)

        assertEquals(
            AppNavigationMutation.RejectedStaleRoute,
            navigator.navigateBack(firstRoute),
        )
        assertEquals(secondRoute, navigator.currentRoute)
    }

    @Test
    fun game_route_requires_a_positive_entry_identity() {
        assertFailsWith<IllegalArgumentException> {
            AppRoute.Game(gameId, entryId = 0L)
        }
    }

    @Test
    fun route_serial_names_round_trip_as_a_stable_contract() {
        val json = Json { classDiscriminator = "route" }
        val routes = listOf<AppRoute>(
            AppRoute.Home,
            AppRoute.Game(gameId, entryId = 9L),
            AppRoute.LocalResumeFailure(SessionId("saved-game")),
            AppRoute.Settings,
        )

        routes.forEach { route ->
            assertEquals(route, json.decodeFromString<AppRoute>(json.encodeToString(route)))
        }
    }

    @Test
    fun regular_navigation_directions_follow_the_logical_layout_direction() {
        assertEquals(
            AnimatedContentTransitionScope.SlideDirection.Left,
            navigationForwardDirection(LayoutDirection.Ltr),
        )
        assertEquals(
            AnimatedContentTransitionScope.SlideDirection.Right,
            navigationBackDirection(LayoutDirection.Ltr),
        )
        assertEquals(
            AnimatedContentTransitionScope.SlideDirection.Right,
            navigationForwardDirection(LayoutDirection.Rtl),
        )
        assertEquals(
            AnimatedContentTransitionScope.SlideDirection.Left,
            navigationBackDirection(LayoutDirection.Rtl),
        )
    }

    @Test
    fun predictive_navigation_uses_the_physical_swipe_edge() {
        assertEquals(
            AnimatedContentTransitionScope.SlideDirection.Right,
            navigationPredictiveBackDirection(
                swipeEdge = NavigationEvent.EDGE_LEFT,
                layoutDirection = LayoutDirection.Rtl,
            ),
        )
        assertEquals(
            AnimatedContentTransitionScope.SlideDirection.Left,
            navigationPredictiveBackDirection(
                swipeEdge = NavigationEvent.EDGE_RIGHT,
                layoutDirection = LayoutDirection.Ltr,
            ),
        )
    }
}
