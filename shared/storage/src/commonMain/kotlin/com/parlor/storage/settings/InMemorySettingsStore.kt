package com.parlor.storage.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory settings — useful for tests and as the dev-grade default in Phase
 * 6. Each platform replaces this with a persistent backing (DataStore on
 * Android, NSUserDefaults on iOS, prefs file on Desktop) behind the same
 * interface.
 */
class InMemorySettingsStore(
    initialSoundEnabled: Boolean = true,
    initialReducedMotion: Boolean = false,
    initialLanguageOverride: String? = null,
) : SettingsStore {
    private val _sound = MutableStateFlow(initialSoundEnabled)
    private val _reducedMotion = MutableStateFlow(initialReducedMotion)
    private val _language = MutableStateFlow(initialLanguageOverride)

    override val soundEnabled = _sound.asStateFlow()
    override val reducedMotion = _reducedMotion.asStateFlow()
    override val languageOverride = _language.asStateFlow()

    override suspend fun setSoundEnabled(enabled: Boolean) { _sound.value = enabled }
    override suspend fun setReducedMotion(enabled: Boolean) { _reducedMotion.value = enabled }
    override suspend fun setLanguageOverride(language: String?) { _language.value = language }
}
