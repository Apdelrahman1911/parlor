package com.parlor.app

/** Global shell response; each game binding owns back policy inside its flow. */
internal enum class AppBackAction {
    AllowPlatformExit,
    NavigateGames,
    DelegateToGame,
    HandledByNavDisplay,
}

internal fun appBackAction(route: AppRoute): AppBackAction = when (route) {
    AppRoute.Home -> AppBackAction.AllowPlatformExit
    AppRoute.Settings -> AppBackAction.NavigateGames
    is AppRoute.LocalResumeFailure -> AppBackAction.HandledByNavDisplay
    is AppRoute.Game -> AppBackAction.DelegateToGame
}


/** Prevents guarded game flows from exposing the catalog during predictive Back. */
internal fun <T> visibleEntriesForBack(
    route: AppRoute,
    entries: List<T>,
): List<T> = if (route is AppRoute.Game) entries.takeLast(1) else entries
