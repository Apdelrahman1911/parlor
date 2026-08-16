package com.parlor.transport.p2p

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the executable CI contract. A green workflow must not be possible
 * after silently dropping an automatable release gate from either CI or the
 * root Gradle aggregate that CI invokes.
 */
class ProductionVerificationWorkflowContractTest {

    private val repositoryRoot: File by lazy(::locateRepositoryRoot)

    @Test
    fun linux_job_enforces_tests_analysis_android_release_and_artifact_inspection() {
        val workflow = read(".github/workflows/production-verification.yml")
        val rootBuild = read("build.gradle.kts")

        listOf(
            "productionCheck",
            "productionStaticAnalysis",
            "allTests",
            "--dependency-verification=strict",
            "unzip -t",
            "base/manifest/AndroidManifest.xml",
            "base/dex/classes.dex",
            "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml",
            "sha256sum",
        ).forEach { required ->
            assertContains(
                workflow,
                required,
                message = "Linux CI must retain the release gate: $required",
            )
        }

        val androidGate = rootBuild.substringAfter("val productionAndroidCheck")
            .substringBefore("val productionAndroidSigningCheck")
        listOf(
            ":composeApp:testDebugUnitTest",
            ":composeApp:testReleaseUnitTest",
            ":composeApp:compileReleaseKotlinAndroid",
            ":composeApp:lintRelease",
            ":composeApp:verifyReleaseLintWarnings",
            ":composeApp:verifyMergedReleaseManifest",
            ":composeApp:minifyReleaseWithR8",
            ":composeApp:bundleRelease",
        ).forEach { task ->
            assertContains(
                androidGate,
                task,
                message = "productionAndroidCheck must enforce $task",
            )
        }

        val staticGate = rootBuild.substringAfter("val productionStaticAnalysis")
            .substringBefore("tasks.register(\"productionAppleCheck\")")
        assertContains(staticGate, "dependsOn(staticAnalysis, typeAwareStaticAnalysis)")
        val repositoryStaticGate = rootBuild.substringAfter("val staticAnalysis")
            .substringBefore("val productionStaticAnalysis")
        assertContains(repositoryStaticGate, "includedBuild(\"build-logic\")")
        assertContains(repositoryStaticGate, "task(\":convention:detekt\")")
        val detektConvention = read(
            "build-logic/convention/src/main/kotlin/com/parlor/buildlogic/DetektConventionPlugin.kt",
        )
        listOf(
            "reports/detekt/\$name.html",
            "reports/detekt/\$name.sarif",
            "reports/detekt/\$name.xml",
            "element.file.toPath().normalize().startsWith(generatedRoot)",
        ).forEach { contract -> assertContains(detektConvention, contract) }
        assertFalse("baseline.setFrom" in detektConvention)
        listOf(
            "detektMetadataCommonMain",
            "detektDesktopMain",
            "detektDesktopTest",
            "detektAndroidRelease",
            "detektAndroidReleaseUnitTest",
            "detektAndroidDebugAndroidTest",
        ).forEach { task ->
            assertContains(rootBuild, task, message = "Host type-aware analysis must retain $task")
        }
        assertContains(rootBuild, "tasks.named(\"typeAwareStaticAnalysis\")")
        assertContains(rootBuild, "tasks.matching { it.name in hostTypeAwareDetektTasks }.all")
        assertContains(rootBuild, "androidInstrumentedSources.isEmpty")
        assertContains(rootBuild, "it.name == androidInstrumentedTypeAwareDetektTask")
        val productionGate = rootBuild.substringAfter("tasks.register(\"productionCheck\")")
            .substringBefore("subprojects")
        assertContains(productionGate, "productionStaticAnalysis")

        val appBuild = read("composeApp/build.gradle.kts")
        assertContains(
            appBuild,
            "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml",
        )
        val mergedManifestGate = appBuild.substringAfter("val verifyMergedReleaseManifest")
            .substringBefore("val verifyReleaseSigning")
        listOf(
            "processReleaseManifest",
            "actualPermissions == expectedPermissions",
            "allowBackup",
            "usesCleartextTraffic",
            "debuggable",
            "testOnly",
            "exportedComponents == expectedExportedComponents",
        ).forEach { contract -> assertContains(mergedManifestGate, contract) }
        assertFalse(
            "find composeApp/build/intermediates" in workflow,
            "CI must never select an arbitrary debug/release merged manifest",
        )

        listOf(
            "**/build/reports/detekt/",
            "**/build/reports/tests/",
            "**/build/reports/lint-results-*",
            "composeApp/build/outputs/mapping/release/",
            "composeApp/build/outputs/bundle/release/",
        ).forEach { evidence ->
            assertContains(
                workflow,
                evidence,
                message = "CI must retain diagnostic evidence: $evidence",
            )
        }
    }

