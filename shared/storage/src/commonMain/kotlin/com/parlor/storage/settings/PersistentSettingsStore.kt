package com.parlor.storage.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Minimal typed persistence boundary implemented by each platform.
 *
 * Reads occur once while the store is constructed; platform implementations
 * should use their small, cached preferences API. Writes are suspending so a
 * backing that must flush a file can move that work off the main thread.
 */
interface SettingsKeyValueBacking {
    fun readBoolean(key: String): Boolean?
    fun readString(key: String): String?
    suspend fun writeBoolean(key: String, value: Boolean)
    suspend fun writeString(key: String, value: String?)
}

/**
 * Validating, serialized production [SettingsStore].
 *
 * A write is persisted before its flow changes, so an I/O failure never tells
 * the running UI that a preference was saved when it was not. One mutex
 * serializes all keys, giving concurrent toggles a deterministic order.
 * Cancellation is deliberately not caught or translated.
 */
class PersistentSettingsStore(
    private val backing: SettingsKeyValueBacking,
) : SettingsStore {

    private val writes = Mutex()
    private val _sound = MutableStateFlow(backing.readBoolean(KEY_SOUND) ?: DEFAULT_SOUND)
    private val _reducedMotion =
        MutableStateFlow(backing.readBoolean(KEY_REDUCED_MOTION) ?: DEFAULT_REDUCED_MOTION)
    private val _language = MutableStateFlow(
        SettingsPolicy.canonicalStoredLanguage(backing.readString(KEY_LANGUAGE)),
    )
    private val _themeMode = MutableStateFlow(
        SettingsPolicy.canonicalStoredTheme(backing.readString(KEY_THEME)),
    )
    private val _analytics =
        MutableStateFlow(backing.readBoolean(KEY_ANALYTICS) ?: DEFAULT_ANALYTICS)
    private val _crashReporting =
        MutableStateFlow(backing.readBoolean(KEY_CRASH_REPORTING) ?: DEFAULT_CRASH_REPORTING)

    override val soundEnabled = _sound.asStateFlow()
    override val reducedMotion = _reducedMotion.asStateFlow()
    override val languageOverride = _language.asStateFlow()
    override val themeMode = _themeMode.asStateFlow()
    override val analyticsEnabled = _analytics.asStateFlow()
    override val crashReportingEnabled = _crashReporting.asStateFlow()

    override suspend fun setSoundEnabled(enabled: Boolean) {
        writes.withLock {
            backing.writeBoolean(KEY_SOUND, enabled)
            _sound.value = enabled
        }
    }

    override suspend fun setReducedMotion(enabled: Boolean) {
        writes.withLock {
            backing.writeBoolean(KEY_REDUCED_MOTION, enabled)
            _reducedMotion.value = enabled
        }
    }

    override suspend fun setLanguageOverride(language: String?) {
        val canonical = SettingsPolicy.requireCanonicalLanguage(language)
        writes.withLock {
            backing.writeString(KEY_LANGUAGE, canonical)
            _language.value = canonical
        }
    }

    override suspend fun setThemeMode(tag: String) {
        val canonical = SettingsPolicy.requireCanonicalTheme(tag)
        writes.withLock {
            backing.writeString(KEY_THEME, canonical)
            _themeMode.value = canonical
        }
    }

    override suspend fun setAnalyticsEnabled(enabled: Boolean) {
        writes.withLock {
            backing.writeBoolean(KEY_ANALYTICS, enabled)
            _analytics.value = enabled
        }
    }

    override suspend fun setCrashReportingEnabled(enabled: Boolean) {
        writes.withLock {
            backing.writeBoolean(KEY_CRASH_REPORTING, enabled)
            _crashReporting.value = enabled
        }
    }

    private companion object {
        const val KEY_SOUND = "sound_enabled"
        const val KEY_REDUCED_MOTION = "reduced_motion"
        const val KEY_LANGUAGE = "language_override"
        const val KEY_THEME = "theme_mode"
        const val KEY_ANALYTICS = "analytics_enabled"
        const val KEY_CRASH_REPORTING = "crash_reporting_enabled"

        const val DEFAULT_SOUND = true
        const val DEFAULT_REDUCED_MOTION = false
        const val DEFAULT_ANALYTICS = false
        const val DEFAULT_CRASH_REPORTING = false
    }
}

internal object SettingsPolicy {
    private val supportedLanguages = setOf("en", "ar")
    private val supportedThemes = setOf("system", "light", "dark")

    fun canonicalStoredLanguage(language: String?): String? =
        language
            ?.trim()
            ?.lowercase()
            ?.takeIf(supportedLanguages::contains)

    fun canonicalStoredTheme(theme: String?): String =
        theme
            ?.trim()
            ?.lowercase()
            ?.takeIf(supportedThemes::contains)
            ?: "system"

    fun requireCanonicalLanguage(language: String?): String? {
        if (language == null) return null
        return canonicalStoredLanguage(language)
            ?: throw IllegalArgumentException("Unsupported language preference")
    }

    fun requireCanonicalTheme(theme: String): String =
        theme
            .trim()
            .lowercase()
            .takeIf(supportedThemes::contains)
            ?: throw IllegalArgumentException("Unsupported theme preference")
}
