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
        assertContains(
            staticGate,
            "Runs the host-independent production static-analysis policy; run " +
                "productionAppleStaticAnalysis on macOS for iOS source sets.",
        )
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
    fun supported_desktop_hosts_execute_real_host_selected_gradle_graphs() {
        val workflow = read(".github/workflows/production-verification.yml")
        listOf(
            "  desktop-linux-arm64:",
            "  desktop-macos-x64:",
            "  desktop-windows-x64:",
        ).forEach { job -> assertContains(workflow, job) }

        val linuxArm64Job = workflow
            .substringAfter("  desktop-linux-arm64:")
            .substringBefore("  desktop-macos-x64:")
        listOf(
            "runs-on: ubuntu-24.04-arm",
            "test \"\$(uname -s)\" = \"Linux\"",
            "test \"\$(uname -m)\" = \"aarch64\"",
            "./gradlew productionDesktopCheck",
            "--dependency-verification=strict",
        ).forEach { contract -> assertContains(linuxArm64Job, contract) }
        assertFalse(
            "downloadKotlinNativeDistribution" in linuxArm64Job,
            "Linux arm64 support is Desktop-only because Kotlin Native has no Linux arm64 host distribution",
        )

        val macosX64Job = workflow
            .substringAfter("  desktop-macos-x64:")
            .substringBefore("  desktop-windows-x64:")
        listOf(
            "runs-on: macos-15-intel",
            "test \"\$(uname -s)\" = \"Darwin\"",
            "test \"\$(uname -m)\" = \"x86_64\"",
            "./gradlew productionDesktopCheck :composeApp:downloadKotlinNativeDistribution",
            "--dependency-verification=strict",
        ).forEach { contract -> assertContains(macosX64Job, contract) }

        val windowsX64Job = workflow
            .substringAfter("  desktop-windows-x64:")
            .substringBefore("  ios:")
        listOf(
            "runs-on: windows-2025",
            "RuntimeInformation]::OSArchitecture",
            "Architecture]::X64",
            ".\\gradlew.bat productionDesktopCheck",
            ":composeApp:downloadKotlinNativeDistribution",
            ":composeApp:processDebugResources",
            "--dependency-verification=strict",
        ).forEach { contract -> assertContains(windowsX64Job, contract) }

        assertFalse(
            "detachedConfiguration" in workflow,
            "Cross-host CI must execute the real build graphs rather than detached coordinates",
        )
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
            componentMarker = "<component group=\"org.junit\" name=\"junit-bom\" version=\"5.9.2\">",
            artifactName = "junit-bom-5.9.2.module",
            sha256 = "ab137ba5a8e32c9b066bf9126a1c76dd5614b724ba5c0b02549772b5e9f4cf1f",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.junit\" name=\"junit-bom\" version=\"5.11.0-M2\">",
            artifactName = "junit-bom-5.11.0-M2.module",
            sha256 = "86477abcf490d6ca059aa9973cb108d22a506f49d1a5569bb32cc6cbf43c2cce",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.kotlin\" name=\"kotlin-native-prebuilt\" version=\"2.4.10\">",
            artifactName = "kotlin-native-prebuilt-2.4.10-linux-x86_64.tar.gz",
            sha256 = "c9e356e8518144f275f1514cfe38b07db949f93e47e054832b8974fff1fd33e0",
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
        verifyComponent(
            componentMarker =
                "<component group=\"com.android.tools.build\" name=\"aapt2\" version=\"8.13.2-14304508\">",
            artifactName = "aapt2-8.13.2-14304508-linux.jar",
            sha256 = "839609d6d776d6dd60a02aa577d97193ce3e650cf1deaabf062321e23bbd6bf6",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.compose.desktop\" name=\"desktop-jvm-linux-x64\" version=\"1.10.3\">",
            artifactName = "desktop-jvm-linux-x64-1.10.3.pom",
            sha256 = "5a6361c112715bea2334898e208ec82640863d592287c2828b9edf56d5d733a9",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.skiko\" name=\"skiko-awt-runtime-linux-x64\" version=\"0.9.37.4\">",
            artifactName = "skiko-awt-runtime-linux-x64-0.9.37.4.jar",
            sha256 = "ec796df135d980bbb1740e789fe8668a8184df243e4d1c39997750303c76f13b",
        )
        verifyComponent(
            componentMarker =
                "<component group=\"org.jetbrains.skiko\" name=\"skiko-awt-runtime-linux-x64\" version=\"0.9.37.4\">",
            artifactName = "skiko-awt-runtime-linux-x64-0.9.37.4.pom",
            sha256 = "39d4d3b389fb0191258e0202b7b0de6b09210509bcd6168b2774a5b24f2ed6a2",
        )
    }

    @Test
    fun apple_job_runs_kotlin_and_app_launch_tests_all_link_targets_and_unsigned_swift_release() {
        val workflow = read(".github/workflows/production-verification.yml")
        val rootBuild = read("build.gradle.kts")
        val composeBuild = read("composeApp/build.gradle.kts")
        val xcodeProject = read("iosApp/iosApp.xcodeproj/project.pbxproj")
        val xcodeScheme = read("iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme")
        val appLaunchTest = read("iosApp/iosAppUITests/IOSAppLaunchUITests.swift")
        val homeScreen = read(
            "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt",
        )
        val frameworkNormalizer = read(
            "scripts/release/normalize_embedded_apple_framework.sh",
        )

        listOf(
            "DEVELOPER_DIR: /Applications/Xcode_26.3.app/Contents/Developer",
            "Xcode 26.3",
            "Build version 17C529",
            ".toolchains.apple.minimum_ios_sdk_major",
            "xcrun --sdk iphoneos --show-sdk-version",
            "xcrun --sdk iphonesimulator --show-sdk-version",
            "productionIosSimulatorRuntimeTests",
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

        val appleTestStep = workflow
            .substringAfter("- name: Run iOS simulator tests, Apple static analysis, and release linkage gates")
            .substringBefore("- name: Validate iOS plist and privacy manifest")
        assertContains(
            appleTestStep,
            "./gradlew productionIosSimulatorRuntimeTests productionAppleCheck",
            message = "Apple CI must run the dedicated executable simulator aggregate before linkage gates",
        )
        assertFalse(
            "./gradlew allTests" in appleTestStep,
            "Apple CI must not duplicate the Linux common/desktop/Android aggregate",
        )

        val simulatorRuntimeGate = rootBuild
            .substringAfter("val productionIosSimulatorRuntimeTests")
            .substringBefore("val productionAndroidCheck")
        assertContains(simulatorRuntimeGate, "Runs every KMP iosSimulatorArm64 runtime test")
        assertContains(rootBuild, "it.name == \"iosSimulatorArm64Test\"")
        assertContains(rootBuild, "tasks.named(\"productionIosSimulatorRuntimeTests\")")
        assertContains(
            xcodeProject,
            "./gradlew --no-daemon :composeApp:embedAndSignAppleFrameworkForXcode " +
                "--dependency-verification=strict",
            message = "The Xcode shell phase must enforce strict dependency verification",
        )
        assertContains(
            composeBuild,
            "isStatic = false",
            message = "The launchable app must embed a dynamic Kotlin framework, not a static archive",
        )
        assertContains(
            xcodeProject,
            "scripts/release/normalize_embedded_apple_framework.sh",
            message = "The Xcode shell phase must normalize the embedded framework's case",
        )
        listOf(
            "canonical_name=\"\${framework_base_name}.framework\"",
            "candidate_lowercase=",
            "match_count=\$((match_count + 1))",
            ".case-normalization",
            "/bin/mv \"\$match\" \"\$temporary_framework\"",
            "embedded framework executable does not match its Mach-O install name",
        ).forEach { required ->
            assertContains(
                frameworkNormalizer,
                required,
                message = "The Apple build must preserve the framework's case-sensitive identity",
            )
        }

        val appLaunchMarker = "- name: Launch Swift host and Compose root on iOS Simulator"
        val swiftReleaseMarker = "- name: Build unsigned Swift Release wrapper"
        assertContains(workflow, appLaunchMarker)
        val appLaunchStep = workflow
            .substringAfter(appLaunchMarker)
            .substringBefore(swiftReleaseMarker)
        listOf(
            "xcrun simctl list devices available --json",
            "-configuration Debug",
            "platform=iOS Simulator,id=\$simulator_udid",
            "-resultBundlePath build/ci-evidence/ios-ui-tests.xcresult",
            "test | tee build/ci-evidence/xcode-ui-test.log",
        ).forEach { required ->
            assertContains(
                appLaunchStep,
                required,
                message = "Apple CI must launch the real app through XCTest: $required",
            )
        }
        listOf(
            "com.apple.product-type.bundle.ui-testing",
            "IOSAppLaunchUITests.swift in Sources",
            "TEST_TARGET_NAME = iosApp",
        ).forEach { required -> assertContains(xcodeProject, required) }
        listOf(
            "TestableReference",
            "BlueprintName = \"iosAppUITests\"",
        ).forEach { required -> assertContains(xcodeScheme, required) }
        listOf(
            "XCUIApplication()",
            "runningForeground",
            "staticTexts[\"parlor-home-brand\"]",
            "No alert should appear during the simulator cold-start observation window",
            "The Swift host should remain in the foreground after Compose renders",
        ).forEach { required -> assertContains(appLaunchTest, required) }
        listOf(
            "HOME_BRAND_TEST_TAG = \"parlor-home-brand\"",
            ".testTag(HOME_BRAND_TEST_TAG)",
        ).forEach { required ->
            assertContains(
                homeScreen,
                required,
                message = "The iOS launch probe must target a stable Compose home marker",
            )
        }

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
            "iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme",
            "iosApp/iosAppUITests/IOSAppLaunchUITests.swift",
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
