// Root build script. Per-module configuration lives in each module's build.gradle.kts;
// shared conventions are extracted into precompiled plugins under :build-logic:convention.

plugins {
    id("parlor.detekt") apply false
    // Declare here only — apply false. Modules opt in via convention plugins or directly.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
}

// Explicit repository-wide test aggregate. Gradle's task-name abbreviation
// previously made `allTests` depend on whichever module tasks happened to be
// selected by the caller. Keeping a real root task makes the CI contract
// deterministic and automatically includes every KMP module added later.
val allTests = tasks.register("allTests") {
    group = "verification"
    description = "Runs every configured KMP allTests task."
}

// Stable, documented verification entry points. New KMP modules join the
// desktop suite automatically when they apply the multiplatform plugin.
val productionDesktopCheck = tasks.register("productionDesktopCheck") {
    group = "verification"
    description = "Runs every KMP desktop test plus the desktop application compile."
}

val productionIosSimulatorRuntimeTests = tasks.register("productionIosSimulatorRuntimeTests") {
    group = "verification"
    description = "Runs every KMP iosSimulatorArm64 runtime test."
}

val productionAndroidCheck = tasks.register("productionAndroidCheck") {
    group = "verification"
    description = "Runs Android debug/release unit tests and builds, shrinks, and lints the unsigned release bundle."
    dependsOn(
        ":composeApp:testDebugUnitTest",
        ":composeApp:testReleaseUnitTest",
        ":composeApp:compileReleaseKotlinAndroid",
        ":composeApp:minifyReleaseWithR8",
        ":composeApp:bundleRelease",
        ":composeApp:lintRelease",
        ":composeApp:verifyReleaseLintWarnings",
        ":composeApp:verifyMergedReleaseManifest",
        ":composeApp:verifyApplicationIdentities",
    )
}

val productionAndroidSigningCheck = tasks.register("productionAndroidSigningCheck") {
    group = "verification"
    description = "Verifies protected Android signing material and builds the store bundle."
    dependsOn(":composeApp:verifyStoreRelease")
}

val productionReleaseAutomationCheck = tasks.register<Exec>("productionReleaseAutomationCheck") {
    group = "verification"
    description = "Runs release provenance, workflow-security, and no-rebuild promotion tests."
    workingDir(rootProject.projectDir)
    commandLine("bash", "scripts/release/validate_release_system.sh")
}

val staticAnalysis = tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs Detekt over application modules and the included convention-plugin build."
    dependsOn(gradle.includedBuild("build-logic").task(":convention:detekt"))
}

// Detekt's plain task provides complete source-file coverage, but it cannot run
// rules that require the Kotlin compiler's type information for KMP source
// sets. Keep explicit host families so CI executes the type-aware tasks on a
// host that can resolve each platform. The subproject wiring below is based on
// task registration, so a new KMP module joins these gates automatically.
val hostTypeAwareDetektTasks = setOf(
    "detektMetadataCommonMain",
    "detektDesktopMain",
    "detektDesktopTest",
    "detektAndroidRelease",
    "detektAndroidReleaseUnitTest",
)
val androidInstrumentedTypeAwareDetektTask = "detektAndroidDebugAndroidTest"
val appleTypeAwareDetektTasks = setOf(
    "detektMetadataNativeMain",
    "detektMetadataAppleMain",
    "detektMetadataIosMain",
    "detektIosArm64Main",
    "detektIosArm64Test",
    "detektIosSimulatorArm64Main",
    "detektIosSimulatorArm64Test",
    "detektIosX64Main",
    "detektIosX64Test",
)

val typeAwareStaticAnalysis = tasks.register("typeAwareStaticAnalysis") {
    group = "verification"
    description = "Runs type-aware Detekt for common metadata, desktop, and Android source sets."
}

val productionAppleStaticAnalysis = tasks.register("productionAppleStaticAnalysis") {
    group = "verification"
    description = "Runs type-aware Detekt for every supported iOS production/test target."
}

// Detekt exposes compiler-backed metadata tasks for hierarchical production
// source sets, but not for commonTest/iosTest. The plain `detekt` task still
// scans every authored test file; Kotlin compilation and the executable test
// matrix provide their type/runtime validation. Leaf test tasks remain wired
// so target-specific tests are analysed whenever such sources exist.

// Stable release-facing name. Keep the shorter staticAnalysis task for local
// use, while automation and release evidence can invoke an unambiguous gate.
val productionStaticAnalysis = tasks.register("productionStaticAnalysis") {
    group = "verification"
    description = "Runs the complete repository-wide production static-analysis policy."
    dependsOn(staticAnalysis, typeAwareStaticAnalysis)
}

tasks.register("productionAppleCheck") {
    group = "verification"
    description = "Runs Apple type-aware analysis and links release frameworks for all supported iOS targets."
    dependsOn(
        productionAppleStaticAnalysis,
        ":composeApp:linkReleaseFrameworkIosArm64",
        ":composeApp:linkReleaseFrameworkIosSimulatorArm64",
        ":composeApp:linkReleaseFrameworkIosX64",
    )
}

tasks.register("productionCheck") {
    group = "verification"
    description = "Runs host-independent release gates; run productionAppleCheck on macOS separately."
    dependsOn(
        productionDesktopCheck,
        productionAndroidCheck,
        productionReleaseAutomationCheck,
        productionStaticAnalysis,
        ":composeApp:verifyGameShellDispatch",
    )
}

subprojects {
    // Every included application/library module is production-relevant. Apply
    // static analysis here instead of relying on each module to opt in; a new
    // module therefore receives the gate automatically.
    pluginManager.apply("parlor.detekt")
    rootProject.tasks.named("staticAnalysis").configure {
        dependsOn(tasks.named("detekt"))
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        tasks.matching { it.name in hostTypeAwareDetektTasks }.all {
            val sourceSetDetekt = this
            rootProject.tasks.named("typeAwareStaticAnalysis").configure {
                dependsOn(sourceSetDetekt)
            }
        }
        val androidInstrumentedSources = fileTree("src") {
            include("androidInstrumentedTest/**/*.kt", "androidTest/**/*.kt")
        }
        if (!androidInstrumentedSources.isEmpty) {
            tasks.matching { it.name == androidInstrumentedTypeAwareDetektTask }.all {
                val sourceSetDetekt = this
                rootProject.tasks.named("typeAwareStaticAnalysis").configure {
                    dependsOn(sourceSetDetekt)
                }
            }
        }
        tasks.matching { it.name in appleTypeAwareDetektTasks }.all {
            val sourceSetDetekt = this
            rootProject.tasks.named("productionAppleStaticAnalysis").configure {
                dependsOn(sourceSetDetekt)
            }
        }

        // Some KMP application modules expose only desktopTest. Join the
        // aggregate when an allTests task is actually registered rather than
        // making configuration fail for those valid modules.
        tasks.matching { it.name == "allTests" }.configureEach {
            val moduleAllTests = this
            rootProject.tasks.named("allTests").configure {
                dependsOn(moduleAllTests)
            }
        }
        tasks.matching { it.name == "iosSimulatorArm64Test" }.configureEach {
            val moduleIosSimulatorTest = this
            rootProject.tasks.named("productionIosSimulatorRuntimeTests").configure {
                dependsOn(moduleIosSimulatorTest)
            }
        }
        rootProject.tasks.named("productionDesktopCheck").configure {
            dependsOn(tasks.named("desktopTest"))
        }
    }
}
