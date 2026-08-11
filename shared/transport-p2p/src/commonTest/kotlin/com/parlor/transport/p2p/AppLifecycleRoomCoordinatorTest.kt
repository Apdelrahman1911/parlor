package com.parlor.transport.p2p

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppLifecycleRoomCoordinatorTest {
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun foreground_cannot_be_overtaken_by_background_captured_during_room_registration() = runTest {
        val coordinator = AppLifecycleRoomCoordinator()
        val room = BlockingLifecycleRoom()
        coordinator.backgrounded(atEpochMillis = 100L)

        val registration = async { coordinator.register("room-generation-1", room) }
        room.backgroundStarted.await()
        val foreground = async { coordinator.foregrounded(atEpochMillis = 200L) }
        runCurrent()

        assertFalse(
            foreground.isCompleted,
            "foreground must serialize behind the captured background application",
        )
        room.allowBackgroundCompletion.complete(Unit)
        registration.await()
        foreground.await()

        assertEquals(RoomVisibility.Foreground, room.visibility)
        assertEquals(
            listOf("background-start", "background-complete", "foreground"),
            room.calls,
        )
    }

    @Test
    fun close_from_an_old_generation_cannot_detach_the_replacement_room() = runTest {
        val coordinator = AppLifecycleRoomCoordinator()
        val oldRoom = RecordingLifecycleRoom()
        val replacement = RecordingLifecycleRoom()
        coordinator.register("old", oldRoom)
        coordinator.register("replacement", replacement)

        coordinator.roomClosed("old")
        coordinator.backgrounded(atEpochMillis = 300L)

        assertEquals(emptyList(), oldRoom.calls)
        assertEquals(listOf("background"), replacement.calls)
    }
}

private enum class RoomVisibility {
    Active,
    Background,
    Foreground,
}

private class BlockingLifecycleRoom : AppLifecycleAwareRoom {
    val backgroundStarted = CompletableDeferred<Unit>()
    val allowBackgroundCompletion = CompletableDeferred<Unit>()
    val calls = mutableListOf<String>()
    var visibility: RoomVisibility = RoomVisibility.Active

    override suspend fun appBackgrounded(atEpochMillis: Long) {
        calls += "background-start"
        backgroundStarted.complete(Unit)
        allowBackgroundCompletion.await()
        visibility = RoomVisibility.Background
        calls += "background-complete"
    }

    override suspend fun appForegrounded(atEpochMillis: Long) {
        visibility = RoomVisibility.Foreground
        calls += "foreground"
    }
}

private class RecordingLifecycleRoom : AppLifecycleAwareRoom {
    val calls = mutableListOf<String>()

    override suspend fun appBackgrounded(atEpochMillis: Long) {
        calls += "background"
    }

    override suspend fun appForegrounded(atEpochMillis: Long) {
        calls += "foreground"
    }
}
