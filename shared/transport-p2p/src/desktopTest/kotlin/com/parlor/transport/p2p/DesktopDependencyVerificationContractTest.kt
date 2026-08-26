package com.parlor.transport.p2p

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Keeps strict verification usable on every desktop host supported by this repository.
 *
 * Gradle records checksums only for artifacts resolved on the current host.
 * The workflow executes each host-selected graph; these exact checksum assertions
 * make an accidental metadata deletion fail on every CI runner as well.
 */
class DesktopDependencyVerificationContractTest {

    private val catalog: String by lazy {
        locateRepositoryRoot().resolve("gradle/libs.versions.toml").readText()
    }

    private val metadata: String by lazy {
        locateRepositoryRoot().resolve("gradle/verification-metadata.xml").readText()
    }

    @Test
    fun compose_desktop_and_skiko_runtime_cover_every_repository_supported_host() {
        assertTrue(
            "compose-multiplatform = \"$COMPOSE_VERSION\"" in catalog,
            "Host matrix must track the Compose version used by the build",
        )
        val variants = listOf(
            DesktopVariant(
                name = "linux-arm64",
                composePomSha256 = "8571cd36d2b46b38fd70eb5feea96cdd82011f93b2c049b142979ea997330edc",
                skikoJarSha256 = "3abcbaa542278fd7b938a08ac05b50139a905ced79075c257adfeae878413330",
                skikoPomSha256 = "fbcda0bce219b70da5de78280b5cd6567822e12823f99793a76bdde1b017fada",
            ),
            DesktopVariant(
                name = "linux-x64",
                composePomSha256 = "5a6361c112715bea2334898e208ec82640863d592287c2828b9edf56d5d733a9",
                skikoJarSha256 = "ec796df135d980bbb1740e789fe8668a8184df243e4d1c39997750303c76f13b",
                skikoPomSha256 = "39d4d3b389fb0191258e0202b7b0de6b09210509bcd6168b2774a5b24f2ed6a2",
            ),
            DesktopVariant(
                name = "macos-arm64",
                composePomSha256 = "3b1f96598e616809df2b4dd79133dd2e7b85f5f84cda4f7eb5cac3ed35b7e8c0",
                skikoJarSha256 = "5d4d47555313d387d742e5225b3f9daffcffd61cb9c3f1db56fc10963cf4280b",
                skikoPomSha256 = "6cb4f7eb377935dfdba5f077802918b974e3d5293940f8c92d230f363fc87533",
            ),
            DesktopVariant(
                name = "macos-x64",
                composePomSha256 = "7a0e2663942df71e9b2f6d03daa48f1296b39de7ee1a1d12b8993cad6af8b9dc",
                skikoJarSha256 = "d01dac2dba6965d9989e1ae939a1e889919f1e076cbf61672e14aa4be28655cd",
                skikoPomSha256 = "7a4c04a84673b29319b19f3a1546f43be28931c89d868fbd49c2ec6e7bf2249a",
            ),
            DesktopVariant(
                name = "windows-x64",
                composePomSha256 = "59bdebb74401f2f13f99672bba88f882ad38b6a4295ca0e508f53965f8e4bb43",
                skikoJarSha256 = "12cbbc1890773264d99a15a234a35001e61098f9e54a631554f89dc8d84ccd9b",
                skikoPomSha256 = "6eb7877c643b24073e802b05234b880b85c5910ebfe89c298fae7a1b9add9c0e",
            ),
        )

        variants.forEach { variant ->
            assertArtifact(
                group = "org.jetbrains.compose.desktop",
                name = "desktop-jvm-${variant.name}",
                version = COMPOSE_VERSION,
                artifact = "desktop-jvm-${variant.name}-$COMPOSE_VERSION.pom",
                sha256 = variant.composePomSha256,
            )
            assertArtifact(
                group = "org.jetbrains.skiko",
                name = "skiko-awt-runtime-${variant.name}",
                version = SKIKO_VERSION,
                artifact = "skiko-awt-runtime-${variant.name}-$SKIKO_VERSION.jar",
                sha256 = variant.skikoJarSha256,
            )
            assertArtifact(
                group = "org.jetbrains.skiko",
                name = "skiko-awt-runtime-${variant.name}",
                version = SKIKO_VERSION,
                artifact = "skiko-awt-runtime-${variant.name}-$SKIKO_VERSION.pom",
                sha256 = variant.skikoPomSha256,
            )
        }
    }

