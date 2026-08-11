package com.parlor.app.di

import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.app.p2p.p2pBootstrapModules
import com.parlor.app.storage.platformStorageModule
import com.parlor.core.random.SessionSeedSource
import com.parlor.core.time.Clock
import com.parlor.core.time.SystemClock
import com.parlor.games.mafia.di.mafiaModule
import com.parlor.games.whodunit.di.whodunitModule
import com.parlor.networking.security.SecureIds
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Root Koin assembly for the Parlor shell. Each game module contributes its
 * own Koin module via the list below.
 *
 * Platform-specific actuals (snapshot/settings/credential backing and P2P kit
 * bootstrap) are provided by their respective Android, iOS, and Desktop Koin
 * modules.
 *
 * Persistent settings and snapshot files are bound by
 * [platformStorageModule].
 */
val coreModule: Module = module {
    single<Clock> { SystemClock }
    single<SessionSeedSource> { SecureSessionSeedSource }
    single { AppLifecycleCoordinator(get()) }
    single<CoroutineScope>(qualifier = named("multiplayerSession")) {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    single {
        ProcessMultiplayerSessionOwner(
            processScope = get(qualifier = named("multiplayerSession")),
        )
    }
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

/** Production-only entropy boundary for hidden-role and role-order fairness. */
internal object SecureSessionSeedSource : SessionSeedSource {
    override fun nextSeed(): Long = SecureIds.randomLong()
}

val allModules: List<Module> = listOf(
    coreModule,
    whodunitModule,
    mafiaModule,
    contentModule,
    storageModule,
    platformStorageModule(),
) + p2pBootstrapModules()
