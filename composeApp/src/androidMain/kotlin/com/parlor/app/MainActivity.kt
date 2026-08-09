package com.parlor.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

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
        setContent {
            App()
        }
    }
}
