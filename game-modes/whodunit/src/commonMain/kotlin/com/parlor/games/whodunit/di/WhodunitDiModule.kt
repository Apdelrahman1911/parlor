package com.parlor.games.whodunit.di

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.validation.PayloadValidator
import com.parlor.games.whodunit.WhodunitNavGraph
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.resources.Res
import com.parlor.navigation.ModuleNavGraph
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Whodunit module's Koin contributions.
 *
 * - Registers the nav-graph entry.
 * - Provides the bundled-fallback data source, sourced from Compose
 *   Multiplatform shared resources (one JSON file per case, under
 *   `composeResources/files/cases/`). One source of truth across platforms.
 * - Provides the Whodunit-specific payload validator.
 *
 * Phase 4+ adds the `WhodunitDefinition` binding here as well.
 */
@OptIn(ExperimentalResourceApi::class)
val whodunitModule = module {
    single<ModuleNavGraph>(qualifier = named("whodunit")) { WhodunitNavGraph() }

    single<PayloadValidator<WhodunitCase>>(qualifier = named("whodunit")) {
        WhodunitPayloadValidator(get())
    }

    single<BundledFallbackCaseDataSource>(qualifier = named("whodunit")) {
        BundledWhodunitCases(
            knownCaseIds = listOf("last-dinner"),
            loadJson = { caseId ->
                runCatching {
                    Res.readBytes("files/cases/$caseId.json").decodeToString()
                }.getOrNull()
            },
            json = get(),
        )
    }
}
