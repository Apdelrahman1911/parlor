package com.parlor.app.p2p

import com.parlor.transport.p2p.p2pTransportModule
import org.koin.core.module.Module

/**
 * Production multiplayer bootstrap.
 *
 * P2pKit is a required, pinned dependency. A missing transport artifact or DI
 * binding is therefore a startup/build failure instead of silently producing
 * a pass-and-play-only application.
 */
internal fun p2pBootstrapModules(): List<Module> = listOf(p2pTransportModule)
