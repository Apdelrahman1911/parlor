package com.parlor.storage.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentSettingsStoreTest {

    @Test
    fun absent_preferences_use_safe_defaults() = runTest {
        val store = PersistentSettingsStore(FakeBacking())

        assertTrue(store.soundEnabled.first())
        assertFalse(store.reducedMotion.first())
        assertNull(store.languageOverride.first())
        assertEquals("system", store.themeMode.first())
        assertFalse(store.analyticsEnabled.first())
        assertFalse(store.crashReportingEnabled.first())
    }

    @Test
    fun every_preference_survives_store_recreation() = runTest {
        val backing = FakeBacking()
        PersistentSettingsStore(backing).apply {
            setSoundEnabled(false)
            setReducedMotion(true)
            setLanguageOverride("AR")
            setThemeMode("DARK")
            setAnalyticsEnabled(true)
            setCrashReportingEnabled(true)
        }

        val restored = PersistentSettingsStore(backing)
        assertFalse(restored.soundEnabled.first())
        assertTrue(restored.reducedMotion.first())
        assertEquals("ar", restored.languageOverride.first())
        assertEquals("dark", restored.themeMode.first())
        assertTrue(restored.analyticsEnabled.first())
        assertTrue(restored.crashReportingEnabled.first())
    }

    @Test
    fun corrupt_persisted_tags_fall_back_without_blocking_startup() = runTest {
        val backing = FakeBacking().apply {
            strings["language_override"] = "not-installed"
            strings["theme_mode"] = "neon"
        }

        val store = PersistentSettingsStore(backing)

        assertNull(store.languageOverride.first())
        assertEquals("system", store.themeMode.first())
    }

    @Test
    fun invalid_setter_values_are_rejected_without_overwriting_last_value() = runTest {
        val backing = FakeBacking()
        val store = PersistentSettingsStore(backing)
        store.setLanguageOverride("en")
        store.setThemeMode("light")

        assertFailsWith<IllegalArgumentException> {
            store.setLanguageOverride("fr")
        }
        assertFailsWith<IllegalArgumentException> {
            store.setThemeMode("sepia")
        }

        assertEquals("en", store.languageOverride.first())
        assertEquals("light", store.themeMode.first())
        assertEquals("en", backing.strings["language_override"])
        assertEquals("light", backing.strings["theme_mode"])
    }

    @Test
    fun cancelled_or_failed_persistence_never_changes_the_published_flow() = runTest {
        val cancelled = PersistentSettingsStore(
            FakeBacking(writeFailure = CancellationException("stopped")),
        )
        assertFailsWith<CancellationException> {
            cancelled.setAnalyticsEnabled(true)
        }
        assertFalse(cancelled.analyticsEnabled.first())

        val failed = PersistentSettingsStore(
            FakeBacking(writeFailure = IllegalStateException("disk unavailable")),
        )
        assertFailsWith<IllegalStateException> {
            failed.setCrashReportingEnabled(true)
        }
        assertFalse(failed.crashReportingEnabled.first())
    }

    private class FakeBacking(
        private val writeFailure: Throwable? = null,
    ) : SettingsKeyValueBacking {
        val booleans = mutableMapOf<String, Boolean>()
        val strings = mutableMapOf<String, String>()

        override fun readBoolean(key: String): Boolean? = booleans[key]
        override fun readString(key: String): String? = strings[key]

        override suspend fun writeBoolean(key: String, value: Boolean) {
            writeFailure?.let { throw it }
            booleans[key] = value
        }

        override suspend fun writeString(key: String, value: String?) {
            writeFailure?.let { throw it }
            if (value == null) strings.remove(key) else strings[key] = value
        }
    }
}
