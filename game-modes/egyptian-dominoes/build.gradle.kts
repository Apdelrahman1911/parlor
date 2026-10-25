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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.parlor.games.dominoes.resources"
}
