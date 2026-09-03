package com.parlor.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.parlor.app.resources.local_resume_failure_discard_failed
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GameShellBackRequest
import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.app.shell.home.HomeScreen
import com.parlor.app.shell.home.HomeRecoveryAvailability
import com.parlor.app.shell.home.LocalResumeFailureScreen
import com.parlor.app.shell.home.loadHomeRecoveryAvailability
import com.parlor.app.shell.settings.SettingsScreen
import com.parlor.core.ids.SessionId
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastHost
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.motion.rememberSystemReducedMotion
import com.parlor.designsystem.motion.shouldReduceMotion
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.routeOrNull
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Parlor's application shell. It owns only global screens and resolves games
 * through [GameShellRegistry]; setup, local play, hosting, joining and resume
 * sub-navigation belong to each registered [com.parlor.app.shell.game.GameShellBinding].
 */
@Composable
fun App() = App(koinInject<SettingsStore>())

@Suppress("LongMethod") // Declarative root composition; stateful effects are separately tested.
@Composable
internal fun App(settings: SettingsStore) {
    val languageTag by settings.languageOverride.collectAsState(initial = null)
    val themeModeTag by settings.themeMode.collectAsState(initial = ThemeMode.Default.tag)
    val reducedMotion by settings.reducedMotion.collectAsState(initial = false)
    val language = languageTag?.let(AppLanguage::fromTag)
    val themeMode = ThemeMode.fromTag(themeModeTag)
    val systemReducedMotion = rememberSystemReducedMotion()
    val effectiveReducedMotion = shouldReduceMotion(
        appPreference = reducedMotion,
        systemPreference = systemReducedMotion,
    )

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
            reducedMotion = effectiveReducedMotion,
        ) {
            CompositionLocalProvider(LocalParlorToastState provides toastState) {
                val resumeOpenFailedText = stringResource(Res.string.home_resume_open_failed)
                val resumeDiscardFailedText = stringResource(
                    Res.string.local_resume_failure_discard_failed,
                )
                val initialLaunch = remember(ownedMultiplayerRoute, gameShellRouter) {
                    ownedMultiplayerRoute?.let(gameShellRouter::restoreOwned)
                }
                val navigator = remember { AppNavigator(initialLaunch) }
                val currentRoute = navigator.currentRoute
                val localResumeCoordinator = remember(
                    appScope,
                    snapshotStore,
                    gameShellRouter,
                ) {
                    LocalResumeCoordinator(
                        scope = appScope,
                        store = snapshotStore,
                        router = gameShellRouter,
                    )
                }
                val localResumeBusy by localResumeCoordinator.busy.collectAsState()
                var unfinishedRefreshKey by remember { mutableStateOf(0) }
                var hadOwnedMultiplayerRoute by remember {
                    mutableStateOf(ownedMultiplayerRoute != null)
                }
                val activeGameRoute = currentRoute as? AppRoute.Game
                var gameBackRequest by remember(activeGameRoute) {
                    mutableStateOf(GameShellBackRequest.Initial)
                }

                val homeRecovery by produceState<HomeRecoveryAvailability>(
                    initialValue = HomeRecoveryAvailability.Loading,
                    key1 = currentRoute,
                    key2 = unfinishedRefreshKey,
                ) {
                    if (currentRoute != AppRoute.Home) return@produceState
                    value = HomeRecoveryAvailability.Loading
                    value = loadHomeRecoveryAvailability(
                        store = snapshotStore,
                        loadMultiplayer = roomTransport::resumableSession,
                        supportsLocalResume = { entry ->
                            entry.gameId?.let { gameId ->
                                gameShellRouter.resumeLocal(gameId, entry.sessionId)
                            } != null
                        },
                        supportsMultiplayerResume = { info ->
                            gameShellRouter.resumeMultiplayer(
                                gameId = info.gameId,
                                gameVersion = info.gameVersion,
                                displayName = info.displayName,
                            ) != null
                        },
                    )
                }

                val backToHome: (AppRoute) -> Unit = { expectedRoute ->
                    if (
                        navigator.navigateHome(expectedRoute) !=
                        AppNavigationMutation.RejectedStaleRoute
                    ) {
                        localResumeCoordinator.invalidate()
                        unfinishedRefreshKey++
                    }
                }

                val requestLocalResume: (SessionId) -> Unit = { sessionId ->
                    localResumeCoordinator.request(
                        sessionId = sessionId,
                        currentRoute = { navigator.currentRoute },
                        navigate = { destination ->
                            when (destination) {
                                is LocalResumeDestination.Game -> {
                                    navigator.openGame(destination.launch)
                                }
                                is LocalResumeDestination.Recovery -> {
                                    navigator.showLocalResumeFailure(destination.sessionId)
                                }
                            }
                        },
                    )
                }

                LaunchedEffect(ownedMultiplayerRoute) {
                    val route = ownedMultiplayerRoute
                    if (route == null) {
                        if (hadOwnedMultiplayerRoute && navigator.currentRoute is AppRoute.Game) {
                            hadOwnedMultiplayerRoute = false
                            backToHome(navigator.currentRoute)
                        }
                    } else {
                        hadOwnedMultiplayerRoute = true
                        val currentGameId = (navigator.currentRoute as? AppRoute.Game)?.gameId
                        if (currentGameId != route.gameId) {
                            gameShellRouter.restoreOwned(route)?.let { launch ->
                                navigator.openGame(launch)
                            }
                        }
                    }
                }

                val backAction = appBackAction(currentRoute)
                val interceptPlatformBack = backAction == AppBackAction.NavigateGames ||
                    backAction == AppBackAction.DelegateToGame
                PlatformBackHandler(enabled = interceptPlatformBack) {
                    when (backAction) {
                        AppBackAction.AllowPlatformExit -> Unit
                        AppBackAction.NavigateGames -> {
                            localResumeCoordinator.invalidate()
                            navigator.navigateBack(currentRoute)
                        }
                        AppBackAction.DelegateToGame -> {
                            gameBackRequest = gameBackRequest.next()
                        }
                        AppBackAction.HandledByNavDisplay -> Unit
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ParlorTheme.colors.surfaceCanvas),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        AppNavDisplay(
                            navigator = navigator,
                            homeContent = {
                                HomeScreen(
                                    games = gameShellRegistry.all,
                                    onGameSelected = { gameId ->
                                        localResumeCoordinator.invalidate()
                                        gameShellRouter.newGame(gameId)?.let { launch ->
                                            navigator.openGame(launch)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    unfinishedSessions =
                                        (homeRecovery as? HomeRecoveryAvailability.Ready)
                                            ?.unfinishedSessions.orEmpty(),
                                    onResume = requestLocalResume,
                                    hasResumableMultiplayer =
                                        (homeRecovery as? HomeRecoveryAvailability.Ready)
                                            ?.resumableMultiplayer != null,
                                    recoveryLoading =
                                        homeRecovery is HomeRecoveryAvailability.Loading,
                                    recoveryUnavailable =
                                        (homeRecovery as? HomeRecoveryAvailability.Ready)
                                            ?.hasUnavailableSource == true,
                                    onRetryRecovery = { unfinishedRefreshKey++ },
                                    onResumeMultiplayer = {
                                        localResumeCoordinator.invalidate()
                                        val info =
                                            (homeRecovery as? HomeRecoveryAvailability.Ready)
                                                ?.resumableMultiplayer
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
                                            navigator.openGame(launch)
                                        }
                                    },
                                )
                            },
                            recoveryContent = { route ->
                                LocalResumeFailureScreen(
                                    actionsEnabled = !localResumeBusy,
                                    onRetry = { requestLocalResume(route.sessionId) },
                                    onDiscard = {
                                        localResumeCoordinator.discard(
                                            sessionId = route.sessionId,
                                            currentRoute = { navigator.currentRoute },
                                            onDiscarded = {
                                                if (
                                                    navigator.navigateHome(route) ==
                                                    AppNavigationMutation.Applied
                                                ) {
                                                    unfinishedRefreshKey++
                                                }
                                            },
                                            onFailure = {
                                                toastState.show(
                                                    text = resumeDiscardFailedText,
                                                    severity = ParlorToastSeverity.Danger,
                                                )
                                            },
                                        )
                                    },
                                    onBack = { backToHome(route) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            gameContent = { route ->
                                val launch = navigator.launchFor(route)
                                val binding = launch?.let(gameShellRouter::bindingFor)
                                if (launch == null || binding == null) {
                                    LaunchedEffect(route) { backToHome(route) }
                                    Box(modifier = Modifier.fillMaxSize())
                                } else {
                                    binding.Content(
                                        launch = launch,
                                        onExit = { backToHome(route) },
                                        backRequest = gameBackRequest,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            },
                            settingsContent = {
                                SettingsScreen(
                                    mutationScope = appScope,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            onBack = { route ->
                                localResumeCoordinator.invalidate()
                                navigator.navigateBack(route)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        ParlorToastHost(
                            state = toastState,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    if (currentRoute.isTopLevel) {
                        AppNavigationBar(
                            selectedDestination = navigator.selectedDestination,
                            onDestinationSelected = { destination ->
                                localResumeCoordinator.invalidate()
                                navigator.selectTopLevel(destination)
                            },
                        )
                    }
                }
            }
        }
    }
}
