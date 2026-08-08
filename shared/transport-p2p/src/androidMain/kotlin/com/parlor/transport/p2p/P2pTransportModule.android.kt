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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * The `init` block fires `P2pKitAndroid.initialize(applicationContext)` once
 * per process — required to give the kit a stable, file-persisted `PeerId`
 * instead of the in-memory fallback that regenerates every cold start.
 * Without it the kit emits a `P2pLogger.warn` at construction and behaves
 * as in P2pKit v0.1.
 */
@OptIn(ExplicitSecurityRisk::class)
private class AndroidP2pKitFactory(private val applicationContext: Context) : P2pKitFactory {
    init {
        P2pKitAndroid.initialize(applicationContext)
    }

    override fun createKit(appId: AppId, deviceName: String): P2pKit = P2pKit.create {
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
        // Auto-recover a peer's outgoing session from a transient drop (the SDK
        // default is Disabled → terminal HostLost on any blip). See the desktop
        // factory + PROBLEMS_PARLOR.md → p2p-003 / peer-recovery.
        lifecycle { reconnectPolicy = ReconnectPolicy.Enabled(maxAttempts = 10, retryDelayMillis = 3_000) }
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
        )
    }
}
