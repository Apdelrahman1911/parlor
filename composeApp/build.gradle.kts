import java.io.File

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val parlorVersionFile = rootProject.layout.projectDirectory.file("config/parlor-version.xcconfig")
val parlorVersionValues = providers.fileContents(parlorVersionFile).asText.map { content ->
    content.lineSequence()
        .map(String::trim)
        .filter { line -> line.isNotEmpty() && !line.startsWith("//") }
        .associate { line ->
            val assignment = line.split('=', limit = 2)
            check(assignment.size == 2) { "Invalid Parlor version assignment: $line" }
            assignment[0].trim() to assignment[1].trim()
        }
}.get()
val parlorVersionName = requireNotNull(parlorVersionValues["PARLOR_VERSION_NAME"]) {
    "PARLOR_VERSION_NAME is missing from ${parlorVersionFile.asFile}"
}.also { version ->
    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) {
        "PARLOR_VERSION_NAME must be a three-component semantic version"
    }
}
val parlorBuildNumber = requireNotNull(parlorVersionValues["PARLOR_BUILD_NUMBER"]) {
    "PARLOR_BUILD_NUMBER is missing from ${parlorVersionFile.asFile}"
}.toInt().also { buildNumber ->
    require(buildNumber > 0) { "PARLOR_BUILD_NUMBER must be positive" }
}
val generatedParlorVersionDirectory = layout.buildDirectory.dir(
    "generated/parlorVersion/commonMain/kotlin",
)
val generateParlorVersion by tasks.registering {
    description = "Generates the common runtime version from the package-version source of truth."
    inputs.file(parlorVersionFile)
    inputs.property("versionName", parlorVersionName)
    inputs.property("buildNumber", parlorBuildNumber)
    outputs.dir(generatedParlorVersionDirectory)
    doLast {
        val versionName = inputs.properties.getValue("versionName") as String
        val buildNumber = inputs.properties.getValue("buildNumber") as Int
        val output = File(
            outputs.files.singleFile,
            "com/parlor/app/build/ParlorBuildVersion.kt",
        )
        output.parentFile.mkdirs()
        output.writeText(
            """
            package com.parlor.app.build

            internal const val PARLOR_VERSION_NAME: String = "$versionName"
            internal const val PARLOR_BUILD_NUMBER: Int = $buildNumber
            """.trimIndent() + "\n",
        )
    }
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
            isStatic = false
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateParlorVersion)
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
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.resources)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.androidx.navigation3.runtime)
                implementation(libs.jetbrains.navigation3.ui)

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
        androidInstrumentedTest.dependencies {
            // InstrumentationTestCase is supplied by the platform's
            // android.test.runner shared library. Compile against the matching
            // SDK stub without adding a Maven dependency to the test APK.
            compileOnly(
                files(
                    androidComponents.sdkComponents.sdkDirectory.map { sdkDirectory ->
                        sdkDirectory.file(
                            "platforms/android-${libs.versions.android.compile.sdk.get()}/optional/android.test.base.jar",
                        )
                    },
                ),
            )
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
        versionCode = parlorBuildNumber
        versionName = parlorVersionName
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
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
        getByName("debug") {
            // Local development installs beside the canonical Store app. Store
            // workflows reject this suffix and always archive the Release
            // identity configured below.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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
    sourceSets {
        // KMP owns the androidInstrumentedTest hierarchy, while AGP's Java
        // compiler reads androidTest. Point it at the shared KMP layout so the
        // platform-only smoke test is packaged in the test APK.
        getByName("androidTest").java.srcDir("src/androidInstrumentedTest/java")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    // Exercise the same R8-shrunk variant that is submitted to the Store. CI
    // supplies an ephemeral, non-production signing key only for installation
    // on this disposable managed device; normal release builds remain unsigned.
    testBuildType = "release"
    testOptions {
        managedDevices {
            localDevices {
                create("pixel2Api35") {
                    device = "Pixel 2"
                    apiLevel = 35
                    systemImageSource = "google"
                    require64Bit = true
                }
            }
        }
    }

    // Case JSON lives inside the Whodunit module's Compose Multiplatform
    // resources, not in app-level Android assets. See game-modes/whodunit/.
}

val configuredStoreApplicationId = requireNotNull(android.defaultConfig.applicationId)
val configuredDebugApplicationIdSuffix = android.buildTypes.getByName("debug").applicationIdSuffix.orEmpty()
val configuredReleaseApplicationIdSuffix = android.buildTypes.getByName("release").applicationIdSuffix.orEmpty()

val verifyApplicationIdentities by tasks.registering {
    group = "verification"
    description = "Rejects Debug/Store Android identity drift before release automation runs."
    inputs.property("storeApplicationId", configuredStoreApplicationId)
    inputs.property("debugApplicationIdSuffix", configuredDebugApplicationIdSuffix)
    inputs.property("releaseApplicationIdSuffix", configuredReleaseApplicationIdSuffix)
    doLast {
        check(inputs.properties.getValue("storeApplicationId") == "com.parlor.app") {
            "Android Store application ID changed from com.parlor.app."
        }
        check(inputs.properties.getValue("debugApplicationIdSuffix") == ".debug") {
            "Android Debug must use the isolated com.parlor.app.debug identity."
        }
        check(inputs.properties.getValue("releaseApplicationIdSuffix") == "") {
            "Android Release must not add a non-Store application-ID suffix."
        }
    }
}

// The release target and pinned toolchain are deliberate compatibility
// choices. Lint's update advisories are reviewed in
// docs/ANDROID_LINT_TRIAGE.md; a new correctness/packaging warning must fail
// the release gate instead of being silently accepted.
val verifyReleaseLintWarnings by tasks.registering {
    group = "verification"
    description = "Fails if release lint reports an untriaged warning."
    dependsOn("lintRelease")
    val lintReport = layout.buildDirectory.file("reports/lint-results-release.xml")
    inputs.file(lintReport)
    val acceptedInventory = rootProject.layout.projectDirectory.file(
        "config/android-lint-accepted-warnings.txt",
    )
    inputs.file(acceptedInventory)
    doLast {
        val inputFiles = inputs.files.files.associateBy(File::getName)
        val report = checkNotNull(inputFiles["lint-results-release.xml"]) {
            "Release lint report was not registered as a task input"
        }
        val inventory = checkNotNull(inputFiles["android-lint-accepted-warnings.txt"]) {
            "Accepted lint inventory was not registered as a task input"
        }
        check(report.isFile) { "Missing release lint report: ${report.absolutePath}" }
        check(inventory.isFile) { "Missing accepted lint inventory: ${inventory.absolutePath}" }

        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(report)
        val repositoryRoot = checkNotNull(inventory.parentFile?.parentFile) {
            "Accepted lint inventory must live under <repository>/config"
        }.canonicalFile.toPath()
        val issues = document.getElementsByTagName("issue")
        val actual = buildList {
            for (index in 0 until issues.length) {
                val issue = issues.item(index)
                val id = issue.attributes.getNamedItem("id")?.nodeValue.orEmpty()
                val rawMessage = issue.attributes.getNamedItem("message")?.nodeValue.orEmpty()
                val message = if (rawMessage.startsWith("Newer version of lint available: ")) {
                    "Newer version of lint available"
                } else {
                    rawMessage.substringBefore(" is available:")
                }
                val location = issue.childNodes.let { children ->
                    (0 until children.length)
                        .map(children::item)
                        .firstOrNull { child -> child.nodeName == "location" }
                }
                check(location != null) { "Lint issue '$id' has no source location" }
                val source = File(
                    location.attributes.getNamedItem("file")?.nodeValue.orEmpty(),
                ).canonicalFile.toPath()
                check(source.startsWith(repositoryRoot)) {
                    "Lint issue '$id' points outside the repository: $source"
                }
                val relativeSource = repositoryRoot.relativize(source)
                    .toString()
                    .replace(File.separatorChar, '/')
                add("$id|$relativeSource|$message")
            }
        }.sorted()
        val expected = inventory.readLines()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
            .sorted()
        check(actual == expected) {
            val unexpected = actual.toMutableList().also { remaining ->
                expected.forEach(remaining::remove)
            }
            val missing = expected.toMutableList().also { remaining ->
                actual.forEach(remaining::remove)
            }
            "Release lint inventory changed. Unexpected: $unexpected; missing: $missing"
        }
    }
}

val mergedReleaseManifest = layout.buildDirectory.file(
    "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml",
)
val verifyMergedReleaseManifest by tasks.registering {
    group = "verification"
    description = "Verifies the final merged release manifest against Parlor's mobile security policy."
    dependsOn("processReleaseManifest")
    inputs.file(mergedReleaseManifest)

    doLast {
        val manifest = inputs.files.singleFile
        check(manifest.isFile && manifest.length() > 0L) {
            "Missing merged release manifest: ${manifest.absolutePath}"
        }

        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(manifest)
        val manifestElement = document.documentElement
        check(manifestElement.tagName == "manifest") { "Release artifact has no <manifest> root" }
        check(manifestElement.getAttribute("package") == "com.parlor.app") {
            "Unexpected release application ID: ${manifestElement.getAttribute("package")}"
        }

        val expectedPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "com.parlor.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val permissionNodes = document.getElementsByTagName("uses-permission")
        val actualPermissions = buildSet {
            for (index in 0 until permissionNodes.length) {
                val element = permissionNodes.item(index) as org.w3c.dom.Element
                add(element.getAttributeNS(androidNamespace, "name"))
            }
        }
        check(actualPermissions == expectedPermissions) {
            "Merged release permissions changed. Expected $expectedPermissions, found $actualPermissions"
        }

        val application = checkNotNull(
            document.getElementsByTagName("application").item(0) as? org.w3c.dom.Element,
        ) { "Merged release manifest has no <application>" }
        check(application.getAttributeNS(androidNamespace, "allowBackup") == "false") {
            "Release application must disable Android backup"
        }
        check(application.getAttributeNS(androidNamespace, "usesCleartextTraffic") == "false") {
            "Release application must reject cleartext network traffic"
        }
        check(application.getAttributeNS(androidNamespace, "debuggable") != "true") {
            "Release application must not be debuggable"
        }
        check(application.getAttributeNS(androidNamespace, "testOnly") != "true") {
            "Release application must not be test-only"
        }

        val exportedComponents = buildSet {
            listOf("activity", "activity-alias", "service", "receiver", "provider")
                .forEach { tagName ->
                    val nodes = document.getElementsByTagName(tagName)
                    for (index in 0 until nodes.length) {
                        val element = nodes.item(index) as org.w3c.dom.Element
                        if (element.getAttributeNS(androidNamespace, "exported") == "true") {
                            add(
                                listOf(
                                    tagName,
                                    element.getAttributeNS(androidNamespace, "name"),
                                    element.getAttributeNS(androidNamespace, "permission"),
                                ).joinToString("|"),
                            )
                        }
                    }
                }
        }
        val expectedExportedComponents = setOf(
            "activity|com.parlor.app.MainActivity|",
            "receiver|androidx.profileinstaller.ProfileInstallReceiver|android.permission.DUMP",
        )
        check(exportedComponents == expectedExportedComponents) {
            "Merged release exported components changed. " +
                "Expected $expectedExportedComponents, found $exportedComponents"
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
    val gameShellSupportSources = fileTree(
        "src/commonMain/kotlin/com/parlor/app/shell/game",
    ).matching {
        include("**/*.kt")
        exclude("**/*GameShellBinding.kt")
    }
    inputs.files(neutralShellSources, multiplayerShellSources, gameShellSupportSources)
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
            packageVersion = parlorVersionName
        }
    }
}
