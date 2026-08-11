package com.parlor.transport.p2p

import dev.p2pkit.core.AppId
import dev.p2pkit.core.BackgroundPolicy
import dev.p2pkit.core.ExplicitSecurityRisk
import dev.p2pkit.core.PeerAuthorizationPolicy
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.ReconnectPolicy
import dev.p2pkit.core.SecurityMode
import dev.p2pkit.transport.lan.lan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.parlor.networking.transport.RoomTransport

/**
 * iOS `lan()` (in P2pKit's appleMain) takes no args — `nw_browser_t` +
 * `nw_listener_t` handle interface selection internally.
 */
@OptIn(ExplicitSecurityRisk::class)
private class IosP2pKitFactory(
    private val initializationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : P2pKitFactory {
    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
        withContext(initializationDispatcher) {
            P2pKit.create {
                this.appId = appId
                this.deviceName = deviceName
                transports { lan() }
                security {
                    // Encrypted authenticated transport; Parlor owns room admission.
                    mode = SecurityMode.AuthenticatedV2(
                        PeerAuthorizationPolicy.AcceptAnyAuthenticatedSameApp,
                    )
                }
                lifecycle {
                    onBackground = BackgroundPolicy.CloseActiveSessions
                    reconnectPolicy = ReconnectPolicy.Enabled(
                        maxAttempts = 10,
                        retryDelayMillis = 3_000,
                    )
                }
            }
        }
}

actual val p2pTransportModule: Module = module {
    single<CoroutineScope>(qualifier = named("p2pTransport")) {
        createP2pTransportScope()
    }
    single<P2pDiagnostics> {
        BoundedP2pDiagnostics(
            scope = get(qualifier = named("p2pTransport")),
            writer = platformP2pDiagnosticWriter(),
        )
    }
    single<P2pKitFactory> { IosP2pKitFactory() }
    single<RoomTransport> {
        P2pKitRoomTransport(
            appId = AppId(P2P_APP_ID),
            deviceName = "parlor-${randomDeviceTag()}",
            scope = get(qualifier = named("p2pTransport")),
            kitFactory = get(),
            secureStorage = get(),
            diagnostics = get(),
        )
    }
}
