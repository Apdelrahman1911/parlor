plugins {
    `kotlin-dsl`
    alias(libs.plugins.detekt)
}

group = "com.parlor.buildlogic"

// Java 21 toolchain so convention plugins compile against the same target as the app.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    basePath = rootProject.projectDir.parentFile.absolutePath
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
    source.setFrom(
        fileTree("src") {
            include("**/*.kt")
            exclude("**/generated/**")
        },
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        md.required.set(false)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "parlor.kmp.library"
            implementationClass = "com.parlor.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpComposeLibrary") {
            id = "parlor.kmp.compose.library"
            implementationClass = "com.parlor.buildlogic.KmpComposeLibraryConventionPlugin"
        }
        register("detektBase") {
            id = "parlor.detekt"
            implementationClass = "com.parlor.buildlogic.DetektConventionPlugin"
        }
    }
}
