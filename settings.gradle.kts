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

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
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

// Multiplayer is a production feature, so the adapter is always part of the
// graph. Missing or incompatible P2pKit artifacts fail fast instead of
// producing a different, pass-and-play-only application.
include(":shared:transport-p2p")

// Game modules
include(":game-modes:whodunit")
include(":game-modes:mafia")
