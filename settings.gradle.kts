@file:Suppress("UnstableApiUsage")

rootProject.name = "parlor"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

// P2pKit integration. The flag resolves in three steps:
//   1. Explicit override via gradle.properties or -P (always wins).
//   2. Auto-detect: enabled when P2pKit 0.6.0 is published in mavenLocal.
//   3. Default off.
//
// So if you've run `./gradlew publishToMavenLocal` inside ../P2pKit once, every
// build from Android Studio / IntelliJ / CLI picks it up automatically — no
// flag, no special build config. Fresh clones / CI without P2pKit stay off.
//
// Why mavenLocal and not includeBuild: P2pKit requires Gradle 9.3.1+ (AGP 9.x);
// Parlor is on Gradle 8.11.1. P2pKit publishes with its own Gradle, Parlor
// consumes via its own — no version clash.
val p2pExplicit: Boolean? = run {
    val raw = extra.properties["parlor.p2p.enabled"] as? String ?: return@run null
    // .properties files don't strip inline `#` comments — strip them ourselves
    // so `parlor.p2p.enabled=true   # always on` reads as `true` and not as
    // the literal "true   # always on" (which `.toBoolean()` would reject).
    val cleaned = raw.substringBefore('#').trim()
    if (cleaned.isEmpty()) null else cleaned.toBooleanStrictOrNull() ?: cleaned.toBoolean()
}
val p2pAutoDetected: Boolean = run {
    val home = System.getProperty("user.home")
    if (home.isNullOrBlank()) {
        false
    } else {
        val coreArtifact = java.io.File(
            home,
            ".m2/repository/dev/p2pkit/p2p-core/0.6.0/p2p-core-0.6.0.jar",
        )
        val lanArtifact = java.io.File(
            home,
            ".m2/repository/dev/p2pkit/p2p-transport-lan/0.6.0/p2p-transport-lan-0.6.0.module",
        )
        coreArtifact.exists() && lanArtifact.exists()
    }
}
val p2pEnabled: Boolean = p2pExplicit ?: p2pAutoDetected

println(
    "[parlor] P2pKit integration: " + if (p2pEnabled) {
        "ENABLED (${if (p2pExplicit != null) "explicit" else "auto-detected from mavenLocal"})"
    } else {
        "disabled — publish ../P2pKit to mavenLocal or set parlor.p2p.enabled=true to turn on"
    },
)

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // P2pKit artifacts (published via `./gradlew publishToMavenLocal`
        // inside the sibling ../P2pKit clone) are resolved from here. Harmless
        // when P2pKit isn't installed locally — nothing else uses mavenLocal.
        mavenLocal()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Application
include(":composeApp")

// Shared platform modules
include(":shared:core")
include(":shared:design-system")
include(":shared:engine")
include(":shared:engine-testing")
include(":shared:session")
include(":shared:content")
include(":shared:networking")
include(":shared:storage")
include(":shared:navigation")

// :shared:transport-p2p is opt-in — only included when the user has cloned
// P2pKit alongside Parlor and flipped parlor.p2p.enabled=true. Keeping the
// pass-and-play build path completely independent of the P2P optional dep.
if (p2pEnabled) {
    include(":shared:transport-p2p")
}

// Game modules
include(":game-modes:whodunit")
include(":game-modes:mafia")
