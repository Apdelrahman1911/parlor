package com.parlor.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.parlor.app.di.allModules
import org.koin.core.context.startKoin

/**
 * Desktop (JVM) entry. Single Compose window, cozy-noir from frame to frame.
 */
fun main() {
    startKoin { modules(allModules) }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Parlor",
        ) {
            App()
        }
    }
}
