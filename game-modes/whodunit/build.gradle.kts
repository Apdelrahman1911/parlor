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
            implementation(project(":shared:navigation"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
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
            implementation(libs.bundles.ktor.common)
            implementation(libs.ktor.client.mock)
            // Phase 7 shape test needs networking protocol types
            // (HostMessage / PeerMessage) directly; production whodunit code
            // does not reach into the transport layer.
            implementation(project(":shared:networking"))
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
