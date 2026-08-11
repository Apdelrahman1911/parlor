package com.parlor.app

import com.parlor.app.shell.game.GameShellLaunch

/** Global shell response; each game binding owns back policy inside its flow. */
internal enum class AppBackAction {
    AllowPlatformExit,
    NavigateHome,
    DelegateToGame,
}

internal fun appBackAction(screen: AppScreen): AppBackAction = when (screen) {
    AppScreen.Home -> AppBackAction.AllowPlatformExit
    AppScreen.Settings -> AppBackAction.NavigateHome
    is AppScreen.Game -> AppBackAction.DelegateToGame
}

internal sealed interface AppScreen {
    data object Home : AppScreen
    data class Game(val launch: GameShellLaunch) : AppScreen
    data object Settings : AppScreen
}
