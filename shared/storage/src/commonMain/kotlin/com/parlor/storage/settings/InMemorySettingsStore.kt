package com.parlor.storage.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory settings for deterministic tests and previews. Production DI uses
 * [PersistentSettingsStore] with a platform backing.
 */
class InMemorySettingsStore(
    initialSoundEnabled: Boolean = true,
    initialReducedMotion: Boolean = false,
    initialLanguageOverride: String? = null,
    initialThemeMode: String = "system",
    initialAnalyticsEnabled: Boolean = false,
    initialCrashReportingEnabled: Boolean = false,
) : SettingsStore {
    private val _sound = MutableStateFlow(initialSoundEnabled)
    private val _reducedMotion = MutableStateFlow(initialReducedMotion)
    private val _language = MutableStateFlow(
        SettingsPolicy.canonicalStoredLanguage(initialLanguageOverride),
    )
    private val _themeMode = MutableStateFlow(
        SettingsPolicy.canonicalStoredTheme(initialThemeMode),
    )
    private val _analytics = MutableStateFlow(initialAnalyticsEnabled)
    private val _crashReporting = MutableStateFlow(initialCrashReportingEnabled)

    override val soundEnabled = _sound.asStateFlow()
    override val reducedMotion = _reducedMotion.asStateFlow()
    override val languageOverride = _language.asStateFlow()
    override val themeMode = _themeMode.asStateFlow()
    override val analyticsEnabled = _analytics.asStateFlow()
    override val crashReportingEnabled = _crashReporting.asStateFlow()

    override suspend fun setSoundEnabled(enabled: Boolean) { _sound.value = enabled }
    override suspend fun setReducedMotion(enabled: Boolean) { _reducedMotion.value = enabled }
    override suspend fun setLanguageOverride(language: String?) {
        _language.value = SettingsPolicy.requireCanonicalLanguage(language)
    }
    override suspend fun setThemeMode(tag: String) {
        _themeMode.value = SettingsPolicy.requireCanonicalTheme(tag)
    }
    override suspend fun setAnalyticsEnabled(enabled: Boolean) { _analytics.value = enabled }
    override suspend fun setCrashReportingEnabled(enabled: Boolean) {
        _crashReporting.value = enabled
    }
}
