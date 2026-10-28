import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("parlor.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

// Architecture tests inspect declared build files and Main Kotlin inputs, not
// arbitrary copies below the checkout. FileTrees retain compiler include/exclude
// patterns and include newly created source; the index is never a Git-only list.
gradle.projectsEvaluated {
    val checkout = rootProject.projectDir
    val buildFiles = rootProject.allprojects.map { it.path to it.buildFile }
    val mainSources = rootProject.allprojects.flatMap { candidate ->
        candidate.extensions.findByType(KotlinMultiplatformExtension::class.java)
            ?.sourceSets?.filter { it.name.endsWith("Main") }
            ?.map { candidate.path to it.kotlin.asFileTree.matching { include("**/*.kt") } }
            .orEmpty()
    }
    val index = layout.buildDirectory.file("source-contracts/declared-inputs.bin")
    val export = tasks.register("exportArchitectureContractInputs") {
        inputs.files(buildFiles.map { it.second }, mainSources.map { it.second })
            .withPropertyName("declaredArchitectureInputs")
        inputs.property("projectPaths", buildFiles.map { it.first })
        outputs.file(index)
        doLast {
            val records = (
                buildFiles.filter { it.second.isFile }.map { Triple('B', it.first, it.second) } +
                    mainSources.flatMap { (owner, sources) -> sources.files.map { Triple('S', owner, it) } }
                ).map { (kind, owner, file) ->
                Triple(kind, owner, file.relativeTo(checkout).invariantSeparatorsPath)
            }.distinct().sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
            check(records.size in 1..20_000) { "Declared architecture input count exceeds bound" }
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { stream ->
                stream.writeInt(0x50535243)
                stream.writeInt(1)
                stream.writeInt(records.size)
                records.forEach { (kind, owner, relative) ->
                    stream.writeChar(kind.code)
                    stream.writeUTF(owner)
                    stream.writeUTF(relative)
                }
            }
            check(bytes.size() <= 1_048_576) { "Declared architecture input bytes exceed bound" }
            val output = index.get().asFile
            check(output.parentFile.isDirectory || output.parentFile.mkdirs())
            output.writeBytes(bytes.toByteArray())
        }
    }
    tasks.withType<Test>().configureEach {
        dependsOn(export)
        inputs.file(index).withPropertyName("declaredArchitectureIndex")
        systemProperty("parlor.contract.sourceIndex", index.get().asFile.absolutePath)
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(project(":shared:networking"))
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
        rootProject.file("CLAUDE.md"),
        rootProject.file("docs/archives/history/ARCHITECTURE.md"),
        rootProject.file("build.gradle.kts"),
        rootProject.file(".github/workflows/production-verification.yml"),
        rootProject.file("iosApp/iosApp.xcodeproj/project.pbxproj"),
        rootProject.file("iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme"),
        rootProject.file("iosApp/iosAppUITests/IOSAppLaunchUITests.swift"),
        rootProject.file("docs/archives/history/whodunit-game-design.md"),
        rootProject.file("settings.gradle.kts"),
        rootProject.file("build-logic/settings.gradle.kts"),
        rootProject.fileTree("build-logic/convention/src/main/kotlin") {
            include("**/*.kt", "**/*.kts")
        },
        rootProject.file("gradle.properties"),
        rootProject.file("config/android-lint-accepted-warnings.txt"),
        rootProject.fileTree(".") {
            include("**/build.gradle.kts")
            exclude("**/build/**")
        },
        rootProject.fileTree("shared/session/src/commonMain") {
            include("**/*.kt")
        },
        rootProject.fileTree("shared/networking-testing/src/commonMain") {
            include("**/*.kt")
        },
        rootProject.fileTree("shared/engine/src/commonMain") {
            include("**/*.kt")
        },
        rootProject.fileTree("shared/engine-testing/src/commonTest") {
            include("**/*.kt")
        },
        rootProject.fileTree("composeApp/src/commonMain/kotlin/com/parlor/app/shell/game") {
            include("**/*.kt")
        },
        rootProject.fileTree("composeApp/src/commonTest/kotlin/com/parlor/app/shell/game") {
            include("**/*.kt")
        },
        rootProject.fileTree("composeApp/src/desktopTest/kotlin/com/parlor/app/shell/game") {
            include("**/*.kt")
        },
        rootProject.file("gradle/libs.versions.toml"),
        rootProject.file("gradle/wrapper/gradle-wrapper.properties"),
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
        rootProject.file("iosApp/Configuration/Config.xcconfig"),
        rootProject.file("config/parlor-version.xcconfig"),
        rootProject.fileTree("docs") {
            include("**/*.md")
        },
    )
        .withPropertyName("repositoryContractFiles")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}
