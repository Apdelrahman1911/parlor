package com.parlor.designsystem.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageSelectionTest {
    @Test
    fun null_override_follows_a_supported_system_language() {
        assertEquals(
            AppLanguage.Arabic,
            resolveAppLanguage(languageOverride = null, systemLanguageTag = "ar-EG"),
        )
    }

    @Test
    fun explicit_override_wins_over_the_system_language() {
        assertEquals(
            AppLanguage.English,
            resolveAppLanguage(languageOverride = "en", systemLanguageTag = "ar-EG"),
        )
    }

    @Test
    fun unsupported_system_language_falls_back_to_english() {
        assertEquals(
            AppLanguage.English,
            resolveAppLanguage(languageOverride = null, systemLanguageTag = "fr-FR"),
        )
    }
}
