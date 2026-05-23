package com.parlor.app.storage

import org.koin.core.module.Module

/**
 * Platform boundary for storage. Each platform's `actual` returns a Koin
 * module that binds the platform-specific `SnapshotFileSystem` (and any other
 * platform-only storage backings added later).
 *
 * Chosen over an `expect class SnapshotFileSystem` because:
 *  - Per ARCHITECTURE.md, we favor "interface in commonMain + DI binding per
 *    platform" over `expect/actual` so the binding can be swapped in tests
 *    without touching production code.
 *  - The interface already exists in `:shared:storage` (`SnapshotFileSystem`).
 *    We don't introduce a parallel abstraction; we just bind it.
 */
expect fun platformStorageModule(): Module
