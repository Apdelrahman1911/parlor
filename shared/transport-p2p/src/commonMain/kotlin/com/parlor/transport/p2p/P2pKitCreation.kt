package com.parlor.transport.p2p

import dev.p2pkit.core.P2pKit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Retain construction ownership until the caller receives the kit. P2pKit starts
 * internal workers during construction, before start(), so a result discarded
 * by withContext's cancellation check still requires stop(). The platform block
 * must return the synchronously constructed kit without a further suspension.
 */
internal suspend fun createOwnedP2pKit(
    initializationDispatcher: CoroutineDispatcher,
    create: suspend () -> P2pKit,
): P2pKit {
    var pendingKit: P2pKit? = null
    try {
        val kit = withContext(initializationDispatcher) {
            create().also { pendingKit = it }
        }
        currentCoroutineContext().ensureActive()
        return kit
    } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
        // Cleanup is the ownership boundary, not an error translation boundary:
        // always rethrow the original failure, including caller cancellation.
        withContext(NonCancellable) {
            try {
                pendingKit?.stop()
            } catch (@Suppress("TooGenericExceptionCaught") cleanupFailure: Throwable) {
                if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
            }
        }
        throw failure
    }
}
