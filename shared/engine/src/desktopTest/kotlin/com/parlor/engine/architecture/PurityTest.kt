package com.parlor.engine.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertEmpty
import kotlin.test.Test

/**
 * Enforces ARCHITECTURE.md §3.3 — the engine is pure Kotlin. No DI framework,
 * no UI library, no I/O, no platform APIs.
 *
 * Konsist scans the `:shared:engine` module's sources and fails the build if
 * any source file imports from a forbidden package.
 *
 * This runs only on the JVM target ("desktopTest") because Konsist requires
 * JVM access to the source tree.
 */
class PurityTest {

    // Production-only scope: test sources legitimately depend on test libraries
    // (Konsist itself, kotlin-test). The purity rule applies to production code.
    private val engineImports = Konsist.scopeFromModule("shared/engine").files.filterNot { f ->
        val p = f.path.replace('\\', '/')
        p.contains("/src/commonTest/") ||
            p.contains("/src/desktopTest/") ||
            p.contains("/src/androidTest/") ||
            p.contains("/src/iosTest/")
    }.flatMap { it.imports }

    private val forbiddenPrefixes = listOf(
        "androidx.compose",
        "androidx.lifecycle",
        "android.",
        "io.ktor",
        "app.cash.sqldelight",
        "org.koin",
        "io.insert.koin",
        "com.parlor.designsystem",
        "com.parlor.session",
        "com.parlor.content",
        "com.parlor.networking",
        "com.parlor.storage",
        "com.parlor.navigation",
        "com.parlor.games",
    )

    @Test
    fun engine_does_not_import_forbidden_packages() {
        forbiddenPrefixes.forEach { forbidden ->
            engineImports
                .filter { it.name.startsWith(forbidden) }
                .assertEmpty(
                    additionalMessage = "Engine purity violation: import from '$forbidden' found in :shared:engine.",
                )
        }
    }
}
