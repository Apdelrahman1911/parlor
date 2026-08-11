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
    fun kotlin_metadata_analyzers_are_explicitly_pinned_and_enforced() {
        val catalog = read("gradle/libs.versions.toml")
        val properties = read("gradle.properties")
        val settings = read("settings.gradle.kts")

        assertContains(catalog, "kotlin = \"2.4.10\"")
        assertContains(properties, "android.experimental.lint.version=9.1.1")
        assertContains(properties, "parlor.android.r8.version=9.1.41")
        assertContains(
            settings,
            "providers.gradleProperty(\"parlor.android.r8.version\").get()",
        )
        assertContains(
            settings,
            "classpath(\"com.android.tools:r8:${'$'}parlorR8Version\")",
        )
    }

    @Test
    fun production_gate_executes_the_triaged_release_lint_verifier() {
        val rootBuild = read("build.gradle.kts")
        val appBuild = read("composeApp/build.gradle.kts")
        val catalog = read("gradle/libs.versions.toml")
        val workflow = read(".github/workflows/production-verification.yml")
        val triage = read("docs/ANDROID_LINT_TRIAGE.md")
        val inventory = read("config/android-lint-accepted-warnings.txt")

        assertContains(rootBuild, ":composeApp:verifyReleaseLintWarnings")
        assertContains(appBuild, "dependsOn(\"lintRelease\")")
        assertContains(appBuild, "reports/lint-results-release.xml")
        assertContains(appBuild, "config/android-lint-accepted-warnings.txt")
        assertContains(appBuild, "actual == expected")
        val accepted = inventory.lineSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
            .toList()
        assertEquals(40, accepted.size)
        assertEquals(
            mapOf(
                "AndroidGradlePluginVersion" to 4,
                "GradleDependency" to 2,
                "NewerVersionAvailable" to 33,
                "OldTargetApi" to 1,
            ),
            accepted.groupingBy { line -> line.substringBefore('|') }.eachCount(),
        )
        assertContains(workflow, "productionCheck")
        assertContains(workflow, "lint-results-*")
        assertContains(catalog, "androidx-activity-compose")
        assertContains(appBuild, "implementation(libs.androidx.activity.compose)")
        assertContains(triage, "reported 59 warnings")
        assertContains(triage, "reports 40 warnings")
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
    fun all_shipping_strings_are_reachable_and_locales_have_identical_keys() {
        val resourceModules = listOf(
            "composeApp",
            "game-modes/mafia",
            "game-modes/whodunit",
        )
        resourceModules.forEach { module ->
            val englishFile = repositoryRoot.resolve(
                "$module/src/commonMain/composeResources/values/strings.xml",
            )
            val arabicFile = repositoryRoot.resolve(
                "$module/src/commonMain/composeResources/values-ar/strings.xml",
            )
            val englishNames = stringNames(englishFile)
            val arabicNames = stringNames(arabicFile)
            assertEquals(
                englishNames,
                arabicNames,
                "$module English and Arabic resource keys diverged",
            )

            val productionText = buildString {
                repositoryRoot.resolve("$module/src").walkTopDown()
                    .filter(File::isFile)
                    .filter { file ->
                        file.extension in setOf("kt", "xml") &&
                            "/src/commonTest/" !in file.invariantSeparatorsPath &&
                            "/src/desktopTest/" !in file.invariantSeparatorsPath &&
                            "/src/androidUnitTest/" !in file.invariantSeparatorsPath
                    }
                    .forEach { appendLine(it.readText()) }
            }
            val unreachable = englishNames.filterNot { name ->
                "Res.string.$name" in productionText ||
                    ".resources.$name" in productionText ||
                    "@string/$name" in productionText
            }
            assertTrue(
                unreachable.isEmpty(),
                "Unreachable $module string resources: $unreachable",
            )
        }
    }

    @Test
    fun compact_interactive_affordances_enforce_accessible_semantics_and_touch_targets() {
        val header = read(
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ScreenHeader.kt",
        )
        assertContains(header, ".size(ParlorTheme.spacing.xxl)")
        assertContains(header, "Modifier.semantics { heading() }")

        val tabs = read(
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorBottomTabBar.kt",
        )
        assertContains(tabs, ".heightIn(min = ParlorTheme.spacing.xxl)")
        assertContains(tabs, "role = Role.Tab")
        assertContains(tabs, "this.selected = selected")

        val home = read("composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt")
        assertContains(home, ".heightIn(min = ParlorTheme.spacing.xxl)")

        val privacy = read(
            "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/safety/PrivacyConcernOverlay.kt",
        )
        assertContains(privacy, ".heightIn(min = ParlorTheme.spacing.xxl)")
    }

    @Test
    fun unsupported_play_modes_are_described_truthfully() {
        val picker = read(
            "composeApp/src/commonMain/kotlin/com/parlor/app/shell/playmode/PlayModePickerScreen.kt",
        )
        val english = read("composeApp/src/commonMain/composeResources/values/strings.xml")
        val arabic = read("composeApp/src/commonMain/composeResources/values-ar/strings.xml")

        assertContains(picker, "Res.string.setup_mode_unavailable")
        assertContains(picker, "enabled = availability.solo")
        assertContains(picker, "enabled = availability.passAndPlay")
        assertContains(picker, "enabled = availability.join")
        assertContains(english, "This setup is not available for this game.")
        assertFalse(english.contains("All four work with the same case"))
        assertContains(arabic, "هذا الإعداد غير متاح لهذه اللعبة.")
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
