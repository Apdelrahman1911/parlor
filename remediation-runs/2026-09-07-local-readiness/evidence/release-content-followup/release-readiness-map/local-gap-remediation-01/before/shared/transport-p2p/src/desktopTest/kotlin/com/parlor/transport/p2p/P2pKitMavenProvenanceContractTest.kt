package com.parlor.transport.p2p

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Makes the reviewed P2pKit release bytes part of Parlor's executable build
 * contract. Updating P2pKit therefore requires an explicit provenance review,
 * not only a version-catalog edit or regenerated Gradle metadata.
 */
class P2pKitMavenProvenanceContractTest {

    private val repositoryRoot: File by lazy(::locateRepositoryRoot)

    @Test
    fun p2pkit_resolves_only_from_the_pinned_production_coordinates() {
        val settings = repositoryRoot.resolve("settings.gradle.kts").readText()
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()

        assertTrue("mavenCentral()" in settings)
        assertFalse("mavenLocal()" in settings)
        assertFalse("includeBuild(\"../P2pKit\")" in settings)
        assertFalse("includeBuild(\"../p2pkit\")" in settings)
        assertTrue("p2pkit = \"$P2PKIT_VERSION\"" in catalog)
        assertTrue(
            "module = \"io.github.apdelrahman1911:p2p-core\"" in catalog,
        )
        assertTrue(
            "module = \"io.github.apdelrahman1911:p2p-transport-lan\"" in catalog,
        )
    }

    @Test
    fun every_shipped_p2pkit_variant_matches_the_reviewed_sha256() {
        val metadata = repositoryRoot
            .resolve("gradle/verification-metadata.xml")
            .readText()

        assertTrue("<verify-metadata>true</verify-metadata>" in metadata)
        assertFalse("p2p-network-provisioning" in metadata)

        expectedArtifacts.forEach { expected ->
            val header =
                "<component group=\"io.github.apdelrahman1911\" " +
                    "name=\"${expected.component}\" version=\"$P2PKIT_VERSION\">"
            val start = metadata.indexOf(header)
            assertTrue(start >= 0, "Missing verified component ${expected.component}")
            val end = metadata.indexOf("</component>", startIndex = start)
            assertTrue(end > start, "Malformed component block for ${expected.component}")
            val block = metadata.substring(start, end)

            assertTrue(
                "<artifact name=\"${expected.artifact}\">" in block,
                "Missing verified artifact ${expected.component}:${expected.artifact}",
            )
            assertTrue(
                "<sha256 value=\"${expected.sha256}\"" in block,
                "Unexpected checksum for ${expected.component}:${expected.artifact}",
            )
        }
    }

    private fun locateRepositoryRoot(): File {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(12)
            .firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile &&
                    File(candidate, "gradle/verification-metadata.xml").isFile
            }
        return assertNotNull(
            root,
            "Could not locate the Parlor repository root from ${File(".").absoluteFile}",
        )
    }

    private data class ExpectedArtifact(
        val component: String,
        val artifact: String,
        val sha256: String,
    )

    private companion object {
        const val P2PKIT_VERSION = "0.7.0-rc3"

        val expectedArtifacts = listOf(
            ExpectedArtifact(
                component = "p2p-core",
                artifact = "p2p-core-$P2PKIT_VERSION.module",
                sha256 = "8dfc573fe79bde06286c1183794ae72f622f6001610f97bb11871cace66ba463",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan",
                artifact = "p2p-transport-lan-$P2PKIT_VERSION.module",
                sha256 = "3676a7f90593f5c8958c233fddaf30fdf17778ef1be09206add50d2e85c3c3d9",
            ),
            ExpectedArtifact(
                component = "p2p-core-android",
                artifact = "p2p-core.aar",
                sha256 = "a24fd6ce11b5a59d65b001748d8a82747edea2491e80ae4e7814d03d855ff50f",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-android",
                artifact = "p2p-transport-lan.aar",
                sha256 = "4b1363f54c35db6749909e92b1bf9bdd6126218f79f1edefb8809ef85d9020d4",
            ),
            ExpectedArtifact(
                component = "p2p-core-jvm",
                artifact = "p2p-core-jvm-$P2PKIT_VERSION.jar",
                sha256 = "4ffb18b77cf55900ab8210c0e382bef9a25421de9196caa326d79c2da1cb5593",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-jvm",
                artifact = "p2p-transport-lan-jvm-$P2PKIT_VERSION.jar",
                sha256 = "571d8464b069f42e073244194fc338d3199ea5e9e63b0ba600a19e17058fcb1d",
            ),
            ExpectedArtifact(
                component = "p2p-core-iosarm64",
                artifact = "p2p-core-iosArm64Main-$P2PKIT_VERSION.klib",
                sha256 = "e33731c2c8151888d7c1cf0d42b38288ddcda16ccb52726f7645543ef9f95c93",
            ),
            ExpectedArtifact(
                component = "p2p-core-iossimulatorarm64",
                artifact = "p2p-core-iosSimulatorArm64Main-$P2PKIT_VERSION.klib",
                sha256 = "06b855509d5cd9f1cc27adbdf3f343beb053a781de499edf27c67abe53fb8492",
            ),
            ExpectedArtifact(
                component = "p2p-core-iosx64",
                artifact = "p2p-core-iosX64Main-$P2PKIT_VERSION.klib",
                sha256 = "b6ec8f13ce44bb02572373ca89b7cd70c62d3c344a7ae918fb75e07cbfb9fae6",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iosarm64",
                artifact = "p2p-transport-lan-iosArm64Main-$P2PKIT_VERSION.klib",
                sha256 = "66dd89cf562c7729307b77fda2d9e584d6100b78d5a5868b2a726794000e8b4a",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iosarm64",
                artifact = "p2p-transport-lan-iosArm64Cinterop-p2pkit_nwMain-$P2PKIT_VERSION.klib",
                sha256 = "bd9b60aca6f4dac875cccc61f3c5d3522ffaffade624e8342d9b49ac22e4c760",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iossimulatorarm64",
                artifact = "p2p-transport-lan-iosSimulatorArm64Main-$P2PKIT_VERSION.klib",
                sha256 = "d6bb064670f34ff04d3b2d8daf4cbac5af5b9aab2e524ee8765bfc9f7d98d61f",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iossimulatorarm64",
                artifact = "p2p-transport-lan-iosSimulatorArm64Cinterop-p2pkit_nwMain-$P2PKIT_VERSION.klib",
                sha256 = "7fccf58d3e6e6c920760333db831db5bbff04229c2f561c69433f41768da5e5d",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iosx64",
                artifact = "p2p-transport-lan-iosX64Main-$P2PKIT_VERSION.klib",
                sha256 = "13ff3e4b528bba102e64ca8f8589f6d42d939afc5bc9239957f9c794011b5543",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iosx64",
                artifact = "p2p-transport-lan-iosX64Cinterop-p2pkit_nwMain-$P2PKIT_VERSION.klib",
                sha256 = "7ba9a44b204461e26c28c45c58c755c8ee8ed179caa5e57b6ccf3d298dfa675d",
            ),
        )
    }
}
