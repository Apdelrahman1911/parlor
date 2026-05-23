package com.parlor.storage.secure

import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result

/**
 * Encrypted-at-rest key/value storage. MVP can ship a plaintext-fallback
 * implementation under this same interface — when platform keystore is
 * available, the implementation wraps reads/writes with it; otherwise it
 * documents the limitation.
 *
 * Used by Phase 6 for snapshot encryption keys, session secrets, and any
 * other host-only persisted material.
 */
interface SecureStorage {
    suspend fun put(key: String, value: ByteArray): EmptyResult<DataError>
    suspend fun get(key: String): Result<ByteArray?, DataError>
    suspend fun remove(key: String): EmptyResult<DataError>
}
