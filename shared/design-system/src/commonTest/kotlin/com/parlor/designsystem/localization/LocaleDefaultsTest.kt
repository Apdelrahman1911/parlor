package com.parlor.designsystem.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LocaleDefaultsTest {
    @Test
    fun system_mode_keeps_the_complete_platform_order() {
        val platform = listOf("en-US", "es", "ar")
        assertSame(platform, orderedLocaleDefaults(null, platform))
    }

    @Test
    fun an_explicit_language_keeps_all_distinct_fallbacks() {
        assertEquals(listOf("ar", "en-US", "es"), orderedLocaleDefaults("ar", listOf("en-US", "es")))
    }

    @Test
    fun a_selected_existing_language_moves_to_the_front_without_duplication() {
        assertEquals(listOf("ar", "en-US", "es"), orderedLocaleDefaults("ar", listOf("en-US", "ar", "es")))
    }

    @Test
    fun an_already_preferred_language_does_not_duplicate_or_drop_other_locales() {
        assertEquals(listOf("ar", "en-US", "es"), orderedLocaleDefaults("ar", listOf("ar", "en-US", "es")))
    }

    @Test
    fun regional_fallbacks_remain_distinct_from_the_explicit_language() {
        assertEquals(listOf("en", "en-US", "en-GB"), orderedLocaleDefaults("en", listOf("en-US", "en-GB")))
    }
}
