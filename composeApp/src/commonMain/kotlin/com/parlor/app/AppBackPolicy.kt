package com.parlor.app

/**
 * Shell-level response to a platform back request.
 *
 * Transaction-sensitive routes consume system Back because their child flow owns
 * the result-bearing save/leave/terminate operation. Those flows expose explicit
 * in-screen actions which must complete before they navigate home.
 */
internal enum class AppBackAction {
    AllowPlatformExit,
    NavigateHome,
    NavigateGameSetup,
    NavigateHostCasePicker,
    Consume,
}

internal fun appBackAction(screen: AppScreen): AppBackAction = when (screen) {
    AppScreen.Home -> AppBackAction.AllowPlatformExit

    AppScreen.LocalCasePicker -> AppBackAction.NavigateGameSetup
    AppScreen.HostMode -> AppBackAction.NavigateHostCasePicker

    AppScreen.GameSetup,
    AppScreen.MafiaSetup,
    AppScreen.HostPermission,
    AppScreen.HostName,
    AppScreen.HostCasePicker,
    AppScreen.JoinPermission,
    AppScreen.JoinName,
    AppScreen.JoinPrompt,
    AppScreen.MafiaHostPermission,
    AppScreen.MafiaHostName,
    AppScreen.MafiaJoinPermission,
    AppScreen.MafiaJoinName,
    AppScreen.MafiaJoinPrompt,
    AppScreen.MultiplayerResumePermission,
    AppScreen.Settings,
    -> AppBackAction.NavigateHome

    AppScreen.Whodunit,
    AppScreen.Mafia,
    AppScreen.HostLobby,
    AppScreen.PeerLobby,
    AppScreen.MafiaHostLobby,
    AppScreen.MafiaPeerLobby,
    AppScreen.ResumeWhodunitPeer,
    AppScreen.ResumeMafiaPeer,
    -> AppBackAction.Consume
}

internal enum class AppScreen {
    Home,
    GameSetup,
    LocalCasePicker, Whodunit,
    MafiaSetup, Mafia,
    MafiaHostPermission, MafiaHostName, MafiaHostLobby,
    MafiaJoinPermission, MafiaJoinName, MafiaJoinPrompt, MafiaPeerLobby,
    HostPermission, HostName, HostCasePicker, HostMode, HostLobby,
    JoinPermission, JoinName, JoinPrompt, PeerLobby,
    MultiplayerResumePermission, ResumeWhodunitPeer, ResumeMafiaPeer,
    Settings,
}
