plugins {
    `kotlin-dsl`
}

group = "com.parlor.buildlogic"

// Java 21 toolchain so convention plugins compile against the same target as the app.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
        register("androidApp") {
            id = "parlor.android.app"
            implementationClass = "com.parlor.buildlogic.AndroidAppConventionPlugin"
        }
        register("detektBase") {
            id = "parlor.detekt"
            implementationClass = "com.parlor.buildlogic.DetektConventionPlugin"
        }
    }
}
