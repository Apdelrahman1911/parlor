package com.parlor.storage.snapshot

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.snapshot.GameSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

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
    fun fatal_errors_are_never_converted_into_a_data_error() = runTest {
        val store = FileBackedSnapshotStore(
            fileSystem = FailingFileSystem(FatalSnapshotError()),
            json = json,
        )

        assertFailsWith<FatalSnapshotError> {
            store.load(sessionId)
        }
    }

    @Test
    fun envelope_codec_runs_on_injected_context_and_filesystem_stays_outside_it() = runTest {
        val marker = SnapshotCodecContext()
        val codec = ContextCheckingCodec(json, marker)
        val fileSystem = MemoryFileSystem(marker)
        val store = FileBackedSnapshotStore(
            fileSystem = fileSystem,
            serializationContext = StandardTestDispatcher(testScheduler) + marker,
            codec = codec,
        )
        val snapshot = validSnapshot()

        assertIs<Result.Success<Unit>>(store.save(snapshot))
        val loaded = assertIs<Result.Success<GameSnapshot>>(store.load(sessionId)).data

        assertEquals(snapshot, loaded)
        assertTrue(codec.encodeContextObserved)
        assertTrue(codec.decodeContextObserved)
        assertTrue(fileSystem.operations > 0)
    }

    @Test
    fun cancellation_during_codec_work_propagates_and_releases_store_mutex() = runTest {
        val marker = SnapshotCodecContext()
        val enteredDecode = CompletableDeferred<Unit>()
        val snapshot = validSnapshot()
        var decodeAttempts = 0
        val codec = object : SnapshotEnvelopeCodec {
            override suspend fun encode(snapshot: GameSnapshot): ByteArray = byteArrayOf(1)

            override suspend fun decode(bytes: ByteArray): GameSnapshot {
                assertSame(marker, currentCoroutineContext()[SnapshotCodecContext.Key])
                decodeAttempts += 1
                if (decodeAttempts == 1) {
                    enteredDecode.complete(Unit)
                    awaitCancellation()
                }
                return snapshot
            }
        }
        val fileSystem = MemoryFileSystem().apply {
            put("${sessionId.raw}${FileBackedSnapshotStore.SUFFIX}", byteArrayOf(1))
        }
        val store = FileBackedSnapshotStore(
            fileSystem = fileSystem,
            serializationContext = StandardTestDispatcher(testScheduler) + marker,
            codec = codec,
        )

        val loading = async { store.load(sessionId) }
        enteredDecode.await()
        loading.cancel(CancellationException("screen disposed"))

        assertFailsWith<CancellationException> { loading.await() }
        assertIs<Result.Success<GameSnapshot>>(store.load(sessionId))
        assertEquals(2, decodeAttempts)
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
        val snapshot = validSnapshot()

        assertIs<Result.Success<Unit>>(store.save(snapshot))
        val loaded = assertIs<Result.Success<GameSnapshot>>(store.load(sessionId)).data

        assertEquals(snapshot, loaded)
        assertEquals(snapshot.sessionId, loaded.sessionId)
        assertEquals(snapshot.gameId, loaded.gameId)
        assertEquals(snapshot.phaseId, loaded.phaseId)
        assertEquals(snapshot.payload.toList(), loaded.payload.toList())
        assertEquals(
            Result.Success(SnapshotMetadata(sessionId, GameId("whodunit"))),
            store.loadMetadata(sessionId),
        )
    }

    @Test
    fun metadata_read_uses_the_header_decoder_instead_of_full_snapshot_decoder() = runTest {
        val codec = object : SnapshotEnvelopeCodec {
            var metadataDecodeCalls = 0

            override suspend fun encode(snapshot: GameSnapshot): ByteArray = byteArrayOf(1)

            override suspend fun decode(bytes: ByteArray): GameSnapshot =
                error("metadata reads must not decode the complete snapshot")

            override suspend fun decodeMetadata(bytes: ByteArray): SnapshotMetadata {
                metadataDecodeCalls += 1
                return SnapshotMetadata(sessionId, GameId("mafia"))
            }
        }
        val fileSystem = MemoryFileSystem().apply {
            put("${sessionId.raw}${FileBackedSnapshotStore.SUFFIX}", byteArrayOf(1))
        }
        val store = FileBackedSnapshotStore(
            fileSystem = fileSystem,
            serializationContext = StandardTestDispatcher(testScheduler),
            codec = codec,
        )

        assertEquals(
            Result.Success(SnapshotMetadata(sessionId, GameId("mafia"))),
            store.loadMetadata(sessionId),
        )
        assertEquals(1, codec.metadataDecodeCalls)
    }

    @Test
    fun malformed_utf8_snapshot_is_rejected_as_corrupted() = runTest {
        val fileSystem = MemoryFileSystem().apply {
            put(
                "${sessionId.raw}${FileBackedSnapshotStore.SUFFIX}",
                byteArrayOf('{'.code.toByte(), 0xC3.toByte(), '}'.code.toByte()),
            )
        }
        val store = FileBackedSnapshotStore(fileSystem, json)

        assertEquals(
            Result.Failure(DataError.CorruptedData),
            store.load(sessionId),
        )
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

    private fun validSnapshot() = GameSnapshot(
        sessionId = sessionId,
        gameId = GameId("whodunit"),
        engineVersion = SemVer(1, 0, 0),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        phaseId = "setup",
        payload = byteArrayOf(1, 2, 3),
    )

    private class SnapshotCodecContext : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*> = Key

        companion object Key : CoroutineContext.Key<SnapshotCodecContext>
    }

    private class ContextCheckingCodec(
        private val json: Json,
        private val expectedContext: SnapshotCodecContext,
    ) : SnapshotEnvelopeCodec {
        var encodeContextObserved = false
            private set
        var decodeContextObserved = false
            private set

        override suspend fun encode(snapshot: GameSnapshot): ByteArray {
            assertSame(
                expectedContext,
                currentCoroutineContext()[SnapshotCodecContext.Key],
            )
            encodeContextObserved = true
            return json
                .encodeToString(GameSnapshot.serializer(), snapshot)
                .encodeToByteArray()
        }

        override suspend fun decode(bytes: ByteArray): GameSnapshot {
            assertSame(
                expectedContext,
                currentCoroutineContext()[SnapshotCodecContext.Key],
            )
            decodeContextObserved = true
            return json.decodeFromString(GameSnapshot.serializer(), bytes.decodeToString())
        }
    }

    private class FailingFileSystem(
        private val failure: Throwable,
    ) : SnapshotFileSystem {
        override suspend fun read(name: String): ByteArray? = throw failure
        override suspend fun write(name: String, bytes: ByteArray): Unit = throw failure
        override suspend fun delete(name: String): Unit = throw failure
        override suspend fun list(): List<String> = throw failure
    }

    private class MemoryFileSystem(
        private val forbiddenContext: SnapshotCodecContext? = null,
    ) : SnapshotFileSystem {
        private val files = mutableMapOf<String, ByteArray>()
        var names: List<String>? = null
        var operations: Int = 0
            private set

        fun put(name: String, bytes: ByteArray) {
            files[name] = bytes.copyOf()
        }

        private suspend fun recordOperation() {
            operations += 1
            forbiddenContext?.let {
                assertNull(currentCoroutineContext()[SnapshotCodecContext.Key])
            }
        }

        override suspend fun read(name: String): ByteArray? {
            recordOperation()
            return files[name]?.copyOf()
        }

        override suspend fun write(name: String, bytes: ByteArray) {
            recordOperation()
            files[name] = bytes.copyOf()
        }

        override suspend fun delete(name: String) {
            recordOperation()
            files.remove(name)
        }

        override suspend fun list(): List<String> {
            recordOperation()
            return names ?: files.keys.toList()
        }
    }

    private class FatalSnapshotError : Error("fatal snapshot failure")
}
