package com.parlor.app.p2p

import org.koin.core.module.Module

/**
 * P2P opt-in seam — disabled variant. Selected by composeApp's build.gradle.kts
 * when the `parlor.p2p.enabled` Gradle property is unset or false. Returns no
 * Koin modules, so `RoomTransport` is absent from the DI graph and the
 * Host / Join entry points on the Home screen render in their disabled state.
 *
 * The enabled twin lives at `composeApp/src/p2pEnabledMain/.../P2pBootstrap.kt`.
 * The two files must keep identical signatures.
 */
internal fun p2pBootstrapModules(): List<Module> = emptyList()
