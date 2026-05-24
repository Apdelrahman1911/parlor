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
            implementation("dev.p2pkit:p2p-core:0.6.0")
            implementation("dev.p2pkit:p2p-transport-lan:0.6.0")
            implementation(libs.koin.core)
        }
        // Koin Android exposes androidContext() which we need for
        // AndroidKitFactory's lan(Context) call.
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.assertk)
        }
    }
}
