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
