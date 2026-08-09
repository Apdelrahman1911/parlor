package com.parlor.storage.snapshot

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.phase.GamePhase
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SerializedSnapshotWriterTest {
    private val sessionId = SessionId("session")

    @Test
    fun concurrent_flushes_are_serialized_and_latest_state_wins() = runTest {
        val firstSaveEntered = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()
        val store = RecordingStore(
            beforeSave = { snapshot ->
                if (snapshot.payload.contentEquals(byteArrayOf(1))) {
                    firstSaveEntered.complete(Unit)
                    releaseFirstSave.await()
                }
            },
        )
        val writer = writer(store)

        val first = async { writer.persist(TestState(1)) }
        firstSaveEntered.await()
        val second = async { writer.persist(TestState(2)) }
        runCurrent()

        assertThat(store.maxConcurrentCalls).isEqualTo(1)
        releaseFirstSave.complete(Unit)
        first.await()
        second.await()

        assertThat(store.savedPayloads).containsExactly(1, 2)
        assertThat(store.maxConcurrentCalls).isEqualTo(1)
        assertThat(writer.status.value).isEqualTo(SnapshotWriteStatus.Saved)
    }

    @Test
    fun a_failed_state_is_retried_but_a_successful_duplicate_is_deduplicated() = runTest {
        var failNext = true
        val store = RecordingStore(
            saveResult = {
                if (failNext) {
                    failNext = false
                    Result.Failure(DataError.DiskFull)
                } else {
                    EmptyOk
                }
            },
        )
        val writer = writer(store)
        val state = TestState(7)

        assertThat(writer.persist(state)).isInstanceOf(Result.Failure::class)
        assertThat(writer.persist(state)).isInstanceOf(Result.Success::class)
        assertThat(writer.persist(state)).isInstanceOf(Result.Success::class)

        assertThat(store.saveCalls).isEqualTo(2)
        assertThat(writer.status.value).isEqualTo(SnapshotWriteStatus.Saved)
    }

    @Test
    fun explicit_discard_waits_for_an_in_flight_save_and_prevents_resurrection() = runTest {
        val saveEntered = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        val store = RecordingStore(
            beforeSave = {
                saveEntered.complete(Unit)
                releaseSave.await()
            },
        )
        val writer = writer(store)

        val saving = async { writer.persist(TestState(3)) }
        saveEntered.await()
        val discarding = async { writer.discard() }
        runCurrent()
        releaseSave.complete(Unit)
        saving.await()
        discarding.await()
        writer.persist(TestState(4))

        assertThat(store.saveCalls).isEqualTo(1)
        assertThat(store.deleteCalls).isEqualTo(1)
        assertThat(store.savedPayloads).containsExactly(3)
        assertThat(store.maxConcurrentCalls).isEqualTo(1)
        assertThat(writer.status.value).isEqualTo(SnapshotWriteStatus.Deleted)
    }

    @Test
    fun completed_state_deletes_and_an_explicit_rematch_can_persist_again() = runTest {
        val store = RecordingStore()
        val writer = writer(store)

        writer.persist(TestState(1))
        writer.persist(TestState(2, completed = true))
        writer.persist(TestState(3))

        assertThat(store.savedPayloads).containsExactly(1, 3)
        assertThat(store.deleteCalls).isEqualTo(1)
        assertThat(writer.status.value).isEqualTo(SnapshotWriteStatus.Saved)
    }

    @Test
    fun cancellation_propagates_and_does_not_leave_a_false_writing_status() = runTest {
        val saveEntered = CompletableDeferred<Unit>()
        val neverRelease = CompletableDeferred<Unit>()
        val store = RecordingStore(
            beforeSave = {
                saveEntered.complete(Unit)
                neverRelease.await()
            },
        )
        val writer = writer(store)
        val saving = async { writer.persist(TestState(9)) }
        saveEntered.await()

        saving.cancel(CancellationException("screen disposed"))

        assertFailsWith<CancellationException> { saving.await() }
        assertThat(writer.status.value).isEqualTo(SnapshotWriteStatus.Idle)
    }

    private fun writer(store: SnapshotStore) = SerializedSnapshotWriter<TestState>(
        store = store,
        sessionId = sessionId,
        snapshotFor = { state ->
            GameSnapshot(
                sessionId = sessionId,
                gameId = GameId("test"),
                engineVersion = SemVer(1, 0, 0),
                createdAt = Instant.fromEpochSeconds(state.value.toLong()),
                phaseId = state.phase.id,
                payload = byteArrayOf(state.value.toByte()),
            )
        },
        isCompleted = TestState::completed,
        // Keep scheduling under runTest; production uses Dispatchers.Default
        // so game/envelope serialization cannot block the UI dispatcher.
        writeContext = EmptyCoroutineContext,
    )
}

private data class TestState(
    val value: Int,
    val completed: Boolean = false,
) : GameState {
    override val phase: GamePhase = object : GamePhase {
        override val id: String = if (completed) "completed" else "active"
    }
    override val players: List<Player> = emptyList()
}

private class RecordingStore(
    private val beforeSave: suspend (GameSnapshot) -> Unit = {},
    private val saveResult: () -> EmptyResult<DataError> = { EmptyOk },
) : SnapshotStore {
    val savedPayloads = mutableListOf<Int>()
    var saveCalls = 0
        private set
    var deleteCalls = 0
        private set
    var maxConcurrentCalls = 0
        private set
    private var concurrentCalls = 0

    override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> {
        concurrentCalls += 1
        maxConcurrentCalls = maxOf(maxConcurrentCalls, concurrentCalls)
        return try {
            saveCalls += 1
            beforeSave(snapshot)
            savedPayloads += snapshot.payload.single().toInt()
            saveResult()
        } finally {
            concurrentCalls -= 1
        }
    }

    override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> {
        concurrentCalls += 1
        maxConcurrentCalls = maxOf(maxConcurrentCalls, concurrentCalls)
        return try {
            deleteCalls += 1
            EmptyOk
        } finally {
            concurrentCalls -= 1
        }
    }

    override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> =
        Result.Failure(DataError.NotFound)

    override suspend fun listUnfinished(): Result<List<SessionId>, DataError> =
        Result.Success(emptyList())
}
