package com.parlor.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for KMP library modules that also use Compose Multiplatform.
 *
 * Layers on top of parlor.kmp.library by applying the Compose plugins.
 *
 * Apply via:  plugins { id("parlor.kmp.compose.library") }
 */
class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("parlor.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    }
}
