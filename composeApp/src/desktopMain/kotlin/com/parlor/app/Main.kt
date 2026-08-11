package com.parlor.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.parlor.app.di.allModules
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

/**
 * Desktop (JVM) entry. Single Compose window, cozy-noir from frame to frame.
 */
fun main() {
    val koinApplication = startKoin { modules(allModules) }
    val sessionOwner = koinApplication.koin.get<ProcessMultiplayerSessionOwner>()
    val sessionScope = koinApplication.koin.get<CoroutineScope>(named("multiplayerSession"))
    val transportScope = koinApplication.koin.get<CoroutineScope>(named("p2pTransport"))
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
            App()
        }
    }
}

/**
 * Desktop is a non-shipping development target, but it still owns real LAN
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
