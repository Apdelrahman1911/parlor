package com.parlor.transport.p2p

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Creates the process-owned P2P scope used by the platform composition roots.
 *
 * The dispatcher is an explicit injection boundary so tests and alternate
 * hosts do not have to inherit a global dispatcher. Mobile/desktop production
 * modules intentionally use the default argument and own cancellation at the
 * process lifecycle boundary.
 */
internal fun createP2pTransportScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
