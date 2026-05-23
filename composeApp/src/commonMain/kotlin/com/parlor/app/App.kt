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
import com.parlor.app.shell.settings.SettingsScreen
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.localization.customAppLocale
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.whodunit.ui.flow.WhodunitGameFlow
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotStore
import org.koin.compose.koinInject

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

    ProvideAppLanguage(language = language) {
        ParlorTheme(themeMode = themeMode) {
            var screen: AppScreen by remember { mutableStateOf(AppScreen.Home) }
            var resumeSessionId: SessionId? by remember { mutableStateOf(null) }
            // Bumped whenever we return to Home, so we re-query the store and
            // pick up sessions that just started OR sessions just-deleted by
            // entering PostGame.
            var unfinishedRefreshKey: Int by remember { mutableStateOf(0) }

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
            }
        }
    }
}

private enum class AppScreen { Home, Whodunit, Settings }

/** Tiny shim used by Compose previews to keep the API surface stable. */
@Composable
internal fun PreviewLabel(text: String) = Text(text)
