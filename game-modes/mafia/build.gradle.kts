plugins {
    id("parlor.kmp.compose.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(project(":shared:engine"))
            implementation(project(":shared:design-system"))
            implementation(project(":shared:session"))
            implementation(project(":shared:content"))
            implementation(project(":shared:storage"))
            implementation(project(":shared:networking"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.assertk)
            implementation(libs.koin.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.parlor.games.mafia.resources"
}
