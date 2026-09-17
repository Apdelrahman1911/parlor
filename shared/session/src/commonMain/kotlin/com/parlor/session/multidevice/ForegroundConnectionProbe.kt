package com.parlor.session.multidevice

import com.parlor.core.ids.PlayerId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One fresh, bounded-by-caller challenge. Replies are already envelope/identity validated. */
internal class ForegroundConnectionProbe(private val idGenerator: () -> String) {
    private class Pending(val id: String, val players: Set<PlayerId>) {
        val revisions = mutableMapOf<PlayerId, Long>()
        val result = CompletableDeferred<Map<PlayerId, Long>?>()
    }

    private val probeMutex = Mutex()
    private val stateMutex = Mutex()
    private var pending: Pending? = null
    private var closed = false

    suspend fun check(
        players: Set<PlayerId>,
        send: suspend (String) -> Boolean,
    ): Map<PlayerId, Long>? = probeMutex.withLock {
        if (players.isEmpty()) return@withLock null
        val request = stateMutex.withLock {
            if (closed) null else Pending(idGenerator(), players.toSet()).also { pending = it }
        } ?: return@withLock null
        try {
            if (!send(request.id)) return@withLock null
            request.result.await()
        } finally {
            withContext(NonCancellable) {
                stateMutex.withLock { if (pending === request) pending = null }
            }
        }
    }

    suspend fun accept(playerId: PlayerId, id: String, revision: Long): Boolean = stateMutex.withLock {
        val request = pending ?: return@withLock false
        if (closed || id != request.id || playerId !in request.players || revision < 0L) return@withLock false
        request.revisions[playerId] = revision
        if (request.revisions.keys == request.players) request.result.complete(request.revisions.toMap())
        true
    }

    suspend fun close() = stateMutex.withLock {
        closed = true
        pending?.result?.complete(null)
        pending = null
    }
}
