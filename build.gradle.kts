// Root build script. Per-module configuration lives in each module's build.gradle.kts;
// shared conventions are extracted into precompiled plugins under :build-logic:convention.

plugins {
    // Declare here only — apply false. Modules opt in via convention plugins or directly.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt) apply false
}

// Stable, documented verification entry points. New KMP modules join the
// desktop suite automatically when they apply the multiplatform plugin.
val productionDesktopCheck = tasks.register("productionDesktopCheck") {
    group = "verification"
    description = "Runs every KMP desktop test plus the desktop application compile."
}

val productionAndroidCheck = tasks.register("productionAndroidCheck") {
    group = "verification"
    description = "Builds and lints the unsigned Android release bundle."
    dependsOn(":composeApp:bundleRelease", ":composeApp:lintRelease")
}

val productionAndroidSigningCheck = tasks.register("productionAndroidSigningCheck") {
    group = "verification"
    description = "Verifies protected Android signing material and builds the store bundle."
    dependsOn(":composeApp:verifyStoreRelease")
}

tasks.register("productionAppleCheck") {
    group = "verification"
    description = "Links release frameworks for physical and Apple-silicon simulator iOS targets."
    dependsOn(
        ":composeApp:linkReleaseFrameworkIosArm64",
        ":composeApp:linkReleaseFrameworkIosSimulatorArm64",
    )
}

tasks.register("productionCheck") {
    group = "verification"
    description = "Runs host-independent release gates; run productionAppleCheck on macOS separately."
    dependsOn(productionDesktopCheck, productionAndroidCheck)
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        rootProject.tasks.named("productionDesktopCheck").configure {
            dependsOn(tasks.named("desktopTest"))
        }
    }
}
