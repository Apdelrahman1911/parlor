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
import com.parlor.app.shell.multiplayer.HostLobbyScreen
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.PeerLobbyScreen
import com.parlor.app.shell.settings.SettingsScreen
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.localization.customAppLocale
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.whodunit.ui.flow.WhodunitGameFlow
import com.parlor.networking.transport.RoomTransport
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform

/**
 * Application root. Reads language and appearance preferences from
 * [SettingsStore] and wraps content with [ProvideAppLanguage] (LTR/RTL + locale
 * propagation) and [ParlorTheme] (light/dark resolution).
 *
 * The Whodunit route now points at the real reducer-driven
 * [WhodunitGameFlow] (not the prior `WhodunitSetupDemo` placeholder).
 */
@Composable
fun App() {
    val settings: SettingsStore = koinInject()
    val languageTag by settings.languageOverride.collectAsState(initial = null)
    val themeModeTag by settings.themeMode.collectAsState(initial = ThemeMode.Default.tag)

    val language = AppLanguage.fromTag(languageTag)
    val themeMode = ThemeMode.fromTag(themeModeTag)

    LaunchedEffect(language) {
        customAppLocale = language.tag
    }

    val snapshotStore: SnapshotStore = koinInject()
    // RoomTransport is only registered when `parlor.p2p.enabled=true`. We pull
    // it through KoinPlatform.getKoin().getOrNull(...) so the default build
    // doesn't need to declare a Koin binding for it.
    val roomTransport: RoomTransport? = remember { KoinPlatform.getKoin().getOrNull() }

    ProvideAppLanguage(language = language) {
        ParlorTheme(themeMode = themeMode) {
            var screen: AppScreen by remember { mutableStateOf(AppScreen.Home) }
            var resumeSessionId: SessionId? by remember { mutableStateOf(null) }
            // Bumped whenever we return to Home, so we re-query the store and
            // pick up sessions that just started OR sessions just-deleted by
            // entering PostGame.
            var unfinishedRefreshKey: Int by remember { mutableStateOf(0) }
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

            when (screen) {
                AppScreen.Home -> HomeScreen(
                    onTileSelected = { gameId ->
                        if (gameId == "whodunit") {
                            resumeSessionId = null
                            screen = AppScreen.Whodunit
                        }
                    },
                    onSettings = { screen = AppScreen.Settings },
                    modifier = Modifier.fillMaxSize(),
                    unfinishedSessions = unfinishedSessions,
                    onResume = { sessionId ->
                        resumeSessionId = sessionId
                        screen = AppScreen.Whodunit
                    },
                    multiplayerEnabled = roomTransport != null,
                    onHost = { if (roomTransport != null) screen = AppScreen.HostLobby },
                    onJoin = { if (roomTransport != null) screen = AppScreen.JoinPrompt },
                )
                AppScreen.Whodunit -> WhodunitGameFlow(
                    onBackToLibrary = {
                        resumeSessionId = null
                        unfinishedRefreshKey++
                        screen = AppScreen.Home
                    },
                    modifier = Modifier.fillMaxSize(),
                    resumeSessionId = resumeSessionId,
                )
                AppScreen.Settings -> SettingsScreen(
                    onBack = { screen = AppScreen.Home },
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.HostLobby -> {
                    val transport = roomTransport
                    if (transport == null) {
                        screen = AppScreen.Home
                    } else {
                        HostLobbyScreen(
                            transport = transport,
                            onLeave = { screen = AppScreen.Home },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                AppScreen.JoinPrompt -> JoinPromptScreen(
                    onConfirm = { code ->
                        pendingJoinCode = code
                        screen = AppScreen.PeerLobby
                    },
                    onCancel = { screen = AppScreen.Home },
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.PeerLobby -> {
                    val transport = roomTransport
                    if (transport == null || pendingJoinCode.isBlank()) {
                        screen = AppScreen.Home
                    } else {
                        PeerLobbyScreen(
                            transport = transport,
                            code = pendingJoinCode,
                            displayName = "parlor-peer",
                            onLeave = {
                                pendingJoinCode = ""
                                screen = AppScreen.Home
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private enum class AppScreen { Home, Whodunit, Settings, HostLobby, JoinPrompt, PeerLobby }

/** Tiny shim used by Compose previews to keep the API surface stable. */
@Composable
internal fun PreviewLabel(text: String) = Text(text)
