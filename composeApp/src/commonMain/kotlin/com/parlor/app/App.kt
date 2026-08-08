package com.parlor.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.parlor.app.permissions.P2pPermissionRationaleScreen
import com.parlor.app.permissions.PermissionStatus
import com.parlor.app.permissions.rememberP2pPermissionGate
import com.parlor.app.shell.home.HomeScreen
import com.parlor.app.shell.library.CasePickerScreen
import com.parlor.app.shell.multiplayer.HostSessionFlow
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.app.shell.multiplayer.PeerSessionFlow
import com.parlor.app.shell.playmode.PlayModePickerScreen
import com.parlor.app.shell.settings.SettingsScreen
import com.parlor.content.repository.CaseRepository
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastHost
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.localization.customAppLocale
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.mafia.ui.flow.multidevice.MafiaHostLobbyFlow
import com.parlor.games.mafia.ui.flow.multidevice.MafiaPeerLobbyFlow
import com.parlor.games.mafia.ui.flow.passandplay.MafiaGameFlow
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.ui.flow.WhodunitGameFlow
import com.parlor.games.whodunit.ui.screens.setup.ModeSelectionScreen
import com.parlor.app.shell.home.MAFIA_GAME_ID
import com.parlor.app.shell.home.WHODUNIT_GAME_ID
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.PlayMode
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import org.koin.compose.koinInject

