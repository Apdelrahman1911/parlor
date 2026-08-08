package com.parlor.app.storage

import com.parlor.storage.settings.PersistentSettingsStore
import com.parlor.storage.settings.SettingsKeyValueBacking
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.secure.PlatformKeyedSecureStorage
import com.parlor.storage.secure.SecureKeyValueBacking
import com.parlor.storage.secure.SecureStorage
import com.parlor.storage.snapshot.SnapshotFileSystem
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformStorageModule(): Module = module {
    single<SnapshotFileSystem> { AndroidSnapshotFileSystem(androidContext()) }
    single<SettingsKeyValueBacking> { AndroidSettingsKeyValueBacking(androidContext()) }
    single<SettingsStore> { PersistentSettingsStore(get()) }
    single<SecureKeyValueBacking> { AndroidSecureKeyValueBacking(androidContext()) }
    single<SecureStorage> { PlatformKeyedSecureStorage(get()) }
}
