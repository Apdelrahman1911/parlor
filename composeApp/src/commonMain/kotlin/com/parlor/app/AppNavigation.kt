package com.parlor.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stable top-level destinations rendered by the application navigation bar. */
internal enum class AppTopLevelDestination {
    Games,
    Settings,
}

/**
 * Minimal Navigation 3 keys for the application shell.
 *
 * [Game] intentionally contains only safe identifiers. The launch object may
 * contain a display name or an owned multiplayer route, so it stays in the
 * process-local [AppNavigator] and is never serialized as navigation state.
 */
@Serializable
internal sealed interface AppRoute : NavKey {
    @Serializable
    @SerialName("home")
    data object Home : AppRoute

    @Serializable
    @SerialName("game")
    data class Game(
        val gameId: GameId,
        val entryId: Long,
    ) : AppRoute {
        init {
            require(entryId > 0L) { "Game navigation entry id must be positive" }
        }
    }

    @Serializable
    @SerialName("local-resume-failure")
    data class LocalResumeFailure(val sessionId: SessionId) : AppRoute

    @Serializable
    @SerialName("settings")
    data object Settings : AppRoute
}

internal fun AppRoute.owner(): AppTopLevelDestination = when (this) {
    AppRoute.Home,
    is AppRoute.Game,
    is AppRoute.LocalResumeFailure,
    -> AppTopLevelDestination.Games
    AppRoute.Settings -> AppTopLevelDestination.Settings
}

internal val AppRoute.isTopLevel: Boolean
    get() = this == AppRoute.Home || this == AppRoute.Settings

internal enum class AppNavigationMutation {
    Applied,
    NoChange,
    RejectedActiveFlow,
    RejectedStaleRoute,
}

/**
 * The application's only navigation state owner and mutation boundary.
 *
 * Each navigation-bar destination owns an independent non-empty Nav3 stack.
 * Game-owned flows are deliberately not selectable from the bottom bar: an
 * active game must authorize and complete its own save/leave transaction.
 * These shell stacks are intentionally process-local. Process recovery starts
 * from the catalog and re-enters a game only through authenticated snapshots
 * or the transport's validated rejoin ownership; private launch/session
 * objects are never serialized into Navigation 3 state.
 */
internal class AppNavigator(initialGameLaunch: GameShellLaunch? = null) {
    private val gamesBackStack = NavBackStack<AppRoute>(AppRoute.Home)
    private val settingsBackStack = NavBackStack<AppRoute>(AppRoute.Settings)
    private var selectedState by mutableStateOf(AppTopLevelDestination.Games)
    private var nextGameEntryId = 0L
    private var activeGameLaunch: Pair<AppRoute.Game, GameShellLaunch>? = null

    init {
        initialGameLaunch?.let(::openGame)
    }

    val selectedDestination: AppTopLevelDestination
        get() = selectedState

    val currentRoute: AppRoute
        get() = backStack(selectedState).last()

    fun backStack(destination: AppTopLevelDestination): List<AppRoute> = when (destination) {
        AppTopLevelDestination.Games -> gamesBackStack
        AppTopLevelDestination.Settings -> settingsBackStack
    }

    fun selectTopLevel(destination: AppTopLevelDestination): AppNavigationMutation {
        if (!currentRoute.isTopLevel) return AppNavigationMutation.RejectedActiveFlow
        if (selectedState == destination) return AppNavigationMutation.NoChange
        selectedState = destination
        return AppNavigationMutation.Applied
    }

    fun openGame(launch: GameShellLaunch): AppRoute.Game {
        resetGamesStack()
        selectedState = AppTopLevelDestination.Games
        val route = AppRoute.Game(
            gameId = launch.gameId,
            entryId = nextGameEntryId(),
        )
        activeGameLaunch = route to launch
        gamesBackStack += route
        return route
    }

    fun showLocalResumeFailure(sessionId: SessionId) {
        resetGamesStack()
        selectedState = AppTopLevelDestination.Games
        gamesBackStack += AppRoute.LocalResumeFailure(sessionId)
    }

    /** Completes an already-authorized flow exit if its route is still active. */
    fun navigateHome(expectedRoute: AppRoute): AppNavigationMutation {
        if (currentRoute != expectedRoute) return AppNavigationMutation.RejectedStaleRoute
        if (currentRoute == AppRoute.Home) return AppNavigationMutation.NoChange
        resetGamesStack()
        selectedState = AppTopLevelDestination.Games
        return AppNavigationMutation.Applied
    }

    /** Handles only unguarded shell Back and rejects callbacks from stale entries. */
    fun navigateBack(expectedRoute: AppRoute): AppNavigationMutation {
        if (currentRoute != expectedRoute) return AppNavigationMutation.RejectedStaleRoute
        if (currentRoute is AppRoute.Game) return AppNavigationMutation.RejectedActiveFlow
        val active = mutableBackStack(selectedState)
        if (active.size > 1) {
            active.removeLast()
            return AppNavigationMutation.Applied
        }
        if (selectedState == AppTopLevelDestination.Settings) {
            selectedState = AppTopLevelDestination.Games
            return AppNavigationMutation.Applied
        }
        return AppNavigationMutation.NoChange
    }

    fun launchFor(route: AppRoute.Game): GameShellLaunch? =
        activeGameLaunch?.takeIf { (activeRoute, _) -> activeRoute == route }?.second

    private fun resetGamesStack() {
        while (gamesBackStack.size > 1) gamesBackStack.removeLast()
        activeGameLaunch = null
    }

    private fun mutableBackStack(destination: AppTopLevelDestination): NavBackStack<AppRoute> =
        when (destination) {
            AppTopLevelDestination.Games -> gamesBackStack
            AppTopLevelDestination.Settings -> settingsBackStack
        }

    private fun nextGameEntryId(): Long {
        nextGameEntryId = if (nextGameEntryId == Long.MAX_VALUE) 1L else nextGameEntryId + 1L
        return nextGameEntryId
    }
}
