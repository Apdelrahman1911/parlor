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
            implementation(project(":shared:storage"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.p2pkit.core)
            implementation(libs.p2pkit.transport.lan)
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
