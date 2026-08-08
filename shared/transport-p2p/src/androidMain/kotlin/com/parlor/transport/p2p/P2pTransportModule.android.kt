package com.parlor.transport.p2p

import android.content.Context
import dev.p2pkit.core.AppId
import dev.p2pkit.core.ExplicitSecurityRisk
import dev.p2pkit.core.PeerAuthorizationPolicy
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.ReconnectPolicy
import dev.p2pkit.core.SecurityMode
import dev.p2pkit.core.android.P2pKitAndroid
import dev.p2pkit.transport.lan.lan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.parlor.networking.transport.RoomTransport

/**
 * Android `lan(Context)` requires the application Context so it can use
 * `NsdManager` for discovery and bind the TCP listener appropriately.
 * Resolved via Koin's `androidContext()`, which composeApp wires in
 * `ParlorApplication.onCreate()` through `startKoin { androidContext(this) }`.
 *
 * The factory initializes P2pKit once before first kit construction. Both the
 * identity-file work and kit construction run on [Dispatchers.IO], never on
 * Android's main thread.
 */
@OptIn(ExplicitSecurityRisk::class)
internal class AndroidP2pKitFactory(
    private val applicationContext: Context,
    private val initializationDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : P2pKitFactory {
    private val initializationMutex = Mutex()
    private var initialized = false

    override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
        withContext(initializationDispatcher) {
            initializationMutex.withLock {
                if (!initialized) {
                    // Reads/creates P2pKit's stable identity file. Keep it on
                    // the explicit initialization dispatcher, never Android's
                    // main thread.
                    P2pKitAndroid.initialize(applicationContext)
                    initialized = true
                }
            }
            P2pKit.create {
                this.appId = appId
                this.deviceName = deviceName
                transports { lan(applicationContext) }
                security {
                    // P2pKit authenticates and encrypts the transport key. Parlor then
                    // applies room-code + explicit-host-approval admission in its room
                    // protocol. Same-AppId authentication alone is not user identity.
                    mode = SecurityMode.AuthenticatedV2(
                        PeerAuthorizationPolicy.AcceptAnyAuthenticatedSameApp,
                    )
                }
                lifecycle {
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
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    single<P2pKitFactory> { AndroidP2pKitFactory(androidContext()) }
    single<RoomTransport> {
        P2pKitRoomTransport(
            appId = AppId(P2P_APP_ID),
            deviceName = "parlor-${randomDeviceTag()}",
            scope = get(qualifier = named("p2pTransport")),
            kitFactory = get(),
            secureStorage = get(),
        )
    }
}
