package com.parlor.storage.secure

import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyOk
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Production [SecureStorage] backed by a platform-provided [SecureKeyValueBacking].
 * The backing is supplied per platform: Android Keystore-backed
 * EncryptedSharedPreferences, iOS Keychain, Desktop a derived-key-encrypted
 * file. The contract is the same.
 */
class PlatformKeyedSecureStorage(
    private val backing: SecureKeyValueBacking,
) : SecureStorage {
    private val mutex = Mutex()

    override suspend fun put(key: String, value: ByteArray): EmptyResult<DataError> = mutex.withLock {
        runCatching { backing.put(key, value) }
            .fold(onSuccess = { EmptyOk }, onFailure = { Result.Failure(DataError.IoError(it.message ?: "io")) })
    }

    override suspend fun get(key: String): Result<ByteArray?, DataError> = mutex.withLock {
        runCatching { backing.get(key) }
            .fold(
                onSuccess = { Result.Success(it) },
                onFailure = { Result.Failure(DataError.IoError(it.message ?: "io")) },
            )
    }

    override suspend fun remove(key: String): EmptyResult<DataError> = mutex.withLock {
        runCatching { backing.remove(key) }
            .fold(onSuccess = { EmptyOk }, onFailure = { Result.Failure(DataError.IoError(it.message ?: "io")) })
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
    override suspend fun put(key: String, value: ByteArray) { map[key] = value }
    override suspend fun get(key: String): ByteArray? = map[key]
    override suspend fun remove(key: String) { map.remove(key) }
}
