package com.parlor.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement

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
            // Type-aware KMP tasks add compiler-generated Compose/resource
            // sources outside the extension's authored `src` tree. Generated
            // package names are controlled by upstream tooling, so analyse the
            // generator inputs and production consumers, not build output.
            exclude("**/build/generated/**", "**/generated/**")
            val generatedRoot = layout.buildDirectory.get().asFile.toPath().normalize()
            exclude { element: FileTreeElement ->
                element.file.toPath().normalize().startsWith(generatedRoot)
            }
            reports {
                html.required.set(true)
                html.outputLocation.set(layout.buildDirectory.file("reports/detekt/$name.html"))
                sarif.required.set(true)
                sarif.outputLocation.set(layout.buildDirectory.file("reports/detekt/$name.sarif"))
                xml.required.set(true)
                xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/$name.xml"))
                txt.required.set(false)
                md.required.set(false)
            }
        }


    }
}
