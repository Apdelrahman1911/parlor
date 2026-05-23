package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotFileSystem
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformStorageModule(): Module = module {
    single<SnapshotFileSystem> { AndroidSnapshotFileSystem(androidContext()) }
}
