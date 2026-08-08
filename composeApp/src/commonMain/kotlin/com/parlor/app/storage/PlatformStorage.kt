package com.parlor.app.storage

import org.koin.core.module.Module

/**
 * Platform boundary for storage. Each platform's `actual` returns a Koin
 * module that binds the protected `SnapshotFileSystem`, persistent,
 * non-sensitive `SettingsStore`, and the platform device-bound
 * `SecureStorage` backing used for resumable-session credentials.
 *
 * Chosen over an `expect class SnapshotFileSystem` because:
 *  - Per ARCHITECTURE.md, we favor "interface in commonMain + DI binding per
 *    platform" over `expect/actual` so the binding can be swapped in tests
 *    without touching production code.
 *  - The interface already exists in `:shared:storage` (`SnapshotFileSystem`).
 *    We don't introduce a parallel abstraction; we just bind it.
 */
expect fun platformStorageModule(): Module
