package com.parlor.transport.p2p

import java.io.File
import kotlin.test.Test
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
            "P2pKit 0.7.0-rc2",
        ).forEach { required ->
            assertTrue(
                runbook.contains(required),
                "P2P_MANUAL_TEST.md must retain current contract marker: $required",
            )
        }
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

    private fun read(relativePath: String): String {
        val file = File(repositoryRoot, relativePath)
        assertTrue(file.isFile, "Missing documentation contract file: ${file.absolutePath}")
        return file.readText()
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
