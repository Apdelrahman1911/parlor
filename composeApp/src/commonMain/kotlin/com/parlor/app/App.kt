package com.parlor.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.shell.home.HomeScreen
import com.parlor.app.shell.library.CasePickerScreen
import com.parlor.app.shell.multiplayer.HostSessionFlow
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.app.shell.multiplayer.PeerSessionFlow
import com.parlor.app.shell.settings.SettingsScreen
import com.parlor.content.repository.CaseRepository
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.localization.customAppLocale
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.ui.flow.WhodunitGameFlow
import com.parlor.games.whodunit.ui.screens.setup.ModeSelectionScreen
import com.parlor.networking.transport.RoomTransport
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform

/**
 * Parlor's root composable. Holds the high-level navigation state machine
 * — Home → (one of: pass-and-play game, host flow, peer flow, settings)
 * — and threads the user's selections (name, case id, mode) through each
 * branch.
 *
 * The pass-and-play branch and the multiplayer branches share the case
 * picker; selecting a case from Home leads into a solo game with the same
 * UX as before, while the Host branch routes the picker through a mode
 * picker into the lobby. Picking a case is dynamic: any JSON registered in
 * `WhodunitDiModule.knownCaseIds` shows up automatically.
 */
@Composable
fun App() {
    val settings: SettingsStore = koinInject()
    val languageTag by settings.languageOverride.collectAsState(initial = null)
    val themeModeTag by settings.themeMode.collectAsState(initial = ThemeMode.Default.tag)
    val language = AppLanguage.fromTag(languageTag)
    val themeMode = ThemeMode.fromTag(themeModeTag)
    LaunchedEffect(language) { customAppLocale = language.tag }

    val snapshotStore: SnapshotStore = koinInject()
    val caseRepository: CaseRepository = koinInject()
    // RoomTransport is only registered when `parlor.p2p.enabled=true`. Pulled
    // through KoinPlatform.getKoin().getOrNull(...) so the default build
    // doesn't need to declare a Koin binding for it.
    val roomTransport: RoomTransport? = remember { KoinPlatform.getKoin().getOrNull() }

    ProvideAppLanguage(language = language) {
        ParlorTheme(themeMode = themeMode) {
            var screen: AppScreen by remember { mutableStateOf(AppScreen.Home) }
            var resumeSessionId: SessionId? by remember { mutableStateOf(null) }
            var unfinishedRefreshKey: Int by remember { mutableStateOf(0) }

            // Pass-and-play case selection.
            var soloCaseId: String by remember { mutableStateOf("last-dinner") }

            // Host flow selections (carried across screens).
            var hostName: String by remember { mutableStateOf("") }
            var hostCaseId: String by remember { mutableStateOf("") }
            var hostModeId: ModeId by remember { mutableStateOf(WhodunitIds.ClassicVoteModeId) }

            // Peer flow selections.
            var peerName: String by remember { mutableStateOf("") }
            var pendingJoinCode: String by remember { mutableStateOf("") }

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

            val backToHome: () -> Unit = {
                resumeSessionId = null
                hostName = ""
                peerName = ""
                hostCaseId = ""
                pendingJoinCode = ""
                unfinishedRefreshKey++
                screen = AppScreen.Home
            }

            when (screen) {
                AppScreen.Home -> HomeScreen(
                    onTileSelected = { /* legacy tile id; replaced by case picker */ },
                    onSettings = { screen = AppScreen.Settings },
                    modifier = Modifier.fillMaxSize(),
                    unfinishedSessions = unfinishedSessions,
                    onResume = { sessionId ->
                        resumeSessionId = sessionId
                        screen = AppScreen.Whodunit
                    },
                    multiplayerEnabled = roomTransport != null,
                    onHost = {
                        if (roomTransport != null) screen = AppScreen.HostName
                    },
                    onJoin = {
                        if (roomTransport != null) screen = AppScreen.JoinName
                    },
                    onBrowseCases = { screen = AppScreen.SoloCasePicker },
                )

                // -------- Pass-and-play branch --------
                AppScreen.SoloCasePicker -> CasePickerScreen(
                    repository = caseRepository,
                    onCasePicked = { summary ->
                        soloCaseId = summary.caseId
                        resumeSessionId = null
                        screen = AppScreen.Whodunit
                    },
                    onBack = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.Whodunit -> WhodunitGameFlow(
                    onBackToLibrary = backToHome,
                    modifier = Modifier.fillMaxSize(),
                    resumeSessionId = resumeSessionId,
                    caseId = soloCaseId,
                )

                // -------- Host branch --------
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
                    val transport = roomTransport
                    if (transport == null || hostCaseId.isBlank() || hostName.isBlank()) {
                        screen = AppScreen.Home
                    } else {
                        HostSessionFlow(
                            transport = transport,
                            caseId = hostCaseId,
                            modeId = hostModeId,
                            hostName = hostName,
                            onBackToLibrary = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // -------- Peer branch --------
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
                    val transport = roomTransport
                    if (transport == null || pendingJoinCode.isBlank() || peerName.isBlank()) {
                        screen = AppScreen.Home
                    } else {
                        PeerSessionFlow(
                            transport = transport,
                            code = pendingJoinCode,
                            peerName = peerName,
                            onBackToLibrary = backToHome,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                AppScreen.Settings -> SettingsScreen(
                    onBack = backToHome,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private enum class AppScreen {
    Home,
    SoloCasePicker, Whodunit,
    HostName, HostCasePicker, HostMode, HostLobby,
    JoinName, JoinPrompt, PeerLobby,
    Settings,
}

/** Tiny shim used by Compose previews to keep the API surface stable. */
@Composable
internal fun PreviewLabel(text: String) = Text(text)
