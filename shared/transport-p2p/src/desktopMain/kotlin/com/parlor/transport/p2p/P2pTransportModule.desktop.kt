package com.parlor.transport.p2p

import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.transport.lan.lan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.parlor.networking.transport.RoomTransport

/**
 * Desktop / JVM `lan()` takes no arguments — JmDNS handles interface
 * selection.
 */
private class JvmP2pKitFactory : P2pKitFactory {
    override fun createKit(appId: AppId, deviceName: String): P2pKit = P2pKit.create {
        this.appId = appId
        this.deviceName = deviceName
        transports { lan() }
    }
}

actual val p2pTransportModule: Module = module {
    single<CoroutineScope>(qualifier = named("p2pTransport")) {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    single<P2pKitFactory> { JvmP2pKitFactory() }
    single<RoomTransport> {
        P2pKitRoomTransport(
            appId = AppId(P2P_APP_ID),
            deviceName = "parlor-${randomDeviceTag()}",
            scope = get(qualifier = named("p2pTransport")),
            kitFactory = get(),
        )
    }
}
