package com.parlor.transport.p2p

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Repository-level regression contract for FR-08/FR-09. Android lint itself is
 * still the authority for Android analysis; these checks make it impossible to
 * silently remove the lint gate or reintroduce the concrete resource defects
 * that the baseline lint report exposed.
 */
class AndroidReleaseLintContractTest {

    private val repositoryRoot: File by lazy(::locateRepositoryRoot)

    @Test
    fun production_gate_executes_the_triaged_release_lint_verifier() {
        val rootBuild = read("build.gradle.kts")
        val appBuild = read("composeApp/build.gradle.kts")
        val catalog = read("gradle/libs.versions.toml")
        val workflow = read(".github/workflows/production-verification.yml")
        val triage = read("docs/ANDROID_LINT_TRIAGE.md")

        assertContains(rootBuild, ":composeApp:verifyReleaseLintWarnings")
        assertContains(appBuild, "dependsOn(\"lintRelease\")")
        assertContains(appBuild, "reports/lint-results-release.xml")
        setOf(
            "OldTargetApi",
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        ).forEach { reviewedId -> assertContains(appBuild, "\"$reviewedId\"") }
        assertContains(workflow, "productionCheck")
        assertContains(workflow, "lint-results-*")
        assertContains(catalog, "androidx-activity-compose")
        assertContains(appBuild, "implementation(libs.androidx.activity.compose)")
        assertContains(triage, "reported 59 warnings")
        assertContains(triage, "reports 39 warnings")
    }

    @Test
    fun launcher_is_adaptive_on_every_supported_android_version() {
        val manifest = read("composeApp/src/androidMain/AndroidManifest.xml")
        assertFalse(manifest.contains("hasFragileUserData"))
        assertContains(manifest, "android:icon=\"@mipmap/ic_launcher\"")
        assertContains(manifest, "android:roundIcon=\"@mipmap/ic_launcher_round\"")

        listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { name ->
            val adaptive = read("composeApp/src/androidMain/res/mipmap-anydpi/$name")
            assertContains(adaptive, "<adaptive-icon")
            assertContains(adaptive, "<monochrome")
            assertContains(adaptive, "@drawable/ic_launcher_monochrome")
        }
        val monochrome = read(
            "composeApp/src/androidMain/res/drawable/ic_launcher_monochrome.xml",
        )
        assertContains(monochrome, "<vector")

        val androidResources = repositoryRoot.resolve("composeApp/src/androidMain/res")
        val redundantLaunchers = androidResources.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "png" && it.name.startsWith("ic_launcher") }
            .filterNot { it.parentFile.name == "drawable-nodpi" }
            .toList()
        assertTrue(
            redundantLaunchers.isEmpty(),
            "Adaptive icons make these density launcher copies redundant: $redundantLaunchers",
        )
        assertFalse(androidResources.resolve("mipmap-anydpi-v26").exists())
    }

    @Test
    fun application_strings_are_reachable_and_locales_have_identical_keys() {
        val englishFile = repositoryRoot.resolve(
            "composeApp/src/commonMain/composeResources/values/strings.xml",
        )
        val arabicFile = repositoryRoot.resolve(
            "composeApp/src/commonMain/composeResources/values-ar/strings.xml",
        )
        val englishNames = stringNames(englishFile)
        val arabicNames = stringNames(arabicFile)
        assertEquals(englishNames, arabicNames, "English and Arabic resource keys diverged")

        val productionKotlin = buildString {
            repositoryRoot.resolve("composeApp/src").walkTopDown()
                .filter(File::isFile)
                .filter { it.extension == "kt" && "Test" !in it.invariantSeparatorsPath }
                .forEach { appendLine(it.readText()) }
        }
        val androidManifest = read("composeApp/src/androidMain/AndroidManifest.xml")
        val unreachable = englishNames.filterNot { name ->
            "Res.string.$name" in productionKotlin || "@string/$name" in androidManifest
        }
        assertTrue(unreachable.isEmpty(), "Unreachable app string resources: $unreachable")
    }

    private fun stringNames(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until strings.length) {
                add(strings.item(index).attributes.getNamedItem("name").nodeValue)
            }
        }
    }

    private fun read(relativePath: String): String {
        val file = File(repositoryRoot, relativePath)
        assertTrue(file.isFile, "Missing release contract file: ${file.absolutePath}")
        return file.readText()
    }

    private fun locateRepositoryRoot(): File {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(12)
            .firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile &&
                    File(candidate, ".github/workflows/production-verification.yml").isFile
            }
        return assertNotNull(root, "Could not locate the Parlor repository root")
    }
}
