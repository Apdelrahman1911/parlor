plugins {
    id("parlor.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(project(":shared:networking"))
            implementation(project(":shared:session"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // P2pKit artifacts — resolved from mavenLocal after the user
            // runs `./gradlew publishToMavenLocal` inside ../P2pKit.
            //
            // The version below should match what P2pKit publishes; bump
            // when consuming a newer P2pKit. Until P2pKit ships a maven-publish
            // configuration the user has to add the plugin and apply
            // `group = "dev.p2pkit"; version = "0.6.0"` to its modules.
            implementation("dev.p2pkit:p2p-core:0.6.0")
            implementation("dev.p2pkit:p2p-transport-lan:0.6.0")
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.assertk)
        }
    }
}
