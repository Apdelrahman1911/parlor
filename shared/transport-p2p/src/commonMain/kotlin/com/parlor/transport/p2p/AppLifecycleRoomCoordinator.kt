package com.parlor.transport.p2p

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
internal class AppLifecycleRoomCoordinator {
    private data class ActiveRoom(
        val registrationId: String,
        val room: AppLifecycleAwareRoom,
    )

    private val transitionMutex = Mutex()
    private val stateMutex = Mutex()
    private var activeRoom: ActiveRoom? = null
    private var appIsBackgrounded: Boolean = false
    private var lastBackgroundedAt: Long? = null

    suspend fun backgrounded(atEpochMillis: Long) = transitionMutex.withLock {
        val room = stateMutex.withLock {
            appIsBackgrounded = true
            lastBackgroundedAt = atEpochMillis
            activeRoom?.room
        }
        room?.appBackgrounded(atEpochMillis)
    }

    suspend fun foregrounded(atEpochMillis: Long) = transitionMutex.withLock {
        val room = stateMutex.withLock {
            appIsBackgrounded = false
            activeRoom?.room
        }
        room?.appForegrounded(atEpochMillis)
    }

    suspend fun register(
        registrationId: String,
        room: AppLifecycleAwareRoom,
    ) = transitionMutex.withLock {
        var registered = false
        try {
            val backgroundedAt = stateMutex.withLock {
                activeRoom = ActiveRoom(registrationId, room)
                lastBackgroundedAt.takeIf { appIsBackgrounded }
            }
            if (backgroundedAt != null) room.appBackgrounded(backgroundedAt)
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
            if (activeRoom?.registrationId == registrationId) activeRoom = null
        }
    }
}
