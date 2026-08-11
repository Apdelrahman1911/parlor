package com.parlor.transport.p2p

import dev.p2pkit.core.AppId
import dev.p2pkit.core.BackgroundPolicy
import dev.p2pkit.core.ExplicitSecurityRisk
import dev.p2pkit.core.PeerAuthorizationPolicy
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.ReconnectPolicy
import dev.p2pkit.core.SecurityMode
import dev.p2pkit.core.dsl.jvmSecureIdentityStore
import dev.p2pkit.core.security.JvmSecureIdentityStore
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
 * Desktop / JVM `lan()` takes no arguments — JmDNS handles interface
 * selection.
 */
@OptIn(ExplicitSecurityRisk::class)
private class JvmP2pKitFactory(
    private val initializationDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : P2pKitFactory {
    // Desktop is a development/test harness, not a production release target.
    // P2pKit deliberately requires the host to supply protected durable JVM
    // storage; this process-only implementation makes that limitation explicit
    // and avoids ever writing key material to an unprotected file.
    private val developmentIdentityStore = DevelopmentIdentityStore()

    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
        withContext(initializationDispatcher) {
            P2pKit.create {
                this.appId = appId
                this.deviceName = deviceName
                transports { lan() }
                jvmSecureIdentityStore(developmentIdentityStore)
                security {
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

private class DevelopmentIdentityStore : JvmSecureIdentityStore {
    private val values = mutableMapOf<String, ByteArray>()

    override fun read(namespace: String): ByteArray? =
        synchronized(values) { values[namespace]?.copyOf() }

    override fun putIfAbsent(namespace: String, value: ByteArray): ByteArray =
        synchronized(values) {
            values.getOrPut(namespace) { value.copyOf() }.copyOf()
        }

    override fun delete(namespace: String): Boolean =
        synchronized(values) { values.remove(namespace) != null }
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
    single<P2pKitFactory> { JvmP2pKitFactory() }
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
