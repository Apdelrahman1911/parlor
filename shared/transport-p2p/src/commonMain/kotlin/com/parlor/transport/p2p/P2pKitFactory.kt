package com.parlor.transport.p2p

import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit

/**
 * Platform-specific entry point for building a [P2pKit] instance with the
 * LAN transport configured. The LAN DSL function `lan()` lives in P2pKit's
 * platform source sets — JVM and iOS take no arguments, Android takes a
 * `Context` — so the construction must be in matching platform-specific
 * code here.
 *
 * Each platform supplies its own implementation:
 *  - desktopMain → `JvmP2pKitFactory` ⇒ `transports { lan() }`
 *  - androidMain → `AndroidP2pKitFactory(applicationContext)` ⇒
 *    `transports { lan(applicationContext) }`
 *  - iosMain    → `IosP2pKitFactory` ⇒ `transports { lan() }`
 *
 * Wired by the platform-specific `p2pTransportModule` (the `expect val` in
 * [P2pTransportModule]).
 */
interface P2pKitFactory {
    /**
     * Kit construction may load or create durable identity material. It is
     * suspending so each platform can keep that work off its UI thread.
     */
    suspend fun createKit(appId: AppId, deviceName: String): P2pKit
}
