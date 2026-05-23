package com.parlor.app.di

import com.parlor.core.random.RandomSource
import com.parlor.core.time.Clock
import com.parlor.core.time.SystemClock
import com.parlor.games.whodunit.di.whodunitModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Root Koin assembly for the Parlor shell. Each game module contributes its
 * own Koin module via the list below.
 *
 * Platform-specific actuals (HttpClient engine, SnapshotStore backing, sound
 * implementation) are provided by their respective androidMain / iosMain /
 * desktopMain Koin modules.
 */
val coreModule: Module = module {
    single<Clock> { SystemClock }
    single<RandomSource> { RandomSource.system() }
}

val allModules: List<Module> = listOf(
    coreModule,
    whodunitModule,
)
