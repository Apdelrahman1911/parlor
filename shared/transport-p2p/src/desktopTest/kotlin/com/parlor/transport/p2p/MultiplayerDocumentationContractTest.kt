package com.parlor.transport.p2p

import com.parlor.networking.protocol.PARLOR_PROTOCOL_MAJOR
import com.parlor.networking.protocol.PARLOR_PROTOCOL_MINOR
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prevents operational multiplayer documentation from drifting back to the
 * pre-remediation protocol, timeout, permission, or rejoin contracts.
 *
 * Historical documents may retain old designs, but they must be labelled
 * before a reader reaches those details.
 */
class MultiplayerDocumentationContractTest {

    private val repositoryRoot: File by lazy(::locateRepositoryRoot)
    private val p2pKitVersion: String by lazy {
        val match = Regex("(?m)^p2pkit = \"([^\"]+)\"$")
            .find(read("gradle/libs.versions.toml"))
        assertNotNull(match, "The version catalog must declare p2pkit").groupValues[1]
    }

    @Test
    fun documentation_reader_normalizes_platform_line_endings() {
        assertEquals(
            "first\nsecond\nthird",
            normalizeDocumentationLineEndings("first\r\nsecond\rthird"),
        )
    }

    @Test
    fun operational_docs_track_runtime_protocol_and_reliable_start_contract() {
        val runtimeVersion = "$PARLOR_PROTOCOL_MAJOR.$PARLOR_PROTOCOL_MINOR"
        val currentDocuments = listOf(
            "docs/P2P_MANUAL_TEST.md",
            "docs/P2P_REMEDIATION_STATUS.md",
            "docs/PRODUCTION_ARCHITECTURE.md",
        )
        val marker = "Runtime protocol: `$runtimeVersion`."

        currentDocuments.forEach { path ->
            assertTrue(
                read(path).contains(marker),
                "$path must derive its current wire-version claim from $marker",
            )
        }

        val reliableStartDocuments = listOf(
            "docs/P2P_MANUAL_TEST.md",
            "docs/PRODUCTION_ARCHITECTURE.md",
        )
        val requiredStartContract = listOf(
            "SessionStarting",
            "SessionStartReady",
            "SessionStartCommitted",
            "SessionStartCommitAck",
            "stable `startId`",
            "Only `SessionStartCommitted` authorizes gameplay",
        )
        reliableStartDocuments.forEach { path ->
            // Markdown line wrapping is presentation, not a contract change.
            // Normalize whitespace while retaining the exact protocol terms.
            val text = read(path).replace(Regex("\\s+"), " ")
            requiredStartContract.forEach { required ->
                assertTrue(
                    text.contains(required),
                    "$path must document protocol-$runtimeVersion start behavior: $required",
                )
            }
        }

        val resumedSequenceContract = listOf(
            "PlayerSnapshot",
            "nextExpectedClientSequence",
            "atomically",
            "resumed peer",
        )
        currentDocuments.forEach { path ->
            val text = read(path).replace(Regex("\\s+"), " ")
            resumedSequenceContract.forEach { required ->
                assertTrue(
                    text.contains(required),
                    "$path must document protocol-$runtimeVersion resume sequencing: $required",
                )
            }
        }

        val historicalBanner = read("ARCHITECTURE.md")
            .take(1_000)
            .replace(">", "")
            .replace(Regex("\\s+"), " ")
        assertTrue(
            historicalBanner.contains("strict Parlor $runtimeVersion protocol"),
            "ARCHITECTURE.md's current-truth banner must name runtime protocol $runtimeVersion",
        )
    }

    @Test
    fun canonical_manual_runbook_matches_the_runtime_contract() {
        val runbook = read("docs/P2P_MANUAL_TEST.md")

        listOf(
            "Document status: current release-gate procedure.",
            "30 seconds",
            "60 seconds",
            "120 seconds",
            "strict CBOR",
            "Tapping **Leave** is final",
            "process death preserves",
            "`MAN-00` is **N/A",
            "P2pKit $p2pKitVersion",
        ).forEach { required ->
            assertTrue(
                runbook.contains(required),
                "P2P_MANUAL_TEST.md must retain current contract marker: $required",
            )
        }
    }

