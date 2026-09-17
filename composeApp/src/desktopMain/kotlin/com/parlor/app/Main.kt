package com.parlor.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.DisposableEffect
import com.parlor.app.di.allModules
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.core.result.Result
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.NetError
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.routeOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * Desktop (JVM) entry. Single Compose window, cozy-noir from frame to frame.
 */
fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("--verify-distribution"))) {
        verifyDesktopDistribution()
        return
    }
    val koinApplication = startKoin { modules(allModules) }
    val sessionOwner = koinApplication.koin.get<ProcessMultiplayerSessionOwner>()
    val sessionScope = koinApplication.koin.get<CoroutineScope>(named("multiplayerSession"))
    val transportScope = koinApplication.koin.get<CoroutineScope>(named("p2pTransport"))
    val lifecycle = koinApplication.koin.get<AppLifecycleCoordinator>()
    application {
        Window(
            onCloseRequest = {
                try {
                    runBlocking {
                        shutdownDesktopMultiplayer(
                            sessionOwner = sessionOwner,
                            sessionScope = sessionScope,
                            transportScope = transportScope,
                        )
                    }
                } finally {
                    koinApplication.close()
                    exitApplication()
                }
            },
            title = "Parlor",
        ) {
            DisposableEffect(window, lifecycle) {
                val listener = object : WindowAdapter() {
                    override fun windowGainedFocus(event: WindowEvent) = lifecycle.notifyActive()
                    override fun windowLostFocus(event: WindowEvent) = lifecycle.notifyInactive()
                    override fun windowIconified(event: WindowEvent) = lifecycle.notifyBackgrounded()
                }
                window.addWindowFocusListener(listener)
                window.addWindowListener(listener)
                if (window.isFocused) lifecycle.notifyActive()
                onDispose {
                    window.removeWindowFocusListener(listener)
                    window.removeWindowListener(listener)
                    lifecycle.notifyBackgrounded()
                }
            }
            App()
        }
    }
}

/**
 * Desktop owns real LAN
 * resources. Give the logical Leave transaction a bounded opportunity to
 * notify peers/revoke credentials, then always cancel the process scope so a
 * stalled transport cannot block the window-exit path or launch new work.
 */
internal suspend fun shutdownDesktopMultiplayer(
    sessionOwner: ProcessMultiplayerSessionOwner,
    sessionScope: CoroutineScope,
    transportScope: CoroutineScope,
    timeoutMillis: Long = DESKTOP_SHUTDOWN_TIMEOUT_MILLIS,
): Result<Unit, NetError> = try {
    withTimeoutOrNull(timeoutMillis) {
        sessionOwner.state.value.routeOrNull?.let { route ->
            sessionOwner.leaveRoute(route, SessionEndReason.HostLeft)
        } ?: Result.Success(Unit)
    } ?: Result.Failure(NetError.Timeout)
} finally {
    sessionScope.cancel("Desktop application is closing")
    transportScope.cancel("Desktop application is closing")
}

private const val DESKTOP_SHUTDOWN_TIMEOUT_MILLIS: Long = 5_000L
