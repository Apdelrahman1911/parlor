package com.parlor.transport.p2p

import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertContains(
            read("shared/transport-p2p/build.gradle.kts"),
            "rootProject.file(\"config/android-lint-accepted-warnings.txt\")",
        )
        assertContains(appBuild, "Newer version of lint available: ")
        assertContains(appBuild, "id in setOf(\"GradleDependency\", \"NewerVersionAvailable\")")
        assertContains(appBuild, "\"DependencyUpdate\"")
        assertContains(appBuild, "actual == expected")
        val accepted = inventory.lineSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
            .toList()
        assertEquals(35, accepted.size)
        assertEquals(
            mapOf(
                "AndroidGradlePluginVersion" to 4,
                "DependencyUpdate" to 29,
                "GradleDependency" to 1,
                "OldTargetApi" to 1,
            ),
            accepted.groupingBy { line -> line.substringBefore('|') }.eachCount(),
        )
        assertContains(
            accepted,
            "GradleDependency|gradle.properties|Newer version of lint available",
        )
        assertFalse(
            accepted.any { line ->
                line.startsWith(
                    "GradleDependency|gradle.properties|Newer version of lint available: ",
                )
            },
        )
        assertFalse(
            accepted.any { line ->
                line.startsWith("GradleDependency|gradle/libs.versions.toml|") ||
                    line.startsWith("NewerVersionAvailable|gradle/libs.versions.toml|")
            },
        )
        listOf(
            "org.jetbrains.kotlin.multiplatform",
            "org.jetbrains.kotlin.plugin.compose",
            "org.jetbrains.kotlin.plugin.serialization",
        ).forEach { pluginId ->
            assertContains(
                accepted,
                "DependencyUpdate|gradle/libs.versions.toml|A newer version of $pluginId than 2.4.10",
            )
        }
        assertContains(workflow, "productionCheck")
        assertContains(workflow, "lint-results-*")
        assertContains(catalog, "androidx-activity-compose")
        assertContains(appBuild, "implementation(libs.androidx.activity.compose)")
        assertContains(triage, "reported 59 warnings")
        assertContains(triage, "contains 35")
    }

    @Test
    fun kotlin_advisory_triage_requires_the_build_to_remain_without_kapt() {
        val moduleBuilds = Regex("""include\("(:[^"]+)"\)""")
            .findAll(read("settings.gradle.kts"))
            .map { match -> match.groupValues[1].removePrefix(":").replace(':', '/') + "/build.gradle.kts" }
            .toList()
        assertTrue(moduleBuilds.isNotEmpty(), "No included modules found for the KAPT applicability review")
        val buildSources = (
            listOf(
                "build.gradle.kts",
                "settings.gradle.kts",
                "gradle.properties",
                "gradle/libs.versions.toml",
                "build-logic/settings.gradle.kts",
                "build-logic/convention/build.gradle.kts",
            ) + moduleBuilds
        ).map(repositoryRoot::resolve) + conventionKotlinSources(
            repositoryRoot.resolve("build-logic/convention/src/main/kotlin"),
        )
        assertNoKaptBuildSources(buildSources)
        assertFalse(
            read("gradle/verification-metadata.xml").contains("kotlin-annotation-processing"),
            "A processing-runtime dependency invalidates the no-KAPT applicability decision",
        )
        val transportBuild = read("shared/transport-p2p/build.gradle.kts")
        assertContains(transportBuild, "rootProject.fileTree(\"build-logic/convention/src/main/kotlin\")")
        assertContains(transportBuild, "include(\"**/*.kt\", \"**/*.kts\")")
    }

    @Test
    fun convention_source_guard_includes_nested_precompiled_kotlin_scripts() {
        val fixture = Files.createTempDirectory("parlor-convention-source-").toFile()
        try {
            fixture.resolve("RegularConvention.kt").writeText("class RegularConvention")
            val precompiled = fixture.resolve("nested/plugins/precompiled.gradle.kts")
            assertTrue(precompiled.parentFile.mkdirs())
            precompiled.writeText("plugins {}")
            fixture.resolve("ignored.txt").writeText("kapt is not configured by this non-source fixture")
            val sources = conventionKotlinSources(fixture)
            assertEquals(
                setOf("RegularConvention.kt", "nested/plugins/precompiled.gradle.kts"),
                sources.map { file -> file.relativeTo(fixture).invariantSeparatorsPath }.toSet(),
            )
            assertNoKaptBuildSources(sources)

            precompiled.writeText("plugins { kotlin(\"kapt\") }")
            val rejected = assertFailsWith<AssertionError> {
                assertNoKaptBuildSources(conventionKotlinSources(fixture))
            }
            assertContains(rejected.message.orEmpty(), "precompiled.gradle.kts")
        } finally {
            assertTrue(fixture.deleteRecursively(), "Could not remove owned convention-source fixture")
        }
    }

    private fun conventionKotlinSources(directory: File): List<File> = directory.walkTopDown()
        .filter { file -> file.isFile && file.extension in setOf("kt", "kts") }
        .sortedBy(File::invariantSeparatorsPath)
        .toList()

    private fun assertNoKaptBuildSources(sources: List<File>) {
        sources.forEach { file ->
            assertFalse(
                file.readText().contains("kapt", ignoreCase = true),
                "${file.invariantSeparatorsPath} changes the no-KAPT prerequisite; " +
                    "re-review GHSA-r937-wjx7-w2jp before accepting this pin",
            )
        }
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
        val iconButton = read(
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/" +
                "ParlorIconButton.kt",
        )
        assertContains(iconButton, ".size(ParlorTheme.spacing.xxl)")
        assertContains(iconButton, "contentDescription: String")
        assertContains(iconButton, "contentDescription.isNotBlank()")
        assertContains(iconButton, "role = Role.Button")

        val header = read(
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ScreenHeader.kt",
        )
        assertContains(header, "ParlorIconButton(")
        assertContains(header, "Modifier.semantics { heading() }")

        val tabs = read(
            "shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorBottomTabBar.kt",
        )
        assertContains(tabs, ".heightIn(min = ParlorTheme.spacing.xxl)")
        assertContains(tabs, ".selectableGroup()")
        assertContains(tabs, "selected = selected")
        assertContains(tabs, "role = Role.Tab")

        val navigationHost = read(
            "composeApp/src/commonMain/kotlin/com/parlor/app/AppNavigationHost.kt",
        )
        assertContains(navigationHost, "icon = ParlorIcons.Settings")
        assertContains(navigationHost, "icon = ParlorIcons.FolderOpen")

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
