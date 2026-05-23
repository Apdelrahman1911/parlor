package com.parlor.app.di

import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Storage bindings (Phase 6.1).
 *
 *  - `SnapshotStore` → `FileBackedSnapshotStore(fs, json)` with `fs` provided
 *    by the per-platform `platformStorageModule()`.
 *  - Reuses the strict `Json` already bound in [coreModule] (Phase 3) so
 *    saved snapshots fail loudly if their shape drifts from the engine's.
 *
 * Phase 6.2 layers a `SnapshotEffectRunner` on top (already wired inline in
 * `WhodunitGameFlow` for now), plus the cold-start resume prompt.
 */
val storageModule: Module = module {
    single<SnapshotStore> {
        FileBackedSnapshotStore(
            fileSystem = get(),
            json = get<Json>(),
        )
    }
}
