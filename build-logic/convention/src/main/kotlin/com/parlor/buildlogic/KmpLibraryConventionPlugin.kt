package com.parlor.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Convention plugin for Kotlin Multiplatform library modules under :shared and :game-modes.
 *
 * Configures: Android, JVM ("desktop"), and iOS targets (x64 + arm64 + simulatorArm64).
 * Applies Java 21 + JVM target. Sets up commonTest with JUnit5.
 *
 * Apply via:  plugins { id("parlor.kmp.library") }
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val compileSdkVersion = requiredIntVersion("android-compile-sdk")
        val minimumSdkVersion = requiredIntVersion("android-min-sdk")
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.library")

        extensions.configure(KotlinMultiplatformExtension::class.java) {
            androidTarget {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
            jvm("desktop") {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
            listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
                iosTarget.binaries.framework {
                    baseName = project.name
                    isStatic = true
                }
            }

            applyDefaultHierarchyTemplate()
        }

        extensions.configure(LibraryExtension::class.java) {
            namespace = "com.parlor.${project.name.replace("-", ".")}"
            compileSdk = compileSdkVersion
            defaultConfig {
                minSdk = minimumSdkVersion
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }
}

private fun Project.requiredIntVersion(alias: String): Int =
    extensions.getByType(VersionCatalogsExtension::class.java)
        .named("libs")
        .findVersion(alias)
        .orElseThrow { IllegalStateException("Missing version-catalog entry '$alias'") }
        .requiredVersion
        .toInt()
