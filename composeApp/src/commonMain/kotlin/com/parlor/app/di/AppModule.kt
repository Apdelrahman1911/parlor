package com.parlor.app.di

import com.parlor.app.p2p.p2pBootstrapModules
import com.parlor.app.storage.platformStorageModule
import com.parlor.core.random.RandomSource
import com.parlor.core.time.Clock
import com.parlor.core.time.SystemClock
import com.parlor.games.whodunit.di.whodunitModule
import com.parlor.storage.settings.InMemorySettingsStore
import com.parlor.storage.settings.SettingsStore
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Root Koin assembly for the Parlor shell. Each game module contributes its
 * own Koin module via the list below.
 *
 * Platform-specific actuals (HttpClient engine, SnapshotStore backing, sound
 * implementation) are provided by their respective androidMain / iosMain /
 * desktopMain Koin modules.
 *
 * `SettingsStore` is bound to [InMemorySettingsStore] as the dev-grade default.
 * Phase 6 (per `docs/PROGRESS.md`) replaces this with persistent backings per
 * platform behind the same interface.
 */
val coreModule: Module = module {
    single<Clock> { SystemClock }
    single<RandomSource> { RandomSource.system() }
    single<SettingsStore> { InMemorySettingsStore() }
    // Strict JSON for content validation: unknown fields in case payloads must
    // fail validation rather than be silently dropped (ARCHITECTURE.md §8.4).
    single<Json> {
        Json {
            ignoreUnknownKeys = false
            isLenient = false
            encodeDefaults = true
        }
    }
}

val allModules: List<Module> = listOf(
    coreModule,
    whodunitModule,
    contentModule,
    storageModule,
    platformStorageModule(),
) + p2pBootstrapModules()
