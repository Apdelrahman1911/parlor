plugins {
    id("parlor.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            // Content needs the engine's GameRegistry / GameDefinition to validate
            // envelopes against installed modules (ARCHITECTURE.md §3.2).
            implementation(project(":shared:engine"))
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":shared:engine-testing"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.assertk)
            implementation(libs.bundles.ktor.json.client)
            implementation(libs.ktor.client.mock)
        }
    }
}
