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
 * Used by Phase 6 for snapshot encryption keys, session secrets, and any
 * other host-only persisted material. Snapshot files currently use dedicated
 * platform key management directly because their atomic file replacement and
 * key lifecycle are one platform concern.
 */
interface SecureStorage {
    suspend fun put(key: String, value: ByteArray): EmptyResult<DataError>
    suspend fun get(key: String): Result<ByteArray?, DataError>
    suspend fun remove(key: String): EmptyResult<DataError>
}
