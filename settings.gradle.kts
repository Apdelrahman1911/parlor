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

// Game modules
include(":game-modes:whodunit")
