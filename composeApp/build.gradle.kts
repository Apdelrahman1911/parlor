plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Shared layers
            implementation(project(":shared:core"))
            implementation(project(":shared:engine"))
            implementation(project(":shared:design-system"))
            implementation(project(":shared:session"))
            implementation(project(":shared:content"))
            implementation(project(":shared:networking"))
            implementation(project(":shared:storage"))
            implementation(project(":shared:navigation"))

            // Game modules
            implementation(project(":game-modes:whodunit"))

            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Ktor (mock-engine wiring lives in androidMain/desktopMain DI bindings)
            implementation(libs.bundles.ktor.common)

            // Kotlinx
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            implementation("androidx.activity:activity-compose:1.9.3")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

android {
    namespace = "com.parlor.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.parlor.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Case JSON lives inside the Whodunit module's Compose Multiplatform
    // resources, not in app-level Android assets. See game-modes/whodunit/.
}

// Compose Multiplatform resources for shell strings (Home, settings, etc.).
compose.resources {
    publicResClass = true
    packageOfResClass = "com.parlor.app.resources"
}

compose.desktop {
    application {
        mainClass = "com.parlor.app.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "Parlor"
            packageVersion = "1.0.0"
        }
    }
}
