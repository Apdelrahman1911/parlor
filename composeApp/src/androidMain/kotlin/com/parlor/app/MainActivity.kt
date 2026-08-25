package com.parlor.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.getKoin

/**
 * The single Activity. Compose owns navigation; the system bars are drawn
 * edge-to-edge so the cozy-noir backdrop runs from frame to frame.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Roles and private actions are visible in the app. Android 13 added a
        // dedicated Recents-thumbnail control, which preserves the player's
        // ability to capture an intentional screenshot. Older supported
        // releases need FLAG_SECURE for reliable Recents privacy; its broader
        // screenshot/screen-recording restriction is the safe fallback.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        // Install Compose synchronously. While settings initialize off the UI
        // dispatcher, the Activity owns an explicit first-frame surface.
        setContent {
            val settings by produceState<SettingsStore?>(initialValue = null) {
                value = loadSettingsForFirstComposition {
                    getKoin().get<SettingsStore>()
                }
            }
            val loadedSettings = settings
            if (loadedSettings == null) {
                ParlorTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ParlorTheme.colors.surfaceCanvas),
                    )
                }
            } else {
                App(loadedSettings)
            }
        }
    }
}

/** Keeps the first disk-backed preference load outside Android's UI dispatcher. */
@Suppress("RedundantSuspendModifier") // withContext is the owned dispatcher hop under test.
internal suspend fun loadSettingsForFirstComposition(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    load: () -> SettingsStore,
): SettingsStore = withContext(dispatcher) { load() }
