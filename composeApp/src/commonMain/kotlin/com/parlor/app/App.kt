package com.parlor.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.parlor.app.resources.Res
import com.parlor.app.resources.home_resume_open_failed
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GameShellBackRequest
import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.app.shell.home.HomeScreen
import com.parlor.app.shell.settings.SettingsScreen
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastHost
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.localization.customAppLocale
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.routeOrNull
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Parlor's application shell. It owns only global screens and resolves games
 * through [GameShellRegistry]; setup, local play, hosting, joining and resume
 * sub-navigation belong to each registered [com.parlor.app.shell.game.GameShellBinding].
 */
@Suppress("LongMethod") // Declarative root composition; stateful effects are separately tested.
@Composable
fun App() {
    val settings: SettingsStore = koinInject()
    val languageTag by settings.languageOverride.collectAsState(initial = null)
    val themeModeTag by settings.themeMode.collectAsState(initial = ThemeMode.Default.tag)
    val reducedMotion by settings.reducedMotion.collectAsState(initial = false)
    val language = AppLanguage.fromTag(languageTag)
    val themeMode = ThemeMode.fromTag(themeModeTag)
    LaunchedEffect(language) { customAppLocale = language.tag }

    val snapshotStore: SnapshotStore = koinInject()
    val roomTransport: RoomTransport = koinInject()
    val multiplayerSessionOwner: ProcessMultiplayerSessionOwner = koinInject()
    val gameShellRegistry: GameShellRegistry = koinInject()
    val gameShellRouter = remember(gameShellRegistry) { GameShellRouter(gameShellRegistry) }
    val multiplayerOwnerState by multiplayerSessionOwner.state.collectAsState()
    val ownedMultiplayerRoute = multiplayerOwnerState.routeOrNull

    val toastState = remember { ParlorToastState() }
    val appScope = rememberCoroutineScope()

    ProvideAppLanguage(language = language) {
        ParlorTheme(
            themeMode = themeMode,
            reducedMotion = reducedMotion,
        ) {
            CompositionLocalProvider(LocalParlorToastState provides toastState) {
                val resumeOpenFailedText = stringResource(Res.string.home_resume_open_failed)
                val initialLaunch = remember(ownedMultiplayerRoute, gameShellRouter) {
                    ownedMultiplayerRoute?.let(gameShellRouter::restoreOwned)
                }
                var screen: AppScreen by remember {
                    mutableStateOf(
                        initialLaunch?.let(AppScreen::Game) ?: AppScreen.Home,
                    )
                }
                var localResumeJob: Job? by remember { mutableStateOf(null) }
                val localResumeGate = remember { LocalResumeRequestGate() }
                var unfinishedRefreshKey by remember { mutableStateOf(0) }
                var hadOwnedMultiplayerRoute by remember {
                    mutableStateOf(ownedMultiplayerRoute != null)
                }
                val activeGameLaunch = (screen as? AppScreen.Game)?.launch
                var gameBackRequest by remember(activeGameLaunch) {
                    mutableStateOf(GameShellBackRequest.Initial)
                }

                val unfinishedSessions by produceState(
                    initialValue = emptyList<SessionId>(),
                    key1 = screen,
                    key2 = unfinishedRefreshKey,
                ) {
                    value = if (screen == AppScreen.Home) {
                        when (val result = snapshotStore.listUnfinished()) {
                            is Result.Success -> result.data
                            is Result.Failure -> emptyList()
                        }
                    } else {
                        value
                    }
                }

                val resumableMultiplayer by produceState<ResumableSessionInfo?>(
                    initialValue = null,
                    key1 = screen,
                    key2 = unfinishedRefreshKey,
                ) {
                    value = if (screen == AppScreen.Home) {
                        when (val result = roomTransport.resumableSession()) {
                            is Result.Success -> result.data?.takeIf { info ->
                                gameShellRouter.resumeMultiplayer(
                                    gameId = info.gameId,
                                    gameVersion = info.gameVersion,
                                    displayName = info.displayName,
                                ) != null
                            }
                            is Result.Failure -> null
                        }
                    } else {
                        value
                    }
                }

                val backToHome: () -> Unit = {
                    localResumeGate.invalidate()
                    localResumeJob?.cancel()
                    localResumeJob = null
                    unfinishedRefreshKey++
                    screen = AppScreen.Home
                }

                LaunchedEffect(ownedMultiplayerRoute) {
                    val route = ownedMultiplayerRoute
                    if (route == null) {
                        if (hadOwnedMultiplayerRoute && screen is AppScreen.Game) {
                            hadOwnedMultiplayerRoute = false
                            backToHome()
                        }
                    } else {
                        hadOwnedMultiplayerRoute = true
                        val currentGameId = (screen as? AppScreen.Game)?.launch?.gameId
                        if (currentGameId != route.gameId) {
                            gameShellRouter.restoreOwned(route)?.let { launch ->
                                screen = AppScreen.Game(launch)
                            }
                        }
                    }
                }

                val backAction = appBackAction(screen)
                PlatformBackHandler(enabled = backAction != AppBackAction.AllowPlatformExit) {
                    when (backAction) {
                        AppBackAction.AllowPlatformExit -> Unit
                        AppBackAction.NavigateHome -> backToHome()
                        AppBackAction.DelegateToGame -> {
                            gameBackRequest = gameBackRequest.next()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ParlorTheme.colors.surfaceCanvas),
                ) {
                    Crossfade(
                        targetState = screen,
                        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
                        modifier = Modifier.fillMaxSize(),
                        label = "parlor-screen-transition",
                    ) { current ->
                        when (current) {
                            AppScreen.Home -> HomeScreen(
                                games = gameShellRegistry.all,
                                onGameSelected = { gameId ->
                                    localResumeGate.invalidate()
                                    localResumeJob?.cancel()
                                    localResumeJob = null
                                    gameShellRouter.newGame(gameId)?.let { launch ->
                                        screen = AppScreen.Game(launch)
                                    }
                                },
                                onSettings = {
                                    localResumeGate.invalidate()
                                    localResumeJob?.cancel()
                                    localResumeJob = null
                                    screen = AppScreen.Settings
                                },
                                modifier = Modifier.fillMaxSize(),
                                unfinishedSessions = unfinishedSessions,
                                onResume = { sessionId ->
                                    localResumeJob?.cancel()
                                    val requestGeneration = localResumeGate.begin()
                                    val request = appScope.launch(start = CoroutineStart.LAZY) {
                                        try {
                                            when (
                                                val destination = resolveLocalResumeDestination(
                                                    store = snapshotStore,
                                                    router = gameShellRouter,
                                                    sessionId = sessionId,
                                                )
                                            ) {
                                                is Result.Success -> if (
                                                    localResumeGate.isCurrent(requestGeneration) &&
                                                    screen == AppScreen.Home
                                                ) {
                                                    screen = AppScreen.Game(destination.data)
                                                }
                                                is Result.Failure -> if (
                                                    localResumeGate.isCurrent(requestGeneration) &&
                                                    screen == AppScreen.Home
                                                ) {
                                                    toastState.show(
                                                        text = resumeOpenFailedText,
                                                        severity = ParlorToastSeverity.Danger,
                                                    )
                                                }
                                            }
                                        } finally {
                                            if (localResumeJob === currentCoroutineContext()[Job]) {
                                                localResumeJob = null
                                            }
                                        }
                                    }
                                    localResumeJob = request
                                    request.start()
                                },
                                hasResumableMultiplayer = resumableMultiplayer != null,
                                onResumeMultiplayer = {
                                    localResumeGate.invalidate()
                                    localResumeJob?.cancel()
                                    localResumeJob = null
                                    val info = resumableMultiplayer
                                    val launch = info?.let {
                                        gameShellRouter.resumeMultiplayer(
                                            gameId = it.gameId,
                                            gameVersion = it.gameVersion,
                                            displayName = it.displayName,
                                        )
                                    }
                                    if (launch == null) {
                                        toastState.show(
                                            text = resumeOpenFailedText,
                                            severity = ParlorToastSeverity.Danger,
                                        )
                                    } else {
                                        screen = AppScreen.Game(launch)
                                    }
                                },
                            )

                            is AppScreen.Game -> {
                                val binding = gameShellRouter.bindingFor(current.launch)
                                if (binding == null) {
                                    LaunchedEffect(current) { backToHome() }
                                    Box(modifier = Modifier.fillMaxSize())
                                } else {
                                    binding.Content(
                                        launch = current.launch,
                                        onExit = backToHome,
                                        backRequest = gameBackRequest,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            AppScreen.Settings -> SettingsScreen(
                                onBack = backToHome,
                                mutationScope = appScope,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    ParlorToastHost(
                        state = toastState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
