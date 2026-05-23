package com.parlor.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Detekt convention — applies the plugin with a sensible default config.
 * Add a project-level detekt-config.yml for custom rules.
 *
 * Apply via:  plugins { id("parlor.detekt") }
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        extensions.configure(DetektExtension::class.java) {
            buildUponDefaultConfig = true
            allRules = false
            parallel = true
        }
    }
}
