package com.parlor.transport.p2p

import com.parlor.networking.transport.RoomTransport
import dev.p2pkit.core.AppId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.random.Random

/**
 * Koin module that registers a [RoomTransport] backed by P2pKit.
 *
 * Consumers depend on the abstract [RoomTransport] (in `:shared:networking`),
 * never on this implementation. composeApp pulls this module into its DI
 * graph only when `parlor.p2p.enabled=true` — the build is structured so
 * the dependency on `:shared:transport-p2p` doesn't exist when the flag is
 * off, keeping the pass-and-play path entirely unaffected.
 *
 * The `AppId` is hard-coded to `"com.parlor.app.whodunit"` so all Parlor
 * devices advertising on the same LAN see each other. `deviceName` defaults
 * to a randomised tag; future Phase 8.x work could plumb the human-chosen
 * player name through here so the host's room shows up with the right label
 * in the join screen.
 */
val p2pTransportModule = module {
    single<CoroutineScope>(qualifier = named("p2pTransport")) {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    single<RoomTransport> {
        P2pKitRoomTransport(
            appId = AppId(P2P_APP_ID),
            deviceName = "parlor-${randomDeviceTag()}",
            scope = get<CoroutineScope>(qualifier = named("p2pTransport")),
        )
    }
}

/** App-wide P2pKit advertisement scope. All Parlor devices use the same id. */
const val P2P_APP_ID: String = "com.parlor.app.whodunit"

private fun randomDeviceTag(): String =
    (1..6).map { Random.nextInt(36).toString(36) }.joinToString("")