/**
 * Parlor's root composable. Owns the high-level navigation state machine.
 *
 * Top-level shape: **Home → Setup → branch**.
 *
 *  - Home is game-first: pick a game card.
 *  - Setup is one screen with four cards (Solo / Pass-and-Play / Host / Join);
 *    the user makes the "how" decision once, up front.
 *  - From Setup, single-device branches go to the case picker then the game;
 *    multi-device branches go through the existing permission + name + case
 *    + mode pipeline. The production app always supplies RoomTransport.
 *
 * Case discovery is dynamic — any JSON registered in
 * `WhodunitDiModule.knownCaseIds` shows up in the picker automatically.
 */
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
    val caseRepository: CaseRepository = koinInject()
    val roomTransport: RoomTransport = koinInject()

    val toastState = remember { ParlorToastState() }

    ProvideAppLanguage(language = language) {
        ParlorTheme(
            themeMode = themeMode,
            reducedMotion = reducedMotion,
        ) {
            CompositionLocalProvider(LocalParlorToastState provides toastState) {
            var screen: AppScreen by remember { mutableStateOf(AppScreen.Home) }
            var resumeSessionId: SessionId? by remember { mutableStateOf(null) }
            var unfinishedRefreshKey: Int by remember { mutableStateOf(0) }

            // Single-device entry (Library tab) — case + chosen play mode.
            var soloCaseId: String by remember { mutableStateOf("last-dinner") }
            // Chosen on the play-mode picker between the case picker and the
            // game. Always one of the two local modes (multi-device is wired
            // separately through HostSessionFlow / PeerSessionFlow).
            var soloPlayMode: PlayMode by remember { mutableStateOf(PlayMode.PassAndPlay) }

            // Host flow selections (carried across screens).
            var hostName: String by remember { mutableStateOf("") }
            var hostCaseId: String by remember { mutableStateOf("") }
            var hostModeId: ModeId by remember { mutableStateOf(WhodunitIds.ClassicVoteModeId) }

            // Peer flow selections.
            var peerName: String by remember { mutableStateOf("") }
            var pendingJoinCode: String by remember { mutableStateOf("") }

            // Mafia multi-device selections — parallel to host/peer above so
            // a single back navigation can clear all of them.
            var mafiaHostName: String by remember { mutableStateOf("") }
            var mafiaPeerName: String by remember { mutableStateOf("") }
            var mafiaPendingJoinCode: String by remember { mutableStateOf("") }

            val unfinishedSessions by produceState(
                initialValue = emptyList<SessionId>(),
                key1 = screen,
                key2 = unfinishedRefreshKey,
            ) {
                value = if (screen == AppScreen.Home) {
                    when (val r = snapshotStore.listUnfinished()) {
                        is Result.Success -> r.data
                        is Result.Failure -> emptyList()
                    }
                } else {
                    value
                }
            }

            val resumableMultiplayer by produceState<com.parlor.networking.transport.ResumableSessionInfo?>(
                initialValue = null,
                key1 = screen,
                key2 = unfinishedRefreshKey,
            ) {
                value = if (screen == AppScreen.Home) {
                    when (val result = roomTransport.resumableSession()) {
                        is Result.Success -> result.data
                        is Result.Failure -> null
                    }
                } else {
                    value
                }
            }

            val backToHome: () -> Unit = {
                resumeSessionId = null
                hostName = ""
                peerName = ""
                hostCaseId = ""
                pendingJoinCode = ""
                mafiaHostName = ""
                mafiaPeerName = ""
                mafiaPendingJoinCode = ""
                unfinishedRefreshKey++
                screen = AppScreen.Home
            }

            // Screen transition: pure Crossfade with surfaceCanvas behind it.
            // The previous slide+fade flashed because the AnimatedContent box
            // was transparent during the transition, exposing the system's
            // default background mid-transition. Crossfade renders both states
            // simultaneously, fading between them — no slide reveals empty
            // canvas, no flash, no flicker.
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
                    onGameSelected = { gameId ->
                        screen = when (gameId) {
                            MAFIA_GAME_ID -> AppScreen.MafiaSetup
                            WHODUNIT_GAME_ID -> AppScreen.GameSetup
                            else -> AppScreen.GameSetup
                        }
                    },
                    onSettings = { screen = AppScreen.Settings },
                    modifier = Modifier.fillMaxSize(),
                    unfinishedSessions = unfinishedSessions,
                    onResume = { sessionId ->
                        resumeSessionId = sessionId
                        screen = AppScreen.Whodunit
                    },
                    hasResumableMultiplayer = resumableMultiplayer != null,
                    onResumeMultiplayer = {
                        screen = AppScreen.MultiplayerResumePermission
                    },
                )

                // -------- Setup (the "how do you want to play?" gate) --------
                // Branches into Solo/Pass-and-Play (case picker → game) or
                // Host/Join (permission → name → case → mode → lobby).
                AppScreen.GameSetup -> PlayModePickerScreen(
                    onModeSelected = { mode ->
                        soloPlayMode = mode
                        screen = AppScreen.SoloCasePicker
                    },
                    onHost = { screen = AppScreen.HostPermission },
                    onJoin = { screen = AppScreen.JoinPermission },
                    onBack = backToHome,
                    multiplayerEnabled = true,
                    modifier = Modifier.fillMaxSize(),
                )

                // -------- Single-device branch (Solo / PassAndPlay) --------
                AppScreen.SoloCasePicker -> CasePickerScreen(
                    repository = caseRepository,
                    onCasePicked = { summary ->
                        soloCaseId = summary.caseId
                        resumeSessionId = null
                        screen = AppScreen.Whodunit
                    },
                    onBack = { screen = AppScreen.GameSetup },
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.Whodunit -> WhodunitGameFlow(
                    onBackToLibrary = backToHome,
                    modifier = Modifier.fillMaxSize(),
                    resumeSessionId = resumeSessionId,
                    caseId = soloCaseId,
                    // The user explicitly picked Solo or PassAndPlay on the
                    // play-mode picker. Multi-device entries are wired
                    // through HostSessionFlow / PeerSessionFlow, which use
                    // their own composables that declare MultiDevice mode
                    // internally — they never land here.
                    playMode = soloPlayMode,
                )

                // -------- Host branch --------
                AppScreen.HostPermission -> {
                    val gate = rememberP2pPermissionGate()
                    val gateStatus by gate.status.collectAsState()
                    LaunchedEffect(gateStatus) {
                        if (gateStatus == PermissionStatus.Granted) {
                            screen = AppScreen.HostName
                        }
                    }
                    if (gateStatus == PermissionStatus.Granted) {
                        // Brief flicker handled by the LaunchedEffect.
                        Text(text = "")
                    } else {
                        P2pPermissionRationaleScreen(
                            gate = gate,
                            onGranted = { screen = AppScreen.HostName },
                            onBack = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                AppScreen.HostName -> NameInputScreen(
                    isHost = true,
                    initial = hostName,
                    onConfirm = { name ->
                        hostName = name
                        screen = AppScreen.HostCasePicker
                    },
                    onBack = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.HostCasePicker -> CasePickerScreen(
                    repository = caseRepository,
                    onCasePicked = { summary ->
                        hostCaseId = summary.caseId
                        screen = AppScreen.HostMode
                    },
                    onBack = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.HostMode -> ModeSelectionScreen(
                    onModeSelected = { mode ->
                        hostModeId = mode
                        screen = AppScreen.HostLobby
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.HostLobby -> {
                    if (hostCaseId.isBlank() || hostName.isBlank()) {
                        screen = AppScreen.Home
                    } else {
                        HostSessionFlow(
                            transport = roomTransport,
                            caseId = hostCaseId,
                            modeId = hostModeId,
                            hostName = hostName,
                            onBackToLibrary = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // -------- Peer branch --------
                AppScreen.JoinPermission -> {
                    val gate = rememberP2pPermissionGate()
                    val gateStatus by gate.status.collectAsState()
                    LaunchedEffect(gateStatus) {
                        if (gateStatus == PermissionStatus.Granted) {
                            screen = AppScreen.JoinName
                        }
                    }
                    if (gateStatus == PermissionStatus.Granted) {
                        Text(text = "")
                    } else {
                        P2pPermissionRationaleScreen(
                            gate = gate,
                            onGranted = { screen = AppScreen.JoinName },
                            onBack = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                AppScreen.JoinName -> NameInputScreen(
                    isHost = false,
                    initial = peerName,
                    onConfirm = { name ->
                        peerName = name
                        screen = AppScreen.JoinPrompt
                    },
                    onBack = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.JoinPrompt -> JoinPromptScreen(
                    onConfirm = { code ->
                        pendingJoinCode = code
                        screen = AppScreen.PeerLobby
                    },
                    onCancel = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.PeerLobby -> {
                    if (pendingJoinCode.isBlank() || peerName.isBlank()) {
                        screen = AppScreen.Home
                    } else {
                        PeerSessionFlow(
                            transport = roomTransport,
                            code = pendingJoinCode,
                            peerName = peerName,
                            onBackToLibrary = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // -------- Mafia branch --------
                // Mafia has no external case content, so it skips the case
                // picker. The play-mode picker disables Solo (Mafia is a
                // group game) and routes Pass-and-Play to MafiaGameFlow,
                // Host/Join into the Mafia-owned multi-device lobby flows.
                AppScreen.MafiaSetup -> PlayModePickerScreen(
                    onModeSelected = { _ ->
                        // The picker disables Solo for Mafia; any selection
                        // here is Pass-and-Play, which is what MafiaGameFlow
                        // expects.
                        screen = AppScreen.Mafia
                    },
                    onHost = { screen = AppScreen.MafiaHostPermission },
                    onJoin = { screen = AppScreen.MafiaJoinPermission },
                    onBack = backToHome,
                    multiplayerEnabled = true,
                    soloEnabled = false,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.Mafia -> MafiaGameFlow(
                    onBackToHome = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )

                // Mafia host branch — permission gate → name → lobby.
                AppScreen.MafiaHostPermission -> {
                    val gate = rememberP2pPermissionGate()
                    val gateStatus by gate.status.collectAsState()
                    LaunchedEffect(gateStatus) {
                        if (gateStatus == PermissionStatus.Granted) {
                            screen = AppScreen.MafiaHostName
                        }
                    }
                    if (gateStatus == PermissionStatus.Granted) {
                        Text(text = "")
                    } else {
                        P2pPermissionRationaleScreen(
                            gate = gate,
                            onGranted = { screen = AppScreen.MafiaHostName },
                            onBack = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                AppScreen.MafiaHostName -> NameInputScreen(
                    isHost = true,
                    initial = mafiaHostName,
                    onConfirm = { name ->
                        mafiaHostName = name
                        screen = AppScreen.MafiaHostLobby
                    },
                    onBack = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.MafiaHostLobby -> {
                    if (mafiaHostName.isBlank()) {
                        screen = AppScreen.Home
                    } else {
                        MafiaHostLobbyFlow(
                            transport = roomTransport,
                            hostName = mafiaHostName,
                            onBackToHome = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Mafia join branch — permission gate → name → code → lobby.
                AppScreen.MafiaJoinPermission -> {
                    val gate = rememberP2pPermissionGate()
                    val gateStatus by gate.status.collectAsState()
                    LaunchedEffect(gateStatus) {
                        if (gateStatus == PermissionStatus.Granted) {
                            screen = AppScreen.MafiaJoinName
                        }
                    }
                    if (gateStatus == PermissionStatus.Granted) {
                        Text(text = "")
                    } else {
                        P2pPermissionRationaleScreen(
                            gate = gate,
                            onGranted = { screen = AppScreen.MafiaJoinName },
                            onBack = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                AppScreen.MafiaJoinName -> NameInputScreen(
                    isHost = false,
                    initial = mafiaPeerName,
                    onConfirm = { name ->
                        mafiaPeerName = name
                        screen = AppScreen.MafiaJoinPrompt
                    },
                    onBack = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.MafiaJoinPrompt -> JoinPromptScreen(
                    onConfirm = { code ->
                        mafiaPendingJoinCode = code
                        screen = AppScreen.MafiaPeerLobby
                    },
                    onCancel = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.MafiaPeerLobby -> {
                    if (mafiaPendingJoinCode.isBlank() || mafiaPeerName.isBlank()) {
                        screen = AppScreen.Home
                    } else {
                        MafiaPeerLobbyFlow(
                            transport = roomTransport,
                            code = mafiaPendingJoinCode,
                            peerName = mafiaPeerName,
                            onBackToHome = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                AppScreen.MultiplayerResumePermission -> {
                    val gate = rememberP2pPermissionGate()
                    val gateStatus by gate.status.collectAsState()
                    val resumeTarget = when (resumableMultiplayer?.gameId?.raw) {
                        WHODUNIT_GAME_ID -> AppScreen.ResumeWhodunitPeer
                        MAFIA_GAME_ID -> AppScreen.ResumeMafiaPeer
                        else -> AppScreen.Home
                    }
                    LaunchedEffect(gateStatus, resumeTarget) {
                        if (gateStatus == PermissionStatus.Granted) screen = resumeTarget
                    }
                    if (gateStatus == PermissionStatus.Granted) {
                        Text(text = "")
                    } else {
                        P2pPermissionRationaleScreen(
                            gate = gate,
                            onGranted = { screen = resumeTarget },
                            onBack = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                AppScreen.ResumeWhodunitPeer -> PeerSessionFlow(
                    transport = roomTransport,
                    code = "",
                    peerName = "",
                    resumeExistingSession = true,
                    onBackToLibrary = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.ResumeMafiaPeer -> MafiaPeerLobbyFlow(
                    transport = roomTransport,
                    code = "",
                    peerName = "",
                    resumeExistingSession = true,
                    onBackToHome = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )

                AppScreen.Settings -> SettingsScreen(
                    onBack = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
            }
                }
                // Toast host overlays every screen so any descendant can
                // show("...") via LocalParlorToastState.current.
                ParlorToastHost(
                    state = toastState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            }
        }
    }
}

private enum class AppScreen {
    Home,
    GameSetup,
    SoloCasePicker, Whodunit,
    MafiaSetup, Mafia,
    MafiaHostPermission, MafiaHostName, MafiaHostLobby,
    MafiaJoinPermission, MafiaJoinName, MafiaJoinPrompt, MafiaPeerLobby,
    HostPermission, HostName, HostCasePicker, HostMode, HostLobby,
    JoinPermission, JoinName, JoinPrompt, PeerLobby,
    MultiplayerResumePermission, ResumeWhodunitPeer, ResumeMafiaPeer,
    Settings,
}

/** Tiny shim used by Compose previews to keep the API surface stable. */
@Composable
internal fun PreviewLabel(text: String) = Text(text)
