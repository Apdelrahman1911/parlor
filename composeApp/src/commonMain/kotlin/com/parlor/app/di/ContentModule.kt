package com.parlor.app.di

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.datasource.CachedCaseDataSource
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.OfflineRemoteCaseDataSource
import com.parlor.content.datasource.RemoteCaseDataSource
import com.parlor.content.repository.CaseRepository
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.CaseValidator
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.registry.GameRegistry
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundled/offline content pipeline for the first production release.
 *
 * Wires:
 *  - the installed game registry;
 *  - an explicit unavailable remote source (no synthetic backend);
 *  - an in-memory cache;
 *  - the Whodunit module's bundled case source;
 *  - strict envelope and per-game payload validation;
 *  - the existing cache/remote/bundled repository contract.
 *
 * The repository sees the remote source as unavailable and therefore loads
 * the bundled case through the same validator used by any future HTTPS
 * source. This accurately represents the release's offline-only capability
 * and keeps Ktor's MockEngine out of production binaries.
 */
val contentModule: Module = module {

    single<GameRegistry> {
        DefaultGameRegistry(
            listOf(
                get<WhodunitDefinition>(),
                get<MafiaDefinition>(),
            ),
        )
    }

    single<RemoteCaseDataSource> { OfflineRemoteCaseDataSource() }

    single<CachedCaseDataSource> { InMemoryCachedCaseDataSource() }

    single<CaseValidator> {
        DefaultCaseValidator(
            json = get(),
            knownSchemaVersion = SUPPORTED_SCHEMA_VERSION,
            installedAppVersion = INSTALLED_APP_VERSION,
            gameRegistry = get(),
        )
    }

    single<CaseRepository> {
        DefaultCaseRepository(
            remote = get(),
            cache = get(),
            bundled = get(),
            validator = get(),
            json = get(),
        )
    }
}

/** Phase 8 reads this from BuildConfig; constant for now. */
private val INSTALLED_APP_VERSION: SemVer = SemVer(1, 0, 0)

/** The schema this app build understands. Bumping this requires app code changes. */
private const val SUPPORTED_SCHEMA_VERSION: Int = 1
