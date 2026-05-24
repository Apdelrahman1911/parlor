package com.parlor.transport.p2p

import org.koin.core.module.Module

/**
 * Koin module that registers a [com.parlor.networking.transport.RoomTransport]
 * backed by P2pKit. The actual binding lives in platform source sets because
 * P2pKit's LAN transport has different DSL signatures per platform (Android
 * requires a `Context`).
 *
 * composeApp pulls this module into its DI graph only when
 * `parlor.p2p.enabled=true`. With the flag off the dependency on
 * `:shared:transport-p2p` doesn't exist, so the pass-and-play path is fully
 * unaffected.
 */
expect val p2pTransportModule: Module

/** App-wide P2pKit advertisement scope. All Parlor devices use the same id. */
const val P2P_APP_ID: String = "com.parlor.app.whodunit"

internal fun randomDeviceTag(): String =
    (1..6).map { kotlin.random.Random.nextInt(36).toString(36) }.joinToString("")
