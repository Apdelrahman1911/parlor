package com.parlor.app.storage

import com.parlor.storage.settings.PersistentSettingsStore
import com.parlor.storage.settings.SettingsKeyValueBacking
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.secure.InMemorySecureKeyValueBacking
import com.parlor.storage.secure.PlatformKeyedSecureStorage
import com.parlor.storage.secure.SecureKeyValueBacking
import com.parlor.storage.secure.SecureStorage
import com.parlor.storage.snapshot.SnapshotFileSystem
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformStorageModule(): Module = module {
    single<SnapshotFileSystem> { DesktopSnapshotFileSystem() }
    single<SettingsKeyValueBacking> { DesktopSettingsKeyValueBacking() }
    single<SettingsStore> { PersistentSettingsStore(get()) }
    // Desktop is a development harness, not a shipping target. Resumable
    // credentials intentionally die with the process until an approved OS
    // credential-vault integration exists.
    single<SecureKeyValueBacking> { InMemorySecureKeyValueBacking() }
    single<SecureStorage> { PlatformKeyedSecureStorage(get()) }
}
