package com.parlor.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Repository-wide Detekt policy.
 *
 * The root build applies this convention to every production subproject. The
 * plain `detekt` task intentionally scans every Kotlin source set (common,
 * platform, and test) so multiplatform code cannot fall outside the release
 * gate merely because it is not in a JVM-style `src/main/kotlin` directory.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        extensions.configure(DetektExtension::class.java) {
            buildUponDefaultConfig = true
            allRules = false
            parallel = true
            basePath = rootProject.projectDir.absolutePath
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            source.setFrom(
                fileTree("src") {
                    include("**/*.kt")
                    exclude("**/generated/**")
                },
            )
        }

        tasks.withType(Detekt::class.java).configureEach {
            reports {
                html.required.set(true)
                sarif.required.set(true)
                xml.required.set(true)
                txt.required.set(false)
                md.required.set(false)
            }
        }
    }
}
