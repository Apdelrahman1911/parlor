plugins {
    id("parlor.kmp.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.assertk)
        }
    }
}