    @Test
    fun kotlin_native_distributions_cover_every_supported_native_toolchain_host() {
        assertTrue(
            "kotlin = \"$KOTLIN_VERSION\"" in catalog,
            "Kotlin Native checksums must track the Kotlin version used by the build",
        )
        mapOf(
            "linux-x86_64.tar.gz" to
                "c9e356e8518144f275f1514cfe38b07db949f93e47e054832b8974fff1fd33e0",
            "macos-aarch64.tar.gz" to
                "55ded039bb56a69aec9df354a92b42df9e916104e3c53d8d9852d9cc6617ed9d",
            "macos-x86_64.tar.gz" to
                "7bfda60c2a4ce859fc85011ea2c3229961b1eb40e9cc0b6b85fee885f23973cb",
            "windows-x86_64.zip" to
                "ce99eba1f4faec1d77f4bbd747bb722404ef11f2c349ec70c59d4c002859380f",
        ).forEach { (hostArchive, sha256) ->
            assertArtifact(
                group = "org.jetbrains.kotlin",
                name = "kotlin-native-prebuilt",
                version = KOTLIN_VERSION,
                artifact = "kotlin-native-prebuilt-$KOTLIN_VERSION-$hostArchive",
                sha256 = sha256,
            )
        }
    }

    @Test
    fun android_aapt2_covers_every_supported_desktop_operating_system() {
        assertTrue(
            "agp = \"$AGP_VERSION\"" in catalog,
            "aapt2 checksums must track the Android Gradle Plugin version",
        )
        mapOf(
            "linux" to "839609d6d776d6dd60a02aa577d97193ce3e650cf1deaabf062321e23bbd6bf6",
            "osx" to "0d47f17c3924e5472b6125aa608d949dd7f46510889729671f31f2f4d801e8e7",
            "windows" to "5dc730c3dc454b76d779a46036c06fd9c874039a31e22214434ecdbe64c3300a",
        ).forEach { (platform, sha256) ->
            assertArtifact(
                group = "com.android.tools.build",
                name = "aapt2",
                version = AAPT2_VERSION,
                artifact = "aapt2-$AAPT2_VERSION-$platform.jar",
                sha256 = sha256,
            )
        }
    }

    private fun assertArtifact(
        group: String,
        name: String,
        version: String,
        artifact: String,
        sha256: String,
    ) {
        val componentMarker =
            "<component group=\"$group\" name=\"$name\" version=\"$version\">"
        val component = metadata.substringAfter(componentMarker, missingDelimiterValue = "")
            .substringBefore("</component>")
        assertTrue(component.isNotEmpty(), "Missing verified component $group:$name:$version")
        assertTrue(
            "<artifact name=\"$artifact\">" in component,
            "Missing verified artifact $group:$name:$version:$artifact",
        )
        assertTrue(
            "<sha256 value=\"$sha256\"" in component,
            "Unexpected checksum for $group:$name:$version:$artifact",
        )
    }

    private fun locateRepositoryRoot(): File {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(12)
            .firstOrNull { candidate ->
                candidate.resolve("settings.gradle.kts").isFile &&
                    candidate.resolve("gradle/verification-metadata.xml").isFile
            }
        return assertNotNull(root, "Could not locate repository root from ${File(".").absoluteFile}")
    }

    private data class DesktopVariant(
        val name: String,
        val composePomSha256: String,
        val skikoJarSha256: String,
        val skikoPomSha256: String,
    )

    private companion object {
        const val KOTLIN_VERSION = "2.4.10"
        const val COMPOSE_VERSION = "1.10.3"
        const val SKIKO_VERSION = "0.9.37.4"
        const val AGP_VERSION = "8.13.2"
        const val AAPT2_VERSION = "8.13.2-14304508"
    }
}