    @Test
    fun third_party_actions_are_pinned_to_reviewed_immutable_commits() {
        val workflow = read(".github/workflows/production-verification.yml")
        val expectedPins = listOf(
            "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1",
            "actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0",
            "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1",
        )
        expectedPins.forEach { assertContains(workflow, it) }

        val unpinned = Regex("uses:\\s+[^\\s@]+@(?:v|main|master)[^\\s]*")
            .findAll(workflow)
            .map { it.value }
            .toList()
        assertFalse(unpinned.isNotEmpty(), "Mutable third-party action refs: $unpinned")
    }

    @Test
    fun clean_ci_can_resolve_required_artifacts_with_strict_dependency_verification() {
        val verificationMetadata = read("gradle/verification-metadata.xml")
        fun verifyComponent(componentMarker: String, artifactName: String, sha256: String) {
            assertContains(verificationMetadata, componentMarker)
            val component = verificationMetadata
                .substringAfter(componentMarker)
                .substringBefore("</component>")
            assertContains(component, "<artifact name=\"$artifactName\">")
            assertContains(
                component,
                "<sha256 value=\"$sha256\"",
                message = "Fresh CI must verify $artifactName before plugin resolution",
            )
        }

        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.kotlinx\" name=\"kotlinx-coroutines-bom\" version=\"1.6.4\">",
            artifactName = "kotlinx-coroutines-bom-1.6.4.pom",
            sha256 = "ab2614855fba66aa8a42514dbe3d5a884315ffe1ed63f5932e710a8006245ce1",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"com.google.guava\" name=\"guava-parent\" version=\"33.3.1-jre\">",
            artifactName = "guava-parent-33.3.1-jre.pom",
            sha256 = "55441db27e8869dfefe053059bdf478bdc7e95585642bf391f0023345fd56287",
        )
        verifyComponent(
            componentMarker = "<component group=\"org.junit\" name=\"junit-bom\" version=\"5.10.2\">",
            artifactName = "junit-bom-5.10.2.module",
            sha256 = "de23b114b3e4119a8fe6eb17bed5a3852816698bace67071579d6d927ebb080a",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.kotlinx\" name=\"kotlinx-coroutines-bom\" version=\"1.8.0\">",
            artifactName = "kotlinx-coroutines-bom-1.8.0.pom",
            sha256 = "1239e9dbe1397cd5971342956b2511bc3ace7b641842e4372a088dcfa8b9ad55",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"dev.whyoleg.cryptography\" name=\"cryptography-bom\" version=\"0.6.0\">",
            artifactName = "cryptography-bom-0.6.0.pom",
            sha256 = "3e83e5af287ca142a03ab81a35395e59bb20cb43d63915b2f96049835eb22d6b",
        )
    }

    @Test
    fun apple_job_runs_simulator_tests_all_link_targets_and_unsigned_swift_release() {
        val workflow = read(".github/workflows/production-verification.yml")
        val rootBuild = read("build.gradle.kts")
        val xcodeProject = read("iosApp/iosApp.xcodeproj/project.pbxproj")

        listOf(
            "DEVELOPER_DIR: /Applications/Xcode_26.3.app/Contents/Developer",
            "Xcode 26.3",
            "Build version 17C529",
            ".toolchains.apple.minimum_ios_sdk_major",
            "xcrun --sdk iphoneos --show-sdk-version",
            "xcrun --sdk iphonesimulator --show-sdk-version",
            "allTests",
            "productionAppleCheck",
            "Apple type-aware static analysis",
            "--dependency-verification=strict",
            "xcodebuild",
            "-configuration Release",
            "generic/platform=iOS Simulator",
            "CODE_SIGNING_ALLOWED=NO",
            "CODE_SIGNING_REQUIRED=NO",
            "plutil -lint",
            "runtime tests",
            "linkage-only",
        ).forEach { required ->
            assertContains(
                workflow,
                required,
                message = "Apple CI must retain the release gate: $required",
            )
        }
        assertContains(
            xcodeProject,
            "embedAndSignAppleFrameworkForXcode --dependency-verification=strict",
            message = "The Xcode shell phase must enforce strict dependency verification",
        )

        val swiftReleaseMarker = "- name: Build unsigned Swift Release wrapper"
        val appleEvidenceMarker = "- name: Upload Apple verification evidence"
        assertContains(workflow, swiftReleaseMarker)
        assertContains(workflow, appleEvidenceMarker)
        val swiftReleaseStep = workflow
            .substringAfter(swiftReleaseMarker)
            .substringBefore(appleEvidenceMarker)
        listOf(
            "ARCHS=arm64",
            "ONLY_ACTIVE_ARCH=YES",
        ).forEach { required ->
            assertContains(
                swiftReleaseStep,
                required,
                message = "The unsigned Swift Release wrapper must remain a deterministic single-architecture build: $required",
            )
        }

        listOf(
            "docs/IOS_SETUP.md",
            "docs/RELEASE_RUNBOOK.md",
        ).forEach { path ->
            val releaseInstructions = read(path)
            assertContains(
                releaseInstructions,
                "-configuration Release",
                message = "$path must document the Release wrapper build",
            )
            assertContains(
                releaseInstructions,
                "ARCHS=arm64",
                message = "$path must document the qualified wrapper architecture",
            )
            assertContains(
                releaseInstructions,
                "ONLY_ACTIVE_ARCH=YES",
                message = "$path must document the qualified wrapper architecture policy",
            )
        }

        val appleGate = rootBuild.substringAfter("tasks.register(\"productionAppleCheck\")")
            .substringBefore("tasks.register(\"productionCheck\")")
        listOf(
            "productionAppleStaticAnalysis",
            ":composeApp:linkReleaseFrameworkIosArm64",
            ":composeApp:linkReleaseFrameworkIosSimulatorArm64",
            ":composeApp:linkReleaseFrameworkIosX64",
        ).forEach { task ->
            assertContains(
                appleGate,
                task,
                message = "productionAppleCheck must retain $task",
            )
        }
        listOf(
            "detektMetadataNativeMain",
            "detektMetadataAppleMain",
            "detektMetadataIosMain",
            "detektIosArm64Main",
            "detektIosArm64Test",
            "detektIosSimulatorArm64Main",
            "detektIosSimulatorArm64Test",
            "detektIosX64Main",
            "detektIosX64Test",
        ).forEach { task ->
            assertContains(rootBuild, task, message = "Apple type-aware analysis must retain $task")
        }
        assertContains(rootBuild, "tasks.named(\"productionAppleStaticAnalysis\")")
        assertContains(rootBuild, "tasks.matching { it.name in appleTypeAwareDetektTasks }.all")
    }

    @Test
    fun repository_contract_inputs_invalidate_cached_results_when_ci_or_build_gates_change() {
        val transportBuild = read("shared/transport-p2p/build.gradle.kts")

        listOf(
            "build.gradle.kts",
            ".github/workflows/production-verification.yml",
            "gradle/verification-metadata.xml",
            "iosApp/iosApp.xcodeproj/project.pbxproj",
        ).forEach { input ->
            assertContains(
                transportBuild,
                input,
                message =
                    "desktopTest must be invalidated when release contract input changes: $input",
            )
        }
    }

    @Test
    fun xcode_resource_artifacts_are_covered_by_strict_dependency_verification() {
        val metadata = read("gradle/verification-metadata.xml")
        val requiredArtifacts = mapOf(
            "koin-compose-4.0.0.kotlin_resources.zip" to
                "3972eaffbc58a5a5319ddc79a5c9d42c23dfdd760338bf7f3a105b6560ea563e",
            "koin-compose-viewmodel-4.0.0.kotlin_resources.zip" to
                "0ae6478b8a832ffc7eec4f662e6cc458586045770a0c2062df2f31333928e3e5",
            "library-1.10.3.kotlin_resources.zip" to
                "4f5c9f70b6260f466e6f72b7788afc1b7772b3801830e81f8d50dfba62c28077",
        )

        requiredArtifacts.forEach { (artifact, sha256) ->
            assertContains(metadata, "artifact name=\"$artifact\"")
            assertContains(metadata, "sha256 value=\"$sha256\"")
        }
    }

    @Test
    fun one_version_source_drives_android_desktop_ios_and_runtime_compatibility() {
        val versionConfig = read("config/parlor-version.xcconfig")
        val composeBuild = read("composeApp/build.gradle.kts")
        val contentModule = read("composeApp/src/commonMain/kotlin/com/parlor/app/di/ContentModule.kt")
        val xcodeConfig = read("iosApp/Configuration/Config.xcconfig")
        val xcodeProject = read("iosApp/iosApp.xcodeproj/project.pbxproj")

        assertContains(versionConfig, "PARLOR_VERSION_NAME = 1.0.0")
        assertContains(versionConfig, "PARLOR_BUILD_NUMBER = 1")
        assertContains(composeBuild, "versionCode = parlorBuildNumber")
        assertContains(composeBuild, "versionName = parlorVersionName")
        assertContains(composeBuild, "packageVersion = parlorVersionName")
        assertContains(composeBuild, "kotlin.srcDir(generateParlorVersion)")
        assertContains(contentModule, "SemVer.parse(PARLOR_VERSION_NAME)")
        assertContains(xcodeConfig, "../../config/parlor-version.xcconfig")
        assertContains(xcodeProject, "CURRENT_PROJECT_VERSION = \"$(PARLOR_BUILD_NUMBER)\"")
        assertContains(xcodeProject, "MARKETING_VERSION = \"$(PARLOR_VERSION_NAME)\"")
        assertFalse("MARKETING_VERSION = 1.0.0" in xcodeProject)
        assertFalse("CURRENT_PROJECT_VERSION = 1;" in xcodeProject)
    }

    @Test
    fun every_version_catalog_alias_has_a_real_build_consumer() {
        val catalog = read("gradle/libs.versions.toml")
        val bundledLibraries = catalogSectionAssignments(catalog, "bundles")
            .flatMap { assignment ->
                Regex("\"([^\"]+)\"").findAll(assignment.substringAfter('='))
                    .map { match -> match.groupValues[1] }
                    .toList()
            }
            .toSet()
        val buildScripts = repositoryRoot.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.name.endsWith(".gradle.kts") &&
                    file.invariantSeparatorsPath.let { path ->
                        "/build/" !in path && "/.gradle/" !in path
                    }
            }
            .joinToString(separator = "\n", transform = File::readText)

        mapOf(
            "libraries" to "libs.",
            "plugins" to "libs.plugins.",
            "bundles" to "libs.bundles.",
        ).forEach { (section, accessorPrefix) ->
            val missing = catalogAliases(catalog, section).filterNot { alias ->
                val accessor = accessorPrefix + alias.replace('-', '.')
                accessor in buildScripts || section == "libraries" && alias in bundledLibraries
            }
            assertTrue(
                missing.isEmpty(),
                "Unused [$section] aliases must be removed from the release catalog: $missing",
            )
        }

        val referencedVersions = Regex("version\\.ref\\s*=\\s*\"([^\"]+)\"")
            .findAll(catalog)
            .map { match -> match.groupValues[1] }
            .toSet()
        val missingVersions = catalogAliases(catalog, "versions").filterNot { alias ->
            alias in referencedVersions ||
                "libs.versions.${alias.replace('-', '.')}" in buildScripts ||
                "\"$alias\"" in buildScripts
        }
        assertTrue(
            missingVersions.isEmpty(),
            "Unused [versions] aliases must be removed from the release catalog: $missingVersions",
        )
    }

    private fun read(relativePath: String): String {
        val file = File(repositoryRoot, relativePath)
        assertTrue(file.isFile, "Missing release contract file: ${file.absolutePath}")
        return file.readText()
    }

    private fun catalogAliases(catalog: String, wantedSection: String): List<String> {
        return catalogSectionAssignments(catalog, wantedSection).map { assignment ->
            assignment.substringBefore('=').trim()
        }
    }

    private fun catalogSectionAssignments(
        catalog: String,
        wantedSection: String,
    ): List<String> {
        var section = ""
        return buildList {
            catalog.lineSequence().forEach { rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.startsWith('[') && line.endsWith(']')) {
                    section = line.removeSurrounding("[", "]")
                } else if (section == wantedSection && '=' in line) {
                    add(line)
                }
            }
        }
    }

    private fun locateRepositoryRoot(): File {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(12)
            .firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile &&
                    File(candidate, ".github/workflows/production-verification.yml").isFile
            }
        return assertNotNull(
            root,
            "Could not locate the Parlor repository root from ${File(".").absoluteFile}",
        )
    }
}
