@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        // Resolve Kotlin DSL's Kotlin dependencies at their upstream repository.
        mavenCentral {
            content {
                includeGroup("org.jetbrains.kotlin")
            }
        }
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":convention")
