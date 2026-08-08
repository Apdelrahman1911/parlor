plugins {
    id("parlor.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            // Phase 8: the protocol's SessionStarting message references
            // com.parlor.engine.state.Player so the same engine type carries
            // through host -> peer setup.
            api(project(":shared:engine"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.cbor)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.assertk)
        }
    }
}
