package com.parlor.storage.secure

import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result

/**
 * Encrypted-at-rest key/value storage contract.
 *
 * A production binding must use a platform-backed, non-exporting or
 * device-bound protection mechanism. Plaintext fallback is test/development
 * behavior only and must never be selected silently.
 *
 * Used for snapshot encryption keys, resumable-session material, and other
 * host-only persisted values. Snapshot files use dedicated platform key
 * management because atomic replacement and key lifecycle are one platform
 * concern.
 */
interface SecureStorage {
    suspend fun put(key: String, value: ByteArray): EmptyResult<DataError>
    suspend fun get(key: String): Result<ByteArray?, DataError>
    suspend fun remove(key: String): EmptyResult<DataError>
}
