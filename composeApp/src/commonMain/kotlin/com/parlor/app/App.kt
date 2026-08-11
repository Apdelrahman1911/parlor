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
import com.parlor.app.resources.local_resume_failure_discard_failed
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GameShellBackRequest
import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.app.shell.home.HomeScreen
import com.parlor.app.shell.home.HomeRecoveryAvailability
import com.parlor.app.shell.home.LocalResumeFailureScreen
import com.parlor.app.shell.home.readLocalRecoveryInventory
import com.parlor.app.shell.home.resolveHomeRecoveryAvailability
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
                var screen: AppScreen by remember {
                    mutableStateOf(
                        initialLaunch?.let(AppScreen::Game) ?: AppScreen.Home,
                    )
                }
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
                val activeGameLaunch = (screen as? AppScreen.Game)?.launch
                var gameBackRequest by remember(activeGameLaunch) {
                    mutableStateOf(GameShellBackRequest.Initial)
                }

                val homeRecovery by produceState<HomeRecoveryAvailability>(
                    initialValue = HomeRecoveryAvailability.Loading,
                    key1 = screen,
                    key2 = unfinishedRefreshKey,
                ) {
                    if (screen != AppScreen.Home) return@produceState
                    value = HomeRecoveryAvailability.Loading
                    val localResult = readLocalRecoveryInventory(snapshotStore)
                    val multiplayerResult = roomTransport.resumableSession()
                    value = resolveHomeRecoveryAvailability(
                        localResult = localResult,
                        multiplayerResult = multiplayerResult,
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

                val backToHome: () -> Unit = {
                    localResumeCoordinator.invalidate()
                    unfinishedRefreshKey++
                    screen = AppScreen.Home
                }

                val requestLocalResume: (SessionId) -> Unit = { sessionId ->
                    localResumeCoordinator.request(
                        sessionId = sessionId,
                        currentScreen = { screen },
                        navigate = { destination -> screen = destination },
                    )
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
                        animationSpec = tween(
                            durationMillis = ParlorTheme.motion.durationFast,
                            easing = ParlorTheme.motion.easingStandard,
                        ),
                        modifier = Modifier.fillMaxSize(),
                        label = "parlor-screen-transition",
                    ) { current ->
                        when (current) {
                            AppScreen.Home -> HomeScreen(
                                games = gameShellRegistry.all,
                                onGameSelected = { gameId ->
                                    localResumeCoordinator.invalidate()
                                    gameShellRouter.newGame(gameId)?.let { launch ->
                                        screen = AppScreen.Game(launch)
                                    }
                                },
                                onSettings = {
                                    localResumeCoordinator.invalidate()
                                    screen = AppScreen.Settings
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
                                        screen = AppScreen.Game(launch)
                                    }
                                },
                            )

                            is AppScreen.LocalResumeFailure -> LocalResumeFailureScreen(
                                actionsEnabled = !localResumeBusy,
                                onRetry = { requestLocalResume(current.sessionId) },
                                onDiscard = {
                                    localResumeCoordinator.discard(
                                        sessionId = current.sessionId,
                                        currentScreen = { screen },
                                        onDiscarded = {
                                            unfinishedRefreshKey++
                                            screen = AppScreen.Home
                                        },
                                        onFailure = {
                                            toastState.show(
                                                text = resumeDiscardFailedText,
                                                severity = ParlorToastSeverity.Danger,
                                            )
                                        },
                                    )
                                },
                                onBack = backToHome,
                                modifier = Modifier.fillMaxSize(),
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
