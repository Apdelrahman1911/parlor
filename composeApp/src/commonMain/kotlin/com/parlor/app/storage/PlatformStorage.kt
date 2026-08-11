package com.parlor.app.storage

import kotlinx.coroutines.CancellationException
import org.koin.core.module.Module

/**
 * Platform boundary for storage. Each platform's `actual` returns a Koin
 * module that binds the protected `SnapshotFileSystem`, persistent,
 * non-sensitive `SettingsStore`, and the platform device-bound
 * `SecureStorage` backing used for resumable-session credentials.
 *
 * The storage interfaces live in `:shared:storage`; only composition is
 * expected/actual. This keeps production and test callers on one abstraction
 * while each platform owns its secure implementation and lifecycle.
 */
expect fun platformStorageModule(): Module

/**
 * Best-effort eager migration for independently recoverable snapshot records.
 *
 * One corrupt legacy save must not hide every healthy resume tile. The failed
 * name remains in the returned inventory so opening it reaches the explicit
 * Retry / Discard recovery surface. Cancellation is still structural and must
 * never be converted into an individual-record failure.
 */
internal inline fun migrateSnapshotRecordsIndependently(
    names: List<String>,
    migrate: (String) -> Unit,
): List<String> {
    names.forEach { name ->
        try {
            migrate(name)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Keep the record discoverable. Its normal read path will report a
            // bounded data error and let the player retry or explicitly delete it.
        }
    }
    return names
}
