package com.parlor.designsystem.localization

import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Synthetic suites only: never modify standard defaults or a user's OS language. */
class IosLanguageOverrideOwnerTest {
    @Test
    fun system_after_interrupted_lifetime_removes_only_owned_override() = withSuite { defaults, name ->
        defaults.registerDefaults(mapOf(LANGUAGE to listOf("ar")))
        // A global OS preference takes precedence over registered defaults.
        val resolvedFallback = assertNotNull(defaults.arrayForKey(LANGUAGE)).toList()
        IosLanguageOverrideOwner(defaults, name).apply("en")
        assertEquals(listOf("en"), defaults.arrayForKey(LANGUAGE))

        // A new process has no remembered old owner/disposal state.
        IosLanguageOverrideOwner(defaults, name).apply(null)
        assertNull(defaults.persistentDomainForName(name)?.get(LANGUAGE))
        assertEquals(resolvedFallback, defaults.arrayForKey(LANGUAGE))
        assertNull(defaults.objectForKey(OWNER))
    }

    @Test
    fun explicit_choice_after_restart_keeps_original_os_value_for_later_system() = withSuite { defaults, name ->
        val original = listOf("ar-EG", "en-US")
        defaults.setObject(original, LANGUAGE)
        IosLanguageOverrideOwner(defaults, name).apply("en")
        IosLanguageOverrideOwner(defaults, name).apply("en")
        IosLanguageOverrideOwner(defaults, name).apply(null)
        assertEquals(original, defaults.arrayForKey(LANGUAGE))
    }

    @Test
    fun fallback_language_is_never_promoted_to_the_application_domain() = withSuite { defaults, name ->
        defaults.registerDefaults(mapOf(LANGUAGE to listOf("ar")))
        val owner = IosLanguageOverrideOwner(defaults, name)
        owner.apply("en")
        owner.release()
        assertNull(defaults.persistentDomainForName(name)?.get(LANGUAGE))
    }

    @Test
    fun external_preference_changes_win_over_owned_cleanup() = withSuite { defaults, name ->
        IosLanguageOverrideOwner(defaults, name).apply("en")
        defaults.setObject(listOf("ar-SA", "en"), LANGUAGE)
        IosLanguageOverrideOwner(defaults, name).apply(null)
        assertEquals(listOf("ar-SA", "en"), defaults.arrayForKey(LANGUAGE))
        assertNull(defaults.objectForKey(OWNER))
    }

    @Test
    fun unmarked_identical_values_are_not_claimed_or_removed() = withSuite { defaults, name ->
        defaults.setObject(listOf("en"), LANGUAGE)
        val owner = IosLanguageOverrideOwner(defaults, name)
        owner.apply("en")
        assertNull(defaults.objectForKey(OWNER))
        owner.release()
        assertEquals(listOf("en"), defaults.arrayForKey(LANGUAGE))
    }

    @Test
    fun interrupted_preparation_does_not_delete_an_unmodified_preference() = withSuite { defaults, name ->
        defaults.setObject(listOf("ar"), LANGUAGE)
        defaults.setObject(mapOf("version" to "1", "installed" to "en", "previous" to listOf("ar")), OWNER)
        IosLanguageOverrideOwner(defaults, name).apply(null)
        assertEquals(listOf("ar"), defaults.arrayForKey(LANGUAGE))
    }

    @Test
    fun malformed_owner_record_cannot_clear_os_preferences() = withSuite { defaults, name ->
        defaults.setObject(listOf("en"), LANGUAGE)
        defaults.setObject(mapOf("version" to "unknown", "installed" to "en"), OWNER)
        IosLanguageOverrideOwner(defaults, name).apply(null)
        assertEquals(listOf("en"), defaults.arrayForKey(LANGUAGE))
    }

    @Test
    fun malformed_previous_value_is_never_installed_as_a_language_array() = withSuite { defaults, name ->
        for (invalid in listOf<Any>("ar", 42, listOf(42), mapOf("value" to "ar"))) {
            defaults.setObject(listOf("en"), LANGUAGE)
            defaults.setObject(mapOf("version" to "1", "installed" to "en", "previous" to invalid), OWNER)
            IosLanguageOverrideOwner(defaults, name).apply(null)
            assertEquals(listOf("en"), defaults.arrayForKey(LANGUAGE))
            assertNull(defaults.objectForKey(OWNER))
        }
    }

    @Test
    fun interrupted_preparation_without_previous_value_keeps_fallback_unowned() = withSuite { defaults, name ->
        defaults.registerDefaults(mapOf(LANGUAGE to listOf("ar")))
        val resolvedFallback = assertNotNull(defaults.arrayForKey(LANGUAGE)).toList()
        defaults.setObject(mapOf("version" to "1", "installed" to "en"), OWNER)
        IosLanguageOverrideOwner(defaults, name).apply(null)
        assertNull(defaults.persistentDomainForName(name)?.get(LANGUAGE))
        assertEquals(resolvedFallback, defaults.arrayForKey(LANGUAGE))
        assertNull(defaults.objectForKey(OWNER))
    }

    @Test
    fun interrupted_release_cannot_restore_over_a_new_os_preference() = withSuite { defaults, name ->
        defaults.setObject(listOf("ar"), LANGUAGE)
        IosLanguageOverrideOwner(defaults, name).apply("en")
        // Release restored the original, but the process ended before clearing
        // its bookkeeping. An intervening OS change must still win.
        defaults.setObject(listOf("ar"), LANGUAGE)
        defaults.setObject(listOf("en-GB", "ar"), LANGUAGE)
        IosLanguageOverrideOwner(defaults, name).apply(null)
        assertEquals(listOf("en-GB", "ar"), defaults.arrayForKey(LANGUAGE))
        assertNull(defaults.objectForKey(OWNER))
    }

    @Test
    fun multiple_runtime_switches_restore_original_preference() = withSuite { defaults, name ->
        defaults.setObject(listOf("ar-EG"), LANGUAGE)
        val owner = IosLanguageOverrideOwner(defaults, name)
        listOf("en", "ar", "en", "ar").forEach { requested ->
            owner.apply(requested)
            assertEquals(listOf(requested), defaults.arrayForKey(LANGUAGE))
        }
        owner.apply(null)
        assertEquals(listOf("ar-EG"), defaults.arrayForKey(LANGUAGE))
    }

    private fun withSuite(test: (NSUserDefaults, String) -> Unit) {
        val name = "com.parlor.synthetic-language-test.${NSUUID.UUID().UUIDString}"
        val defaults = NSUserDefaults(suiteName = name)
        try {
            test(defaults, name)
        } finally {
            defaults.removePersistentDomainForName(name)
        }
    }

    private companion object {
        const val LANGUAGE = IosLanguageOverrideOwner.APPLE_LANGUAGES_KEY
        const val OWNER = IosLanguageOverrideOwner.OWNERSHIP_KEY
    }
}
