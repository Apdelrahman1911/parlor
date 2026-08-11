package com.parlor.app.di

import com.parlor.core.random.SessionSeedSource
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertSame

class ProductionSessionSeedSourceTest {
    @Test
    fun application_binds_fresh_game_seeds_to_the_platform_csprng_boundary() {
        val application = koinApplication { modules(coreModule) }
        try {
            assertSame(
                SecureSessionSeedSource,
                application.koin.get<SessionSeedSource>(),
            )
        } finally {
            application.close()
        }
    }
}
