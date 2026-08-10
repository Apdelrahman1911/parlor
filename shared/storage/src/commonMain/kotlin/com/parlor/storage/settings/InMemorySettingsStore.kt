package com.parlor.storage.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory settings for deterministic tests and previews. Production DI uses
 * [PersistentSettingsStore] with a platform backing.
 */
class InMemorySettingsStore(
    initialReducedMotion: Boolean = false,
    initialLanguageOverride: String? = null,
    initialThemeMode: String = "system",
) : SettingsStore {
    private val _reducedMotion = MutableStateFlow(initialReducedMotion)
    private val _language = MutableStateFlow(
        SettingsPolicy.canonicalStoredLanguage(initialLanguageOverride),
    )
    private val _themeMode = MutableStateFlow(
        SettingsPolicy.canonicalStoredTheme(initialThemeMode),
    )

    override val reducedMotion = _reducedMotion.asStateFlow()
    override val languageOverride = _language.asStateFlow()
    override val themeMode = _themeMode.asStateFlow()

    override suspend fun setReducedMotion(enabled: Boolean) { _reducedMotion.value = enabled }
    override suspend fun setLanguageOverride(language: String?) {
        _language.value = SettingsPolicy.requireCanonicalLanguage(language)
    }
    override suspend fun setThemeMode(tag: String) {
        _themeMode.value = SettingsPolicy.requireCanonicalTheme(tag)
    }
}
