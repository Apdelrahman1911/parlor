package com.parlor.games.mafia.di

import com.parlor.games.mafia.MafiaDefinition
import org.koin.dsl.module

/**
 * Koin module that registers Mafia's contributions:
 *
 *  - `MafiaDefinition` — engine-level definition contributed to the app's
 *    shell and engine registries by the composition root.
 *
 * Mafia has no external content (no case JSON), so no payload validator or
 * bundled data source is registered.
 */
val mafiaModule = module {
    single { MafiaDefinition(get()) }
}
