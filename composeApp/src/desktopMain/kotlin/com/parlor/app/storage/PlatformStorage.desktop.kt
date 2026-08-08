package com.parlor.app.storage

import com.parlor.storage.settings.PersistentSettingsStore
import com.parlor.storage.settings.SettingsKeyValueBacking
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.SnapshotFileSystem
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformStorageModule(): Module = module {
    single<SnapshotFileSystem> { DesktopSnapshotFileSystem() }
    single<SettingsKeyValueBacking> { DesktopSettingsKeyValueBacking() }
    single<SettingsStore> { PersistentSettingsStore(get()) }
}
