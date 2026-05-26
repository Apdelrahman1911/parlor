package com.parlor.games.mafia.di

import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaNavGraph
import com.parlor.navigation.ModuleNavGraph
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module that registers Mafia's contributions:
 *
 *  - `ModuleNavGraph` (qualified `"mafia"`) — registry entry for the nav graph.
 *  - `MafiaDefinition` — engine-level definition consumed by `GameRegistry`.
 *
 * Mafia has no external content (no case JSON), so no payload validator or
 * bundled data source is registered.
 */
val mafiaModule = module {
    single<ModuleNavGraph>(qualifier = named("mafia")) { MafiaNavGraph() }
    single { MafiaDefinition(get()) }
}
