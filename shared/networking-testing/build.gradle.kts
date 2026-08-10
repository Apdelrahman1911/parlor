plugins {
    id("parlor.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(project(":shared:networking"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
