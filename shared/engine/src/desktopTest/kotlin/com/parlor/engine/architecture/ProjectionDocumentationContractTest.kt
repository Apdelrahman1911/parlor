package com.parlor.engine.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Keeps projection documentation aligned with its runtime redaction boundary. */
class ProjectionDocumentationContractTest {

    @Test
    fun projection_kdocs_describe_runtime_not_compiler_enforced_redaction() {
        val projections = read(
            "shared/engine/src/commonMain/kotlin/com/parlor/engine/projection/Projections.kt",
        )
        val stateContainer = read(
            "shared/engine/src/commonMain/kotlin/com/parlor/engine/state/GameStateContainer.kt",
        )

        assertTrue("[ProjectionPolicy] enforces redaction at runtime" in projections)
        assertTrue("the compiler cannot establish redaction" in projections)
        assertTrue(
            "`ProjectionPolicy` must strip unwanted buckets per viewer at runtime" in stateContainer,
        )
        assertTrue(
            "the type system\n * does not itself prevent host-only data" in stateContainer,
        )
        assertFalse("The compiler prevents a `PublicProjection` consumer" in projections)
        assertFalse("prevents host-only data from ending up in a `PublicProjection`" in stateContainer)
    }

    @Test
    fun documentation_reads_normalize_platform_line_endings() {
        assertEquals("first\nsecond\nthird", normalizeLineEndings("first\r\nsecond\rthird"))
    }

    private fun read(path: String): String =
        normalizeLineEndings(File(findRepositoryRoot(), path).readText())

    private fun normalizeLineEndings(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')

    private fun findRepositoryRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate repository root")
    }
}
