package com.parlor.games.whodunit.di

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.validation.PayloadValidator
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.content.BundledWhodunitCases
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.resources.Res
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Whodunit module's Koin contributions:
 *
 *  - `WhodunitDefinition` — the engine-level definition. The app-level game
 *    shell binding contributes it to both shell and engine registries.
 *  - `PayloadValidator<WhodunitCase>` — strict payload validation per
 *    docs/CONTENT_SCHEMA.md §3.5.
 *  - `BundledFallbackCaseDataSource` — bundled case JSON via Compose
 *    Multiplatform shared resources (`composeResources/files/cases/`). No
 *    qualifier; the repository consumes a single bundled source for MVP.
 */
@OptIn(ExperimentalResourceApi::class)
val whodunitModule = module {
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
                try {
                    Res.readBytes("files/cases/$caseId.json").decodeToString()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            },
            json = get(),
        )
    }
}
