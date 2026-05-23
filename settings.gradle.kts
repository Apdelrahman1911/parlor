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

// P2pKit integration is opt-in via `parlor.p2p.enabled=true` in gradle.properties.
// When enabled, the adapter module :shared:transport-p2p is added to the build
// and depends on P2pKit's published artifacts (dev.p2pkit:p2p-core,
// dev.p2pkit:p2p-transport-lan) via mavenLocal().
//
// Why mavenLocal and not includeBuild: P2pKit requires Gradle 9.3.1+ (AGP 9.x),
// while Parlor is on Gradle 8.11.1. The user runs `./gradlew publishToMavenLocal`
// once inside ../P2pKit so its own Gradle 9.3+ builds the artifacts; Parlor then
// consumes them through its own Gradle 8.11 build with no version clash.
//
// Pass-and-play builds are unaffected — the module isn't even included when the
// flag is false.
val p2pEnabled: Boolean = (extra.properties["parlor.p2p.enabled"] as? String)?.toBoolean() ?: false

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
