package com.parlor.games.whodunit.di

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.validation.PayloadValidator
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitNavGraph
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.resources.Res
import com.parlor.navigation.ModuleNavGraph
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Whodunit module's Koin contributions:
 *
 *  - `ModuleNavGraph` — registry entry for the nav graph.
 *  - `WhodunitDefinition` — the engine-level definition. Consumed by
 *    `GameRegistry` in `contentModule`.
 *  - `PayloadValidator<WhodunitCase>` — strict payload validation per
 *    docs/CONTENT_SCHEMA.md §3.5.
 *  - `BundledFallbackCaseDataSource` — bundled case JSON via Compose
 *    Multiplatform shared resources (`composeResources/files/cases/`). No
 *    qualifier; the repository consumes a single bundled source for MVP.
 */
@OptIn(ExperimentalResourceApi::class)
val whodunitModule = module {
    single<ModuleNavGraph>(qualifier = named("whodunit")) { WhodunitNavGraph() }

    single { WhodunitDefinition(get()) }

    single<PayloadValidator<WhodunitCase>>(qualifier = named("whodunit")) {
        WhodunitPayloadValidator(get())
    }

    single<BundledFallbackCaseDataSource> {
        BundledWhodunitCases(
            knownCaseIds = listOf(
                "last-dinner",
                "layla-halabi",
                "jasmine-ring",
                "khan-el-khalili",
                "iskenderia-corniche",
                "zamalek-ramadan",
                "saidi-inheritance",
            ),
            loadJson = { caseId ->
                runCatching {
                    Res.readBytes("files/cases/$caseId.json").decodeToString()
                }.getOrNull()
            },
            json = get(),
        )
    }
}
