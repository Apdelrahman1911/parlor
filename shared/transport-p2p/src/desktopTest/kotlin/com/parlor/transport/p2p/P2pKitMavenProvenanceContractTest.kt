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
        assertTrue("p2pkit = \"0.7.0-rc2\"" in catalog)
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
                    "name=\"${expected.component}\" version=\"0.7.0-rc2\">"
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
        val expectedArtifacts = listOf(
            ExpectedArtifact(
                component = "p2p-core",
                artifact = "p2p-core-0.7.0-rc2.module",
                sha256 = "7a92e4d038e11ff6532467462b7b5c3a441f4381f8bfa513c303bfc5744d1717",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan",
                artifact = "p2p-transport-lan-0.7.0-rc2.module",
                sha256 = "ef52db9f0bb29854b47204e70ffc166ba3fccd7d7e506c234bd3e26bd55c8b8b",
            ),
            ExpectedArtifact(
                component = "p2p-core-android",
                artifact = "p2p-core.aar",
                sha256 = "84d8a1c40a25ccf3481a4b1fc618c49647fa4179132f50fc1521f9ad5e1c861e",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-android",
                artifact = "p2p-transport-lan.aar",
                sha256 = "34afa9ef9e7aa1a5e71b1f4a5411ee7f14f77347eba3770c24304623d800f00d",
            ),
            ExpectedArtifact(
                component = "p2p-core-jvm",
                artifact = "p2p-core-jvm-0.7.0-rc2.jar",
                sha256 = "95f9d4aa0150e241265c512bb7a52b7c98a7e594726d6c3efe5057eab5cd8a6a",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-jvm",
                artifact = "p2p-transport-lan-jvm-0.7.0-rc2.jar",
                sha256 = "410906c2e4b0b69db3fce27992d3891b324da2b9acf930e6dc8e0a294dba7be7",
            ),
            ExpectedArtifact(
                component = "p2p-core-iosarm64",
                artifact = "p2p-core-iosArm64Main-0.7.0-rc2.klib",
                sha256 = "2329f33c79788713d764ca3e0bfd89bd2193bb7393dd50ca32936bace2a89043",
            ),
            ExpectedArtifact(
                component = "p2p-core-iossimulatorarm64",
                artifact = "p2p-core-iosSimulatorArm64Main-0.7.0-rc2.klib",
                sha256 = "990f23bfd8e11b4e34cbc0f2ff504118cc49c90254f973c263f7826123213ba9",
            ),
            ExpectedArtifact(
                component = "p2p-core-iosx64",
                artifact = "p2p-core-iosX64Main-0.7.0-rc2.klib",
                sha256 = "030da4114aedabd12d566069b6604f8d5e1a99fa89829363b8c0ae426e5f2d43",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iosarm64",
                artifact = "p2p-transport-lan-iosArm64Main-0.7.0-rc2.klib",
                sha256 = "1065e7f413d56f714c24f62ae05ec7c77f5cc41e62f06539b45499a9ad7c6004",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iosarm64",
                artifact = "p2p-transport-lan-iosArm64Cinterop-p2pkit_nwMain-0.7.0-rc2.klib",
                sha256 = "ae330cf0ede29d67b6a2f637c16891f02c5e798eca624c7fe69eba35ceb7b3be",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iossimulatorarm64",
                artifact = "p2p-transport-lan-iosSimulatorArm64Main-0.7.0-rc2.klib",
                sha256 = "ad6029bf3b4361ba10ab6636739cdae98e54f80aadf2e7fbfc754a97417f3d0a",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iossimulatorarm64",
                artifact = "p2p-transport-lan-iosSimulatorArm64Cinterop-p2pkit_nwMain-0.7.0-rc2.klib",
                sha256 = "3e6bcf04ccde50831853c908319700383c6ef85dd7eb4dd93698b50ced9dc543",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iosx64",
                artifact = "p2p-transport-lan-iosX64Main-0.7.0-rc2.klib",
                sha256 = "b07344ece10b82240d0214364c2035da43be650e91574305a983e9b430e74f32",
            ),
            ExpectedArtifact(
                component = "p2p-transport-lan-iosx64",
                artifact = "p2p-transport-lan-iosX64Cinterop-p2pkit_nwMain-0.7.0-rc2.klib",
                sha256 = "87713d61ea5b014a1225bf0abc89002e0189fd64fcfc76a03b47cc5db8b54a16",
            ),
        )
    }
}
