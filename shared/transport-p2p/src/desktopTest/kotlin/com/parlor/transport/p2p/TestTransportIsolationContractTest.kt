package com.parlor.transport.p2p

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestTransportIsolationContractTest {
    private val repositoryRoot: File = findRepositoryRoot()

    @Test
    fun `in-memory room fake is isolated from shipping source sets`() {
        val oldProductionFake = repositoryRoot.resolve(
            "shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/" +
                "InMemoryRoomTransport.kt",
        )
        val isolatedFake = repositoryRoot.resolve(
            "shared/networking-testing/src/commonMain/kotlin/com/parlor/networking/testing/" +
                "InMemoryRoomBus.kt",
        )
        assertFalse(oldProductionFake.exists())
        assertTrue(isolatedFake.isFile)

        val shippingHits = repositoryRoot.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .filter { file ->
                val path = file.relativeTo(repositoryRoot).invariantSeparatorsPath
                path.contains("/src/commonMain/") ||
                    path.contains("/src/androidMain/") ||
                    path.contains("/src/iosMain/") ||
                    path.contains("/src/desktopMain/")
            }
            .filterNot {
                it.toPath().startsWith(
                    repositoryRoot.resolve("shared/networking-testing").toPath(),
                )
            }
            .filter { file ->
                val text = file.readText()
                "com.parlor.networking.testing" in text || "InMemoryRoomBus" in text
            }
            .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
            .toList()
        assertEquals(emptyList(), shippingHits)
    }

    @Test
    fun `only test source sets depend on networking test support`() {
        val coordinate = "project(\":shared:networking-testing\")"
        val consumers = repositoryRoot.walkTopDown()
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .filter { coordinate in it.readText() }
            .toList()

        assertEquals(
            setOf(
                "game-modes/mafia/build.gradle.kts",
                "game-modes/whodunit/build.gradle.kts",
                "shared/session/build.gradle.kts",
            ),
            consumers.map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }.toSet(),
        )
        consumers.forEach { buildFile ->
            val text = buildFile.readText()
            val dependency = text.indexOf(coordinate)
            assertTrue(dependency >= 0)
            assertTrue(
                text.lastIndexOf("commonTest.dependencies", dependency) >
                    text.lastIndexOf("commonMain.dependencies", dependency),
                "${buildFile.relativeTo(repositoryRoot)} must keep the fake test-only",
            )
        }
    }

    private fun findRepositoryRoot(): File {
        var candidate = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (
                File(candidate, "settings.gradle.kts").isFile &&
                File(candidate, "shared").isDirectory
            ) {
                return candidate
            }
            candidate = candidate.parentFile ?: return@repeat
        }
        error("Unable to locate repository root")
    }
}
