package com.parlor.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Complements session-layer race tests by guarding the two shipping Compose
 * lobbies against returning to ignored operation results or duplicate taps.
 */
class HostLobbyOperationFeedbackContractTest {
    private val root: File by lazy(::findProjectRoot)

    @Test
    fun bothShippingLobbiesObserveFailuresAndGuardOperationsInFlight() {
        val sources = listOf(
            read(
                "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/" +
                    "multiplayer/" +
                    "WhodunitHostSessionFlow.kt",
            ),
            read(
                "game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/" +
                    "multidevice/MafiaHostLobbyFlow.kt",
            ),
        )

        sources.forEach { source ->
            assertContains(source, "startInFlight = true")
            assertContains(source, "freezeAdmissions()")
            assertContains(source, "val blocked = frozen.error")
            assertContains(source, "admissionsInFlight += admission.playerId")
            assertContains(source, "approved is Result.Failure")
            assertContains(source, "rejected is Result.Failure")
            assertContains(source, "enabled = admission.playerId !in admissionsInFlight")
            assertFalse(source.contains("scope.launch { room.approveAdmission"))
            assertFalse(source.contains("scope.launch { room.rejectAdmission"))
        }
    }

    private fun read(path: String): String = root.resolve(path).readText()

    private fun findProjectRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate project root")
    }
}
