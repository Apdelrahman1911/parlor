package com.parlor.storage.snapshot

import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.engine.snapshot.GameSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/**
 * File-backed snapshot store. Persists one JSON file per session under the
 * provided directory.
 *
 * Platform implementations of [SnapshotFileSystem] are responsible for
 * authenticated encryption and OS storage protection before bytes reach disk.
 * Keeping protection below this class also makes atomic replacement and
 * platform key lifecycle one indivisible concern.
 */
class FileBackedSnapshotStore private constructor(
    private val fileSystem: SnapshotFileSystem,
    private val codec: SnapshotEnvelopeCodec,
    private val serializationContext: CoroutineContext,
) : SnapshotStore {
    private val mutex = Mutex()

    /** Preserves the original public constructor for existing consumers. */
    constructor(
        fileSystem: SnapshotFileSystem,
        json: Json,
    ) : this(fileSystem, JsonSnapshotEnvelopeCodec(json), Dispatchers.Default)

    /**
     * Allows applications to own the dispatcher used for snapshot envelope
     * serialization while platform filesystems keep ownership of their I/O
     * dispatcher.
     */
    constructor(
        fileSystem: SnapshotFileSystem,
        json: Json,
        serializationContext: CoroutineContext,
    ) : this(fileSystem, JsonSnapshotEnvelopeCodec(json), serializationContext)

    /** Deterministic codec seam used by common tests. */
    internal constructor(
        fileSystem: SnapshotFileSystem,
        serializationContext: CoroutineContext,
        codec: SnapshotEnvelopeCodec,
    ) : this(fileSystem, codec, serializationContext)

    override suspend fun save(snapshot: GameSnapshot): EmptyResult<DataError> = mutex.withLock {
        try {
            val payload = withContext(serializationContext) {
                codec.encode(snapshot)
            }
            fileSystem.write(fileName(snapshot.sessionId), payload)
            EmptyOk
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.Failure(mapToDataError(failure))
        }
    }

    override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> = mutex.withLock {
        try {
            val bytes = fileSystem.read(fileName(sessionId))
                ?: return@withLock Result.Failure(DataError.NotFound)
            Result.Success(
                withContext(serializationContext) {
                    codec.decode(bytes)
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.Failure(mapToDataError(failure))
        }
    }

    override suspend fun delete(sessionId: SessionId): EmptyResult<DataError> = mutex.withLock {
        try {
            fileSystem.delete(fileName(sessionId))
            EmptyOk
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.Failure(mapToDataError(failure))
        }
    }

    override suspend fun listUnfinished(): Result<List<SessionId>, DataError> = mutex.withLock {
        try {
            val names = fileSystem.list()
            Result.Success(
                withContext(serializationContext) {
                    names
                        .asSequence()
                        .filter(::isSafeSnapshotFileName)
                        .filter { it.endsWith(SUFFIX) }
                        .map { it.removeSuffix(SUFFIX) }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                        .map(::SessionId)
                        .toList()
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.Failure(mapToDataError(failure))
        }
    }

    private fun fileName(sessionId: SessionId) = "${sessionId.raw}$SUFFIX"

    private fun mapToDataError(t: Exception): DataError = when (t) {
        is SerializationException,
        is SnapshotProtectionException -> DataError.CorruptedData
        // Do not copy exception text into a domain error: platform exceptions
        // commonly contain private container paths or key aliases.
        else -> DataError.IoError("snapshot_io")
    }

    companion object {
        const val SUFFIX = ".snapshot.json"
    }
}

internal interface SnapshotEnvelopeCodec {
    suspend fun encode(snapshot: GameSnapshot): ByteArray
    suspend fun decode(bytes: ByteArray): GameSnapshot
}

private class JsonSnapshotEnvelopeCodec(
    private val json: Json,
) : SnapshotEnvelopeCodec {
    override suspend fun encode(snapshot: GameSnapshot): ByteArray =
        json
            .encodeToString(GameSnapshot.serializer(), snapshot)
            .encodeToByteArray()

    override suspend fun decode(bytes: ByteArray): GameSnapshot =
        json.decodeFromString(GameSnapshot.serializer(), bytes.decodeToString())
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

/**
 * Signals that protected snapshot bytes failed format or authenticity checks.
 * The store maps this to [DataError.CorruptedData] without exposing crypto or
 * filesystem details to the UI.
 */
class SnapshotProtectionException(
    message: String = "snapshot protection failed",
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Rejects traversal and temporary-file names at every platform boundary.
 *
 * Session ids are generated by Parlor today, but treating a future restored or
 * network-provided id as a safe path component would be an unnecessary risk.
 */
fun requireSafeSnapshotFileName(name: String) {
    require(isSafeSnapshotFileName(name)) {
        "Invalid snapshot file name"
    }
}

fun isSafeSnapshotFileName(name: String): Boolean =
    name.isNotBlank() &&
        name.length <= MAX_SNAPSHOT_FILE_NAME_LENGTH &&
        name != "." &&
        name != ".." &&
        !name.endsWith(".tmp") &&
        name.none { it == '/' || it == '\\' || it == '\u0000' }

private const val MAX_SNAPSHOT_FILE_NAME_LENGTH = 240