    @Test
    fun contributor_guidance_matches_the_current_build_and_module_graph() {
        val catalog = read("gradle/libs.versions.toml")
        fun version(key: String): String {
            val match = Regex("(?m)^$key = \"([^\"]+)\"$").find(catalog)
            return assertNotNull(match, "Missing version-catalog key: $key").groupValues[1]
        }

        val guidance = read("CLAUDE.md")
        val readme = read("README.md")
        val wrapper = read("gradle/wrapper/gradle-wrapper.properties")
        val wrapperVersion = assertNotNull(
            Regex("gradle-([^-]+)-bin\\.zip").find(wrapper),
            "Gradle wrapper URL must identify the pinned distribution",
        ).groupValues[1]

        listOf(
            "checked-in Gradle $wrapperVersion wrapper",
            "compileSdk = ${version("android-compile-sdk")}",
            "targetSdk = ${version("android-target-sdk")}",
            "minSdk = ${version("android-min-sdk")}",
            "P2pKit $p2pKitVersion",
            "minimum iOS 16",
            "Production uses\n`OfflineRemoteCaseDataSource`",
        ).forEach { marker ->
            assertTrue(marker in guidance, "CLAUDE.md is missing current build marker: $marker")
        }

        listOf(guidance, readme).forEach { currentGuide ->
            assertFalse("shared/navigation" in currentGuide)
            assertFalse("future local-multiplayer" in currentGuide)
            assertFalse("Gradle 8.11.1" in currentGuide)
            assertFalse("compileSdk = 35" in currentGuide)
        }
        assertTrue("Android and iOS are shipping targets" in guidance)
        assertTrue("Desktop is a development and deterministic" in guidance)
    }

    @Test
    fun local_snapshot_inventory_matches_both_shipping_games() {
        val architecture = read("docs/PRODUCTION_ARCHITECTURE.md")
        val privacy = read("docs/PRIVACY_AND_COMPLIANCE.md")

        assertTrue(
            "Canonical pass-and-play\nresume snapshots for both shipping games" in architecture,
            "Architecture documentation must inventory both shipping games' protected snapshots",
        )
        listOf("MafiaSnapshotRecovery.kt", "WhodunitGameFlow.kt").forEach { recoveryGate ->
            assertTrue(
                recoveryGate in architecture,
                "Architecture documentation must name $recoveryGate as a snapshot recovery gate",
            )
        }
        assertFalse("Mafia currently does not write a pass-and-play cold-start snapshot" in architecture)
        assertTrue("Enables play and optional local resume when a game supplies a snapshot adapter" in privacy)
        assertFalse("(currently for Whodunit)" in privacy)
    }

    @Test
    fun current_operational_docs_do_not_repeat_obsolete_positive_claims() {
        val canonicalDocuments = listOf(
            "README.md",
            "docs/IOS_SETUP.md",
            "docs/MULTIPLAYER_PLAYTEST.md",
            "docs/P2P_MANUAL_TEST.md",
            "docs/P2P_REMEDIATION_STATUS.md",
            "docs/PRIVACY_AND_COMPLIANCE.md",
            "docs/PRODUCTION_ARCHITECTURE.md",
            "docs/RELEASE_GATES.md",
            "docs/RELEASE_RUNBOOK.md",
        )
        val currentText = canonicalDocuments.joinToString(separator = "\n") { read(it) }

        listOf(
            "Protocol 3.1",
            "strict Parlor 3.1",
            "shipping multiplayer uses the strict Parlor 3.1",
        )
            .forEach { obsoleteProtocolClaim ->
                assertFalse(
                    currentText.contains(obsoleteProtocolClaim, ignoreCase = true),
                    "Current multiplayer docs contain obsolete protocol claim: $obsoleteProtocolClaim",
                )
            }

        listOf(
            "iOS reports `Granted` immediately",
            "Multipeer / Bluetooth",
            "P2pMessage.Binary(json bytes)",
            "join: TIMEOUT after 10000ms",
            "logs print host/join/freshness/session-state lines with peer ids and room codes",
        ).forEach { obsoleteClaim ->
            assertFalse(
                currentText.contains(obsoleteClaim, ignoreCase = true),
                "Current multiplayer docs contain obsolete claim: $obsoleteClaim",
            )
        }
    }

