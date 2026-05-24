package com.parlor.app.p2p

import com.parlor.transport.p2p.p2pTransportModule
import org.koin.core.module.Module

/**
 * P2P opt-in seam — enabled variant. Selected by composeApp's build.gradle.kts
 * when `parlor.p2p.enabled=true`. Pulls in the real
 * [com.parlor.transport.p2p.p2pTransportModule] which registers a
 * P2pKit-backed `RoomTransport` in the DI graph.
 *
 * The disabled twin lives at `composeApp/src/p2pDisabledMain/.../P2pBootstrap.kt`.
 * The two files must keep identical signatures.
 */
internal fun p2pBootstrapModules(): List<Module> = listOf(p2pTransportModule)
