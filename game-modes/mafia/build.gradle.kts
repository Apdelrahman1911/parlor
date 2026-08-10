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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
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
            implementation(project(":shared:networking-testing"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.parlor.games.mafia.resources"
}
