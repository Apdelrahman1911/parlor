package com.parlor.app.di

import com.parlor.storage.snapshot.FileBackedSnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Shared snapshot-store binding.
 *
 * The platform module supplies the protected file-system implementation and
 * [coreModule] supplies the strict [Json] instance. Game-owned snapshot codecs
 * and writers validate and persist through this common [SnapshotStore].
 */
val storageModule: Module = module {
    single<SnapshotStore> {
        FileBackedSnapshotStore(
            fileSystem = get(),
            json = get<Json>(),
        )
    }
}
