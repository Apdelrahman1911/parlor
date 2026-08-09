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

// Desktop contract tests inspect repository-owned documentation and platform
// configuration outside this module's source sets. Declare those files as
// inputs so Gradle cannot reuse a stale PASS after a runbook, manifest, plist,
// coordinate, or checksum changes.
tasks.named("desktopTest") {
    inputs.files(
        rootProject.file("README.md"),
        rootProject.file("ARCHITECTURE.md"),
        rootProject.file("build.gradle.kts"),
        rootProject.file(".github/workflows/production-verification.yml"),
        rootProject.file("iosApp/iosApp.xcodeproj/project.pbxproj"),
        rootProject.file("whodunit-game-design.md"),
        rootProject.file("settings.gradle.kts"),
        rootProject.file("gradle/libs.versions.toml"),
        rootProject.file("gradle/verification-metadata.xml"),
        rootProject.file("composeApp/src/androidMain/AndroidManifest.xml"),
        rootProject.file("composeApp/build.gradle.kts"),
        rootProject.fileTree("composeApp/src/androidMain/res") {
            include("**/*")
        },
        rootProject.fileTree("composeApp/src/commonMain/composeResources") {
            include("**/*.xml")
        },
        rootProject.file("composeApp/src/androidMain/res/xml/backup_rules.xml"),
        rootProject.file("composeApp/src/androidMain/res/xml/data_extraction_rules.xml"),
        rootProject.file("iosApp/iosApp/Info.plist"),
        rootProject.fileTree("docs") {
            include("**/*.md")
        },
    )
        .withPropertyName("repositoryContractFiles")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}
