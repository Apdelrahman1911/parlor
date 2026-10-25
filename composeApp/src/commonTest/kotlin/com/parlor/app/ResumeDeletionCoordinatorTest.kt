package com.parlor.app

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.ResumableSessionSelection
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ResumeDeletionCoordinatorTest {
    @Test
    fun confirmation_cancel_and_repeated_callbacks_only_delete_the_selected_local_save() = runTest {
        val store = RecordingStore()
        val transport = RecordingTransport()
        var refreshes = 0
        val coordinator = ResumeDeletionCoordinator(this, store, transport, { true }, { refreshes++ })
        coordinator.request(ResumeDeletionTarget.Local(SessionId("selected")), "Saved game")
        val cancelled = coordinator.state.value.request!!
        assertEquals(emptyList(), store.deleted)
        coordinator.cancel(cancelled)
        coordinator.confirm(cancelled)
        runCurrent()
        assertEquals(emptyList(), store.deleted)
        coordinator.request(ResumeDeletionTarget.Local(SessionId("selected")), "Saved game")
        val confirmed = coordinator.state.value.request!!
        coordinator.confirm(cancelled)
        coordinator.confirm(confirmed)
        coordinator.confirm(confirmed)
        runCurrent()
        coordinator.confirm(confirmed)
        assertEquals(listOf(SessionId("selected")), store.deleted)
        assertEquals(1, refreshes)
        assertEquals(0, transport.deleted.size)
        assertNull(coordinator.state.value.request)
    }

    @Test
    fun multiplayer_deletion_uses_the_selected_opaque_handle_and_never_the_local_store() = runTest {
        val store = RecordingStore()
        val transport = RecordingTransport()
        val selection = object : ResumableSessionSelection {}
        val coordinator = ResumeDeletionCoordinator(this, store, transport, { true }, {})
        coordinator.request(ResumeDeletionTarget.Multiplayer(selection), "Multiplayer")
        coordinator.confirm(coordinator.state.value.request!!)
        runCurrent()
        assertEquals(listOf(selection), transport.deleted)
        assertEquals(emptyList(), store.deleted)
    }

    @Test
    fun storage_failure_keeps_confirmation_visible_and_cancellable() = runTest {
        val store = RecordingStore().apply { failure = true }
        var refreshes = 0
        val coordinator = ResumeDeletionCoordinator(this, store, RecordingTransport(), { true }, { refreshes++ })
        coordinator.request(ResumeDeletionTarget.Local(SessionId("selected")), "Saved game")
        val request = coordinator.state.value.request!!
        coordinator.confirm(request)
        runCurrent()
        assertTrue(coordinator.state.value.failed)
        assertFalse(coordinator.state.value.busy)
        assertEquals(0, refreshes)
        coordinator.cancel(request)
        assertNull(coordinator.state.value.request)
    }

    @Test
    fun busy_confirmation_cannot_cancel_or_start_a_second_deletion() = runTest {
        val store = object : SnapshotStore by InMemorySnapshotStore() {
            override suspend fun delete(sessionId: SessionId): Result<Unit, DataError> = awaitCancellation()
        }
        val coordinator = ResumeDeletionCoordinator(this, store, RecordingTransport(), { true }, {})
        coordinator.request(ResumeDeletionTarget.Local(SessionId("selected")), "Saved game")
        val request = coordinator.state.value.request!!
        coordinator.confirm(request)
        runCurrent()
        coordinator.cancel(request)
        coordinator.request(ResumeDeletionTarget.Local(SessionId("other")), "Other")
        assertTrue(coordinator.state.value.busy)
        assertEquals(request, coordinator.state.value.request)
        coordinator.invalidate()
        runCurrent()
        assertNull(coordinator.state.value.request)
    }

    @Test
    fun navigation_and_noncooperative_old_completions_cannot_refresh_or_replace_new_confirmation() = runTest {
        val release = CompletableDeferred<Unit>()
        val store = object : SnapshotStore by InMemorySnapshotStore() {
            override suspend fun delete(sessionId: SessionId): Result<Unit, DataError> = withContext(NonCancellable) {
                release.await()
                Result.Success(Unit)
            }
        }
        var home = true
        var refreshes = 0
        val coordinator = ResumeDeletionCoordinator(this, store, RecordingTransport(), { home }, { refreshes++ })
        coordinator.request(ResumeDeletionTarget.Local(SessionId("selected")), "Saved game")
        val old = coordinator.state.value.request!!
        home = false
        coordinator.confirm(old)
        assertFalse(coordinator.state.value.busy)
        home = true
        coordinator.confirm(old)
        runCurrent()
        coordinator.invalidate()
        coordinator.request(ResumeDeletionTarget.Local(SessionId("other")), "Other")
        val newer = coordinator.state.value.request
        release.complete(Unit)
        runCurrent()
        assertEquals(newer, coordinator.state.value.request)
        assertEquals(0, refreshes)
    }

    private class RecordingStore : SnapshotStore by InMemorySnapshotStore() {
        val deleted = mutableListOf<SessionId>()
        var failure = false
        override suspend fun delete(sessionId: SessionId): Result<Unit, DataError> {
            deleted += sessionId
            return if (failure) Result.Failure(DataError.IoError("test")) else Result.Success(Unit)
        }
    }

    private class RecordingTransport : RoomTransport {
        val deleted = mutableListOf<ResumableSessionSelection>()
        override val capability = TransportCapability(false, 0, 1024)
        override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = error("not used")
        override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> = error("not used")
        override suspend fun discardResumableSession(): Result<Unit, NetError> = error("unsafe unselected deletion")
        override suspend fun discardResumableSession(selection: ResumableSessionSelection): Result<Unit, NetError> {
            deleted += selection
            return Result.Success(Unit)
        }
    }
}
