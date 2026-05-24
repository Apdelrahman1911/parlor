package com.parlor.transport.p2p

import android.content.Context
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
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
 */
private class AndroidP2pKitFactory(private val applicationContext: Context) : P2pKitFactory {
    override fun createKit(appId: AppId, deviceName: String): P2pKit = P2pKit.create {
        this.appId = appId
        this.deviceName = deviceName
        transports { lan(applicationContext) }
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
