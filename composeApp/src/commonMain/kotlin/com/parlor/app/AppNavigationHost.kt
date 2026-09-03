package com.parlor.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.parlor.app.resources.Res
import com.parlor.app.resources.navigation_games_description
import com.parlor.app.resources.navigation_games_label
import com.parlor.app.resources.navigation_settings_description
import com.parlor.app.resources.navigation_settings_label
import com.parlor.designsystem.components.ParlorBottomTab
import com.parlor.designsystem.components.ParlorBottomTabBar
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The single Navigation 3 renderer for the application shell.
 *
 * Decorated entries are retained independently for each top-level stack. A
 * game route is projected without its previous entry so Nav3 cannot reveal or
 * pop the catalog before the game has completed its save/leave policy.
 */
@Composable
internal fun AppNavDisplay(
    navigator: AppNavigator,
    homeContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    recoveryContent: @Composable (AppRoute.LocalResumeFailure) -> Unit,
    gameContent: @Composable (AppRoute.Game) -> Unit,
    onBack: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestHomeContent = rememberUpdatedState(homeContent)
    val latestSettingsContent = rememberUpdatedState(settingsContent)
    val latestRecoveryContent = rememberUpdatedState(recoveryContent)
    val latestGameContent = rememberUpdatedState(gameContent)
    val routeProvider = remember {
        entryProvider<AppRoute> {
            entry<AppRoute.Home> { latestHomeContent.value() }
            entry<AppRoute.Settings> { latestSettingsContent.value() }
            entry<AppRoute.LocalResumeFailure> { route ->
                latestRecoveryContent.value(route)
            }
            entry<AppRoute.Game> { route -> latestGameContent.value(route) }
        }
    }

    val gamesDecorator = rememberSaveableStateHolderNavEntryDecorator<AppRoute>()
    val settingsDecorator = rememberSaveableStateHolderNavEntryDecorator<AppRoute>()
    val gamesEntries = rememberDecoratedNavEntries(
        backStack = navigator.backStack(AppTopLevelDestination.Games),
        entryDecorators = remember(gamesDecorator) { listOf(gamesDecorator) },
        entryProvider = routeProvider,
    )
    val settingsEntries = rememberDecoratedNavEntries(
        backStack = navigator.backStack(AppTopLevelDestination.Settings),
        entryDecorators = remember(settingsDecorator) { listOf(settingsDecorator) },
        entryProvider = routeProvider,
    )
    val currentRoute = navigator.currentRoute
    val activeEntries = when (navigator.selectedDestination) {
        AppTopLevelDestination.Games -> gamesEntries
        AppTopLevelDestination.Settings -> settingsEntries
    }
    val visibleEntries = visibleEntriesForBack(currentRoute, activeEntries)
    val layoutDirection = LocalLayoutDirection.current
    val reducedMotion = ParlorTheme.reducedMotion
    val motion = ParlorTheme.motion
    val transitionSpec = remember(layoutDirection, reducedMotion, motion) {
        if (reducedMotion) {
            reducedNavigationTransitionSpec<AppRoute>(
                durationMillis = motion.durationFast,
                easing = motion.easingStandard,
            )
        } else {
            platformNavigationTransitionSpec(layoutDirection)
        }
    }
    val popTransitionSpec = remember(layoutDirection, reducedMotion, motion) {
        if (reducedMotion) {
            reducedNavigationTransitionSpec<AppRoute>(
                durationMillis = motion.durationFast,
                easing = motion.easingStandard,
            )
        } else {
            platformNavigationPopTransitionSpec(layoutDirection)
        }
    }
    val predictivePopTransitionSpec = remember(layoutDirection, reducedMotion, motion) {
        if (reducedMotion) {
            reducedNavigationPredictivePopTransitionSpec<AppRoute>(motion.durationFast)
        } else {
            platformNavigationPredictivePopTransitionSpec(layoutDirection)
        }
    }

    NavDisplay(
        entries = visibleEntries,
        onBack = { onBack(currentRoute) },
        modifier = modifier.fillMaxSize(),
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
        predictivePopTransitionSpec = predictivePopTransitionSpec,
    )
}

/** Localized presentation of the two real top-level destinations. */
@Composable
internal fun AppNavigationBar(
    selectedDestination: AppTopLevelDestination,
    onDestinationSelected: (AppTopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = AppTopLevelDestination.entries
    val tabs = listOf(
        ParlorBottomTab(
            label = stringResource(Res.string.navigation_games_label),
            contentDescription = stringResource(Res.string.navigation_games_description),
            icon = ParlorIcons.FolderOpen,
        ),
        ParlorBottomTab(
            label = stringResource(Res.string.navigation_settings_label),
            contentDescription = stringResource(Res.string.navigation_settings_description),
            icon = ParlorIcons.Settings,
        ),
    )
    ParlorBottomTabBar(
        tabs = tabs,
        selectedIndex = destinations.indexOf(selectedDestination),
        onTabSelected = { index -> onDestinationSelected(destinations[index]) },
        modifier = modifier,
    )
}
