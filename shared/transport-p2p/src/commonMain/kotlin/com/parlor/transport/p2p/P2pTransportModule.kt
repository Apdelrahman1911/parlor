package com.parlor.transport.p2p

import org.koin.core.module.Module

/**
 * Koin module that registers a [com.parlor.networking.transport.RoomTransport]
 * backed by P2pKit. The actual binding lives in platform source sets because
 * P2pKit's LAN transport has different DSL signatures per platform (Android
 * requires a `Context`).
 *
 * The production app always includes this module so a release cannot
 * accidentally become pass-and-play-only because of a local Gradle flag.
 */
expect val p2pTransportModule: Module

/** App-wide P2pKit advertisement scope. All Parlor devices use the same id. */
const val P2P_APP_ID: String = "com.parlor.app"

internal fun randomDeviceTag(): String =
    List(DEVICE_TAG_LENGTH) {
        kotlin.random.Random.nextInt(DEVICE_TAG_RADIX).toString(DEVICE_TAG_RADIX)
    }.joinToString("")

private const val DEVICE_TAG_LENGTH: Int = 6
private const val DEVICE_TAG_RADIX: Int = 36
