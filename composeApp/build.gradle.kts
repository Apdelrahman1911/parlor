plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Phase 8 opt-in: when parlor.p2p.enabled=true the composeApp depends on the
// :shared:transport-p2p adapter and compiles the `p2pEnabledMain` source set,
// which exposes `p2pBootstrapModules()` returning the real Koin transport
// module. With the flag off the alternate `p2pDisabledMain` source set
// returns an empty list and no P2pKit symbol is ever referenced.
//
// Resolution mirrors settings.gradle.kts: strip inline `#` comments (.properties
// files don't do that natively) and trim before parsing. Auto-detect from
// mavenLocal when the property isn't set explicitly.
val p2pEnabled: Boolean = run {
    val raw = project.findProperty("parlor.p2p.enabled") as? String
    val explicit = raw?.substringBefore('#')?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { it.toBooleanStrictOrNull() ?: it.toBoolean() }
    if (explicit != null) return@run explicit
    val home = System.getProperty("user.home").orEmpty()
    if (home.isBlank()) return@run false
    val core = file("$home/.m2/repository/dev/p2pkit/p2p-core/0.6.0/p2p-core-0.6.0.jar")
    val lan = file("$home/.m2/repository/dev/p2pkit/p2p-transport-lan/0.6.0/p2p-transport-lan-0.6.0.module")
    core.exists() && lan.exists()
}
println("[parlor:composeApp] P2pKit dep: ${if (p2pEnabled) "ON" else "OFF"}")

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
        commonMain {
            // One of these two source dirs supplies `p2pBootstrapModules()`;
            // the inactive directory's file is simply not seen by the compiler.
            kotlin.srcDir(
                if (p2pEnabled) "src/p2pEnabledMain/kotlin" else "src/p2pDisabledMain/kotlin",
            )
            dependencies {
                // Shared layers
                implementation(project(":shared:core"))
                implementation(project(":shared:engine"))
                implementation(project(":shared:design-system"))
                implementation(project(":shared:session"))
                implementation(project(":shared:content"))
                implementation(project(":shared:networking"))
                implementation(project(":shared:storage"))
                implementation(project(":shared:navigation"))
                if (p2pEnabled) {
                    implementation(project(":shared:transport-p2p"))
                }

                // Game modules
                implementation(project(":game-modes:whodunit"))
                implementation(project(":game-modes:mafia"))

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

                // Ktor + the in-process MockEngine for dev. Production builds
                // swap MockEngine for a real platform engine.
                implementation(libs.bundles.ktor.common)
                implementation(libs.ktor.client.mock)

                // Kotlinx
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
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
    compileSdk = 36
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
