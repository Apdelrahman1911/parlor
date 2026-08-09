package com.parlor.transport.p2p

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
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
            "allTests",
            "--dependency-verification=strict",
            "unzip -t",
            "base/manifest/AndroidManifest.xml",
            "base/dex/classes.dex",
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
            ":composeApp:minifyReleaseWithR8",
            ":composeApp:bundleRelease",
        ).forEach { task ->
            assertContains(
                androidGate,
                task,
                message = "productionAndroidCheck must enforce $task",
            )
        }

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
    fun apple_job_runs_simulator_tests_all_link_targets_and_unsigned_swift_release() {
        val workflow = read(".github/workflows/production-verification.yml")
        val rootBuild = read("build.gradle.kts")
        val xcodeProject = read("iosApp/iosApp.xcodeproj/project.pbxproj")

        listOf(
            "allTests",
            "productionAppleCheck",
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

        val appleGate = rootBuild.substringAfter("tasks.register(\"productionAppleCheck\")")
            .substringBefore("tasks.register(\"productionCheck\")")
        listOf(
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
        return assertNotNull(
            root,
            "Could not locate the Parlor repository root from ${File(".").absoluteFile}",
        )
    }
}
