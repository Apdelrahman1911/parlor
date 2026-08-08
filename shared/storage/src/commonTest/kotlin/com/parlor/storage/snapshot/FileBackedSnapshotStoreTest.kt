package com.parlor.storage.snapshot

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.snapshot.GameSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FileBackedSnapshotStoreTest {

    private val json = Json
    private val sessionId = SessionId("snapshot-test")

    @Test
    fun protection_failure_is_reported_as_corrupted_without_crypto_detail() = runTest {
        val store = FileBackedSnapshotStore(
            fileSystem = FailingFileSystem(SnapshotProtectionException("secret key alias")),
            json = json,
        )

        assertEquals(
            Result.Failure(DataError.CorruptedData),
            store.load(sessionId),
        )
    }

    @Test
    fun generic_io_failure_is_sanitized() = runTest {
        val store = FileBackedSnapshotStore(
            fileSystem = FailingFileSystem(
                IllegalStateException("/private/container/path/snapshots"),
            ),
            json = json,
        )

        val result = store.load(sessionId)
        val failure = assertIs<Result.Failure<DataError>>(result)
        assertEquals(DataError.IoError("snapshot_io"), failure.error)
    }

    @Test
    fun cancellation_is_never_converted_into_a_data_error() = runTest {
        val store = FileBackedSnapshotStore(
            fileSystem = FailingFileSystem(CancellationException("lifecycle stopped")),
            json = json,
        )

        assertFailsWith<CancellationException> {
            store.load(sessionId)
        }
    }

    @Test
    fun unfinished_sessions_are_filtered_deduplicated_and_stable() = runTest {
        val fileSystem = MemoryFileSystem().apply {
            names = listOf(
                "second.snapshot.json",
                "unrelated.txt",
                ".snapshot.json",
                "first.snapshot.json",
                "second.snapshot.json",
                "partial.snapshot.json.tmp",
                "../escape.snapshot.json",
            )
        }
        val store = FileBackedSnapshotStore(fileSystem, json)

        assertEquals(
            Result.Success(listOf(SessionId("first"), SessionId("second"))),
            store.listUnfinished(),
        )
    }

    @Test
    fun valid_snapshot_round_trips_through_filesystem_boundary() = runTest {
        val fileSystem = MemoryFileSystem()
        val store = FileBackedSnapshotStore(fileSystem, json)
        val snapshot = GameSnapshot(
            sessionId = sessionId,
            gameId = GameId("whodunit"),
            engineVersion = SemVer(1, 0, 0),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            phaseId = "setup",
            payload = byteArrayOf(1, 2, 3),
        )

        assertIs<Result.Success<Unit>>(store.save(snapshot))
        val loaded = assertIs<Result.Success<GameSnapshot>>(store.load(sessionId)).data

        assertEquals(snapshot, loaded)
        assertEquals(snapshot.sessionId, loaded.sessionId)
        assertEquals(snapshot.gameId, loaded.gameId)
        assertEquals(snapshot.phaseId, loaded.phaseId)
        assertEquals(snapshot.payload.toList(), loaded.payload.toList())
    }

    @Test
    fun traversal_and_temporary_names_are_rejected() {
        listOf("../escape", "nested/file", "nested\\file", "state.tmp", "\u0000")
            .forEach { name ->
                assertFailsWith<IllegalArgumentException> {
                    requireSafeSnapshotFileName(name)
                }
            }
        requireSafeSnapshotFileName("session-123.snapshot.json")
    }

    private class FailingFileSystem(
        private val failure: Throwable,
    ) : SnapshotFileSystem {
        override suspend fun read(name: String): ByteArray? = throw failure
        override suspend fun write(name: String, bytes: ByteArray): Unit = throw failure
        override suspend fun delete(name: String): Unit = throw failure
        override suspend fun list(): List<String> = throw failure
    }

    private class MemoryFileSystem : SnapshotFileSystem {
        private val files = mutableMapOf<String, ByteArray>()
        var names: List<String>? = null

        override suspend fun read(name: String): ByteArray? = files[name]?.copyOf()

        override suspend fun write(name: String, bytes: ByteArray) {
            files[name] = bytes.copyOf()
        }

        override suspend fun delete(name: String) {
            files.remove(name)
        }

        override suspend fun list(): List<String> = names ?: files.keys.toList()
    }
}
