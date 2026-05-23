package com.parlor.games.whodunit.di

import com.parlor.games.whodunit.WhodunitNavGraph
import com.parlor.navigation.ModuleNavGraph
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Whodunit module's Koin contributions.
 *
 * Phase 2+ adds `GameDefinition`, `PayloadValidator`, and presentation-layer
 * ViewModels. Phase 1 just registers the nav-graph entry.
 */
val whodunitModule = module {
    single<ModuleNavGraph>(qualifier = named("whodunit")) { WhodunitNavGraph() }
}
