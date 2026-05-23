package com.parlor.storage.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.engine.snapshot.GameSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * File-backed snapshot store. Persists one JSON file per session under the
 * provided directory.
 *
 * Phase 6 ships this implementation in commonMain delegating actual filesystem
 * operations to an injected [SnapshotFileSystem] — platform actuals provide
 * the concrete read/write/list/delete. Encryption-at-rest is added in a thin
 * wrapper around the filesystem (Android Keystore, iOS Keychain, Desktop
 * derived key) without changing this class.
 */
class FileBackedSnapshotStore(
    private val fileSystem: SnapshotFileSystem,
    private val json: Json,
) : SnapshotStore {
    private val mutex = Mutex()

    override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> = mutex.withLock {
        runCatching {
            val payload = json.encodeToString(GameSnapshot.serializer(), snapshot)
            fileSystem.write(fileName(snapshot.sessionId), payload.encodeToByteArray())
        }.fold(
            onSuccess = { EmptyOk },
            onFailure = { mapToDataError(it).let { e -> Result.Failure(e) } },
        )
    }

    override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> = mutex.withLock {
        runCatching {
            val bytes = fileSystem.read(fileName(sessionId))
                ?: return@runCatching null
            json.decodeFromString(GameSnapshot.serializer(), bytes.decodeToString())
        }.fold(
            onSuccess = { snapshot ->
                if (snapshot == null) Result.Failure(DataError.NotFound)
                else Result.Success(snapshot)
            },
            onFailure = { Result.Failure(mapToDataError(it)) },
        )
    }

    override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> = mutex.withLock {
        runCatching { fileSystem.delete(fileName(sessionId)) }.fold(
            onSuccess = { EmptyOk },
            onFailure = { Result.Failure(mapToDataError(it)) },
        )
    }

    override suspend fun listUnfinished(): Result<List<SessionId>, DataError> = mutex.withLock {
        runCatching { fileSystem.list() }.fold(
            onSuccess = { fileNames ->
                Result.Success(
                    fileNames
                        .filter { it.endsWith(SUFFIX) }
                        .map { SessionId(it.removeSuffix(SUFFIX)) },
                )
            },
            onFailure = { Result.Failure(mapToDataError(it)) },
        )
    }

    private fun fileName(sessionId: SessionId) = "${sessionId.raw}$SUFFIX"

    private fun mapToDataError(t: Throwable): DataError = when (t) {
        is kotlinx.serialization.SerializationException -> DataError.CorruptedData
        else -> DataError.IoError(t.message ?: "io")
    }

    companion object {
        const val SUFFIX = ".snapshot.json"
    }
}

/**
 * Platform-agnostic snapshot filesystem contract. Platform `actual`s (Android
 * `Context.filesDir`, iOS `NSDocumentDirectory`, Desktop user-config dir)
 * implement reads/writes against the chosen directory.
 */
interface SnapshotFileSystem {
    suspend fun read(name: String): ByteArray?
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun delete(name: String)
    suspend fun list(): List<String>
}
