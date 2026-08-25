package com.parlor.designsystem.localization

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformAppLocaleLoadingContractTest {
    @Test
    fun every_platform_renders_the_loading_slot_before_its_locale_effect_applies() {
        platformActuals.forEach { relativePath ->
            val source = repositoryRoot().resolve(relativePath).readText()

            assertTrue("loading: @Composable () -> Unit" in source, relativePath)
            assertTrue("val activeTag = appliedLanguageTag ?: run {" in source, relativePath)
            assertTrue("loading()\n        return" in source, relativePath)
        }
    }

    @Test
    fun production_provider_supplies_a_full_screen_loading_surface() {
        val source = repositoryRoot()
            .resolve("shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/localization/" +
                "ProvideAppLanguage.kt")
            .readText()

        assertTrue("Surface(modifier = Modifier.fillMaxSize()) {}" in source)
    }

    private fun repositoryRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(directory, "settings.gradle.kts").isFile) {
            directory = checkNotNull(directory.parentFile) {
                "Could not locate the repository root from ${File(".").absoluteFile}"
            }
        }
        return directory
    }

    private companion object {
        val platformActuals = listOf(
            "shared/design-system/src/androidMain/kotlin/com/parlor/designsystem/localization/" +
                "LocalAppLocale.android.kt",
            "shared/design-system/src/iosMain/kotlin/com/parlor/designsystem/localization/" +
                "LocalAppLocale.ios.kt",
            "shared/design-system/src/desktopMain/kotlin/com/parlor/designsystem/localization/" +
                "LocalAppLocale.desktop.kt",
        )
    }
}
