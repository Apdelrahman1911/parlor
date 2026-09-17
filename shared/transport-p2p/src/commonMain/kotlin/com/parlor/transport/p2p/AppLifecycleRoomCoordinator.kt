package com.parlor.transport.p2p

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serializes process lifecycle state with registration of the one active room.
 *
 * Platform lifecycle callbacks and room creation run in different coroutines.
 * Updating the process state under one mutex and applying it to a room later
 * permits a newer foreground callback to be overtaken by a stale background
 * application. [transitionMutex] makes the state commit and its room effect one
 * ordered transaction while [stateMutex] still lets an idempotent close
 * callback detach a room without deadlocking inside that room's lifecycle work.
 */
internal class AppLifecycleRoomCoordinator(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val onFailure: (Exception) -> Unit = {},
) {
    private data class ActiveRoom(
        val registrationId: String,
        val room: AppLifecycleAwareRoom,
    )

    private val transitionMutex = Mutex()
    private val stateMutex = Mutex()
    private var activeRoom: ActiveRoom? = null
    private var appIsBackgrounded: Boolean = false
    private var lastBackgroundedAt: Long? = null
    private class Retention(
        val owner: ActiveRoom,
        val startedAt: Long,
        val deadline: Long,
        val connection: RetainedRoomConnection,
        var job: Job? = null,
    )

    private var retention: Retention? = null

    suspend fun backgrounded(atEpochMillis: Long): Unit = transitionMutex.withLock {
        val owner = stateMutex.withLock {
            // Repeated platform signals never restart either deadline.
            if (appIsBackgrounded) return
            appIsBackgrounded = true
            lastBackgroundedAt = atEpochMillis
            activeRoom
        } ?: return
        val deadline = atEpochMillis + CONNECTION_GRACE_MS
        val observedAt = nowMillis()
        val connection = if (observedAt in atEpochMillis until deadline) {
            attemptRetention { owner.room.retainInBackground(atEpochMillis, deadline) }
        } else null
        if (connection == null) {
            owner.room.appBackgrounded(atEpochMillis, maxOf(atEpochMillis, observedAt))
            return
        }
        val pending = Retention(owner, atEpochMillis, deadline, connection)
        val accepted = stateMutex.withLock {
            if (activeRoom !== owner) false else true.also { retention = pending }
        }
        if (accepted) startRetention(pending)
    }

    suspend fun foregrounded(atEpochMillis: Long): Unit = transitionMutex.withLock {
        val (owner, pending) = stateMutex.withLock {
            appIsBackgrounded = false
            lastBackgroundedAt = null
            activeRoom to retention.also { retention = null }
        }
        pending?.job?.cancel()
        val room = owner?.room ?: return
        val observedAt = maxOf(atEpochMillis, nowMillis())
        if (pending != null && pending.owner === owner) {
            val restored = observedAt in pending.startedAt until pending.deadline &&
                attemptRetention { room.resumeRetainedConnection(observedAt) } == true
            if (restored) return
            room.appBackgrounded(pending.startedAt, maxOf(observedAt, nowMillis()))
        }
        room.appForegrounded(maxOf(observedAt, nowMillis()))
    }

    suspend fun register(
        registrationId: String,
        room: AppLifecycleAwareRoom,
    ) = transitionMutex.withLock {
        var registered = false
        try {
            val previous = stateMutex.withLock { retention.also { retention = null } }
            previous?.job?.cancel()
            previous?.let { it.owner.room.appBackgrounded(it.startedAt, nowMillis()) }
            val backgroundedAt = stateMutex.withLock {
                activeRoom = ActiveRoom(registrationId, room)
                lastBackgroundedAt.takeIf { appIsBackgrounded }
            }
            // A newly opened/replacement room never earns grace while already hidden.
            if (backgroundedAt != null) room.appBackgrounded(backgroundedAt, nowMillis())
            currentCoroutineContext().ensureActive()
            registered = true
        } finally {
            if (!registered) withContext(NonCancellable) {
                // Detach before releasing the transition lock: a queued
                // foreground must not revive a room whose opening failed.
                roomClosed(registrationId)
            }
        }
    }

    suspend fun roomClosed(registrationId: String) {
        stateMutex.withLock {
            if (activeRoom?.registrationId == registrationId) {
                activeRoom = null
                retention?.job?.cancel()
                retention = null
            }
        }
    }

    private suspend fun startRetention(pending: Retention) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val lost = withTimeoutOrNull((pending.deadline - nowMillis()).coerceAtLeast(1L)) {
                pending.connection.health.first { !it }
                true
            } == true
            attemptRetention {
                transitionMutex.withLock {
                    val ownsRoom = stateMutex.withLock {
                        if (activeRoom === pending.owner && retention === pending && appIsBackgrounded) {
                            retention = null
                            true
                        } else false
                    }
                    if (ownsRoom) {
                        val observedAt = if (lost) nowMillis() else maxOf(nowMillis(), pending.deadline)
                        pending.owner.room.appBackgrounded(pending.startedAt, observedAt)
                    }
                }
            }
        }
        val accepted = stateMutex.withLock {
            if (retention !== pending) false else true.also { pending.job = job }
        }
        if (accepted) job.start() else job.cancel()
    }

    private suspend fun <T> attemptRetention(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        onFailure(failure)
        null
    }

    companion object {
        const val CONNECTION_GRACE_MS = 15_000L
    }
}
