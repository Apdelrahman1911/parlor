plugins {
    id("parlor.kmp.compose.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(project(":shared:engine"))
            implementation(project(":shared:design-system"))
            implementation(project(":shared:session"))
            implementation(project(":shared:content"))
            implementation(project(":shared:storage"))
            implementation(project(":shared:networking"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.assertk)
            implementation(libs.koin.test)
            implementation(libs.bundles.ktor.json.client)
            implementation(libs.ktor.client.mock)
            // Multiplayer shape tests use protocol types and the isolated
            // in-process room fixture without exposing either as app-shell API.
            implementation(project(":shared:networking"))
            implementation(project(":shared:networking-testing"))
        }
    }
}

// Compose Multiplatform resources — bundled case JSON lives once under
// commonMain/composeResources/files/cases/<id>.json and is loaded uniformly
// across Android/iOS/Desktop via `Res.readBytes(...)`. The generated `Res`
// accessor is exposed at this package so call sites read cleanly.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.parlor.games.whodunit.resources"
}
