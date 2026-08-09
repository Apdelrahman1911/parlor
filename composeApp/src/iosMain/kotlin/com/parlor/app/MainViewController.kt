package com.parlor.app

import androidx.compose.ui.window.ComposeUIViewController
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.app.di.allModules
import kotlin.concurrent.Volatile
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

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

/** SwiftUI scenePhase bridge; lifecycle policy remains common Kotlin code. */
@Suppress("FunctionName", "Unused")
fun NotifyAppBackgrounded() {
    startKoinOnce()
    lifecycleCoordinator().notifyBackgrounded()
}

@Suppress("FunctionName", "Unused")
fun NotifyAppForegrounded() {
    startKoinOnce()
    lifecycleCoordinator().notifyActive()
}

/** Covers private UI without suspending the still-foreground LAN session. */
@Suppress("FunctionName", "Unused")
fun NotifyAppInactive() {
    startKoinOnce()
    lifecycleCoordinator().notifyInactive()
}

private fun lifecycleCoordinator(): AppLifecycleCoordinator =
    KoinPlatform.getKoin().get()