    @Test
    fun historical_documents_warn_before_preserving_old_behavior() {
        val historicalDocuments = listOf(
            "ARCHITECTURE.md",
            "docs/DESIGN_TOKENS.md",
            "docs/FR_REMEDIATION_FINDINGS.md",
            "docs/MOCK_BACKEND.md",
            "docs/MOTION_DOWNGRADE.md",
            "docs/APP_PLAN.md",
            "docs/P2P_REMEDIATION_PLAN.md",
            "docs/PARLOR_P2P_SMOKE_TEST.md",
            "docs/PHASE_0_VALIDATION.md",
            "docs/PHASE_8_VALIDATION.md",
            "docs/PROGRESS.md",
            "whodunit-game-design.md",
        )

        historicalDocuments.forEach { path ->
            val warning = read(path).take(1_000).lowercase()
            assertTrue(
                "historical" in warning || "superseded" in warning,
                "$path must identify itself as historical/superseded before old details",
            )
        }
    }

    @Test
    fun current_authoring_and_accessibility_docs_name_executable_owners() {
        val schema = read("docs/CONTENT_SCHEMA.md")
        listOf(
            "Document status: current authoring guide.",
            "Production Kotlin types, validators,",
            "`signature`",
            "Must be absent",
            "`structuredAction = NONE`",
            "current seven bundled",
        ).forEach { marker ->
            assertTrue(marker in schema, "CONTENT_SCHEMA.md is missing: $marker")
        }
        assertFalse("source of truth for the content validator (Phase 3)" in schema)

        val review = read("docs/CONTENT_REVIEW.md")
        listOf(
            "Document status: current release checklist",
            "composeResources/files/cases/",
            "BundledCaseLoadingTest",
            "WhodunitContentIdentityTest",
            "WhodunitPayloadHardeningTest",
        ).forEach { marker ->
            assertTrue(marker in review, "CONTENT_REVIEW.md is missing: $marker")
        }
        assertFalse("content/last-dinner.draft.json" in review)

        val mock = read("docs/MOCK_BACKEND.md")
        assertTrue("historical filename retained" in mock)
        assertTrue("OfflineRemoteCaseDataSource" in mock)
        assertTrue("`MockEngine` appears only in" in mock)

        val motion = read("docs/MOTION_DOWNGRADE.md")
        assertTrue("historical filename retained" in motion)
        assertTrue("no `MotionCapabilityProbe`" in motion)
        assertTrue("rememberSystemReducedMotion()" in motion)

        val accessibility = read("docs/ACCESSIBILITY_AUDIT.md")
        assertTrue("current external release gate" in accessibility)
        assertTrue("physical-device receipts" in accessibility)
        assertTrue("must not be converted to PASS from a" in accessibility)
        assertTrue("simulator, screenshot, compile, or static test" in accessibility)
    }

