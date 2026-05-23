package com.parlor.app

import androidx.compose.ui.window.ComposeUIViewController
import com.parlor.app.di.allModules
import org.koin.core.context.startKoin

/**
 * iOS Compose Multiplatform entry. The Xcode wrapper (iosApp project) calls
 * this from SwiftUI / AppDelegate to host the Compose root view.
 *
 * The Koin start guard prevents a re-start if the iOS lifecycle invokes us
 * twice (e.g., on hot-reload).
 */
@Suppress("FunctionName", "Unused")
fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoinOnce()
    },
) {
    App()
}

@Volatile
private var koinStarted: Boolean = false

private fun startKoinOnce() {
    if (koinStarted) return
    startKoin { modules(allModules) }
    koinStarted = true
}
