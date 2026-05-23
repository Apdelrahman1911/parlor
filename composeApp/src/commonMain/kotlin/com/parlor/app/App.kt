package com.parlor.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.shell.WhodunitSetupDemo
import com.parlor.app.shell.home.HomeScreen
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Application root. Wraps Parlor's theme and routes between Home and the
 * (current Phase 4) Whodunit setup demo.
 *
 * Phase 5 replaces this hand-rolled router with Compose Navigation backed by
 * the `NavGraphRegistry`.
 */
@Composable
fun App() {
    ParlorTheme {
        var screen: AppScreen by remember { mutableStateOf(AppScreen.Home) }

        when (screen) {
            AppScreen.Home -> HomeScreen(
                onTileSelected = { gameId ->
                    if (gameId == "whodunit") screen = AppScreen.WhodunitSetup
                },
                modifier = Modifier.fillMaxSize(),
            )
            AppScreen.WhodunitSetup -> WhodunitSetupDemo(
                onSetupComplete = {
                    // Phase 5 transitions into the reveal/round flow.
                    screen = AppScreen.Home
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private enum class AppScreen { Home, WhodunitSetup }

/** Tiny shim used by Compose previews to keep the API surface stable. */
@Composable
internal fun PreviewLabel(text: String) = Text(text)