    @Test
    fun production_source_comments_do_not_claim_historical_delivery_phases() {
        val productionFiles = repositoryRoot.walkTopDown()
            .filter(File::isFile)
            .filter { file -> file.extension == "kt" || file.extension == "kts" }
            .filter { file ->
                val path = file.relativeTo(repositoryRoot).invariantSeparatorsPath
                Regex("/src/(common|android|ios|desktop)Main/").containsMatchIn("/$path") ||
                    path.endsWith("build.gradle.kts")
            }
            .toList()

        assertTrue(productionFiles.isNotEmpty(), "No production sources found")
        val obsolete = productionFiles.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (
                    Regex("\\bPhase [0-9]").containsMatchIn(line) ||
                    Regex("\\bWave [0-9]").containsMatchIn(line)
                ) {
                    "${file.relativeTo(repositoryRoot).invariantSeparatorsPath}:${index + 1}:$line"
                } else {
                    null
                }
            }
        }
        assertTrue(
            obsolete.isEmpty(),
            "Production source still presents historical delivery labels as current:\n" +
                obsolete.joinToString("\n"),
        )
    }

    @Test
    fun ios_plist_and_setup_describe_the_same_lan_transport() {
        val setup = read("docs/IOS_SETUP.md")
        val plist = read("iosApp/iosApp/Info.plist")

        assertTrue("_p2pkit2._tcp" in setup)
        assertTrue("Network.framework" in setup)
        assertTrue("does not use MultipeerConnectivity or\nBluetooth" in setup)
        assertTrue("<string>_p2pkit2._tcp</string>" in plist)
        assertTrue("<key>NSLocalNetworkUsageDescription</key>" in plist)
        assertFalse("NSBluetoothAlwaysUsageDescription" in plist)
        assertFalse("NSBluetoothPeripheralUsageDescription" in plist)
    }

    @Test
    fun current_game_module_guides_name_the_live_shell_registry_contract() {
        val documents = listOf(
            "docs/PRODUCTION_ARCHITECTURE.md",
            "docs/adr/0001-game-module-registration.md",
            "docs/HOW_TO_ADD_A_GAME.md",
        )
        val text = documents.joinToString(separator = "\n") { path -> read(path) }

        listOf(
            "ModuleNavGraph",
            "NavGraphRegistry",
            "DefaultNavGraphRegistry",
            ":shared:navigation",
        ).forEach { removedContract ->
            assertFalse(
                removedContract in text,
                "Current game-module guides must not require removed $removedContract",
            )
        }

        mapOf(
            "GameDefinition" to
                "shared/engine/src/commonMain/kotlin/com/parlor/engine/definition/GameDefinition.kt",
            "DefaultGameRegistry" to
                "shared/engine/src/commonMain/kotlin/com/parlor/engine/registry/GameRegistry.kt",
            "GameShellBinding" to
                "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/GameShellRegistry.kt",
            "DefaultGameShellRegistry" to
                "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/GameShellRegistry.kt",
            "GameShellRouter" to
                "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/GameShellRegistry.kt",
            "GameRegistryExtensibilityTest" to
                "shared/engine-testing/src/commonTest/kotlin/" +
                    "com/parlor/engine/testing/registry/GameRegistryExtensibilityTest.kt",
            "GameShellRegistryExtensibilityTest" to
                "composeApp/src/commonTest/kotlin/com/parlor/app/shell/game/GameShellRegistryExtensibilityTest.kt",
            "GameShellRegistryCompositionTest" to
                "composeApp/src/desktopTest/kotlin/com/parlor/app/shell/game/GameShellRegistryCompositionTest.kt",
        ).forEach { (name, sourcePath) ->
            assertTrue(name in text, "Current game-module guides must name $name")
            assertTrue(
                Regex("\\b(?:class|interface)\\s+$name\\b").containsMatchIn(read(sourcePath)),
                "$name must resolve to a source declaration at $sourcePath",
            )
        }
    }

    private fun read(relativePath: String): String {
        val file = File(repositoryRoot, relativePath)
        assertTrue(file.isFile, "Missing documentation contract file: ${file.absolutePath}")
        return normalizeDocumentationLineEndings(file.readText())
    }

    private fun locateRepositoryRoot(): File {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(12)
            .firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile &&
                    File(candidate, "docs/P2P_MANUAL_TEST.md").isFile
            }
        return assertNotNull(
            root,
            "Could not locate the Parlor repository root from ${File(".").absoluteFile}",
        )
    }
}

internal fun normalizeDocumentationLineEndings(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n')
