package com.parlor.storage.secure

import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [SecureStorage] adapter backed by a caller-provided
 * [SecureKeyValueBacking]. Security is only as strong as that backing; DI must
 * not bind [InMemorySecureKeyValueBacking] in production.
 */
class PlatformKeyedSecureStorage(
    private val backing: SecureKeyValueBacking,
) : SecureStorage {
    private val mutex = Mutex()

    override suspend fun put(key: String, value: ByteArray): EmptyResult<DataError> = mutex.withLock {
        try {
            backing.put(key, value)
            EmptyOk
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            Result.Failure(DataError.IoError("secure_storage_io"))
        }
    }

    override suspend fun get(key: String): Result<ByteArray?, DataError> = mutex.withLock {
        try {
            Result.Success(backing.get(key))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            Result.Failure(DataError.IoError("secure_storage_io"))
        }
    }

    override suspend fun remove(key: String): EmptyResult<DataError> = mutex.withLock {
        try {
            backing.remove(key)
            EmptyOk
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            Result.Failure(DataError.IoError("secure_storage_io"))
        }
    }
}

/** Platform-supplied secure key-value backing. */
interface SecureKeyValueBacking {
    suspend fun put(key: String, value: ByteArray)
    suspend fun get(key: String): ByteArray?
    suspend fun remove(key: String)
}

/**
 * Dev/test in-memory backing — plaintext, but uses the production interface so
 * code paths are identical.
 */
class InMemorySecureKeyValueBacking : SecureKeyValueBacking {
    private val map = mutableMapOf<String, ByteArray>()
    private val mutex = Mutex()

    override suspend fun put(key: String, value: ByteArray) {
        mutex.withLock {
            map.put(key, value.copyOf())?.fill(0)
        }
    }

    override suspend fun get(key: String): ByteArray? =
        mutex.withLock { map[key]?.copyOf() }

    override suspend fun remove(key: String) {
        mutex.withLock {
            map.remove(key)?.fill(0)
        }
    }
}
