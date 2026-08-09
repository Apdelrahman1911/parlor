plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Unlike the shared/game library modules, the Android application cannot use
// `parlor.kmp.library`, whose convention selects JUnit 5. Apply the same test
// runtime policy here so common tests use the repository's verified JUnit 5
// artifacts on JVM/Android instead of silently resolving JUnit 4.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}

// Release signing material is intentionally external to the repository.
// Environment variables are preferred in CI; equivalent Gradle properties
// make local store builds possible without changing this file.
val releaseStoreFile = providers.environmentVariable("PARLOR_ANDROID_KEYSTORE_PATH")
    .orElse(providers.gradleProperty("parlor.android.signing.storeFile"))
    .orNull
val releaseStorePassword = providers.environmentVariable("PARLOR_ANDROID_KEYSTORE_PASSWORD")
    .orElse(providers.gradleProperty("parlor.android.signing.storePassword"))
    .orNull
val releaseKeyAlias = providers.environmentVariable("PARLOR_ANDROID_KEY_ALIAS")
    .orElse(providers.gradleProperty("parlor.android.signing.keyAlias"))
    .orNull
val releaseKeyPassword = providers.environmentVariable("PARLOR_ANDROID_KEY_PASSWORD")
    .orElse(providers.gradleProperty("parlor.android.signing.keyPassword"))
    .orNull
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

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
            dependencies {
                // Shared layers
                implementation(project(":shared:core"))
                implementation(project(":shared:engine"))
                implementation(project(":shared:design-system"))
                implementation(project(":shared:session"))
                implementation(project(":shared:content"))
                implementation(project(":shared:networking"))
                implementation(project(":shared:storage"))
                implementation(project(":shared:transport-p2p"))

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

                // Kotlinx
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":shared:engine-testing"))
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// Release Kotlin/Native LTO is memory intensive. With Gradle parallelism
// enabled, linking multiple Compose frameworks in one JVM can exhaust even the
// configured 6 GiB heap although every target links successfully in isolation.
// Keep compilation parallel, but serialize only the three release link tasks
// that form the production Apple gate.
tasks.named("linkReleaseFrameworkIosSimulatorArm64") {
    mustRunAfter("linkReleaseFrameworkIosArm64")
}
tasks.named("linkReleaseFrameworkIosX64") {
    mustRunAfter(
        "linkReleaseFrameworkIosArm64",
        "linkReleaseFrameworkIosSimulatorArm64",
    )
}

android {
    namespace = "com.parlor.app"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.parlor.app"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                // Preserve native symbols for Play Console crash symbolication.
                debugSymbolLevel = "FULL"
            }
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    // Case JSON lives inside the Whodunit module's Compose Multiplatform
    // resources, not in app-level Android assets. See game-modes/whodunit/.
}

// The release target and pinned toolchain are deliberate compatibility
// choices. Lint's update advisories are reviewed in
// docs/ANDROID_LINT_TRIAGE.md; a new correctness/packaging warning must fail
// the release gate instead of being silently accepted.
val verifyReleaseLintWarnings by tasks.registering {
    group = "verification"
    description = "Fails if release lint reports an untriaged warning."
    dependsOn("lintRelease")
    inputs.file(layout.buildDirectory.file("reports/lint-results-release.xml"))
    doLast {
        val report = inputs.files.singleFile
        check(report.isFile) { "Missing release lint report: ${report.absolutePath}" }

        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(report)
        val allowedAdvisories = setOf(
            "OldTargetApi",
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        )
        val issues = document.getElementsByTagName("issue")
        val untriaged = buildList {
            for (index in 0 until issues.length) {
                val issue = issues.item(index)
                val id = issue.attributes.getNamedItem("id")?.nodeValue.orEmpty()
                if (id !in allowedAdvisories) add(id)
            }
        }
        check(untriaged.isEmpty()) {
            "Release lint contains untriaged issue IDs: ${untriaged.distinct().sorted()}"
        }
    }
}

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails unless store-release Android signing is configured externally."
    // The task deliberately inspects process/environment credentials and a
    // filesystem path supplied by the protected release runner. It is an
    // external gate, not a cacheable build input.
    notCompatibleWithConfigurationCache(
        "Signing credentials and the keystore are external release-runner inputs",
    )
    doLast {
        check(releaseSigningConfigured) {
            """
            Android store signing is not configured. Set all of:
              PARLOR_ANDROID_KEYSTORE_PATH
              PARLOR_ANDROID_KEYSTORE_PASSWORD
              PARLOR_ANDROID_KEY_ALIAS
              PARLOR_ANDROID_KEY_PASSWORD
            (or the matching parlor.android.signing.* Gradle properties).
            """.trimIndent()
        }
        check(rootProject.file(requireNotNull(releaseStoreFile)).isFile) {
            "PARLOR_ANDROID_KEYSTORE_PATH does not identify a readable file."
        }
    }
}

// `bundleRelease` is intentionally an unsigned verification artifact when no
// external key is configured. Store delivery must use the explicit signing
// gate below; keeping the two artifacts separate lets CI exercise shrinking,
// packaging, and lint without ever inventing or checking in a key.
val verifyStoreRelease by tasks.registering {
    group = "verification"
    description = "Verifies external signing material before a store release."
    notCompatibleWithConfigurationCache(
        "The dependent signing gate uses external release-runner inputs",
    )
    dependsOn(verifyReleaseSigning, "bundleRelease")
}

/**
 * Architectural guard for FR-01: global shell dispatch must remain game-id
 * neutral. Concrete game names belong only in binding implementations and the
 * composition root where those bindings are registered.
 */
val verifyGameShellDispatch by tasks.registering {
    group = "verification"
    description = "Rejects game-specific branches in central shell dispatch files."
    val neutralShellPaths = listOf(
        "src/commonMain/kotlin/com/parlor/app/App.kt",
        "src/commonMain/kotlin/com/parlor/app/AppBackPolicy.kt",
        "src/commonMain/kotlin/com/parlor/app/LocalResumeRouter.kt",
        "src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt",
    )
    val neutralShellSources = neutralShellPaths.map(::file)
    val multiplayerShellSources = fileTree(
        "src/commonMain/kotlin/com/parlor/app/shell/multiplayer",
    ).matching { include("**/*.kt") }
    inputs.files(neutralShellSources, multiplayerShellSources)
    doLast {
        val forbidden = listOf("whodunit", "mafia", "com.parlor.games.")
        inputs.files.files.filter { file -> file.isFile }.forEach { source ->
            val text = source.readText().lowercase()
            val found = forbidden.filter { token -> token in text }
            check(found.isEmpty()) {
                "Central shell file ${source.name} contains game-specific " +
                    "dispatch tokens: ${found.joinToString()}"
            }
        }
    }
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
