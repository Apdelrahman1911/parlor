package com.parlor.storage.settings

import kotlinx.coroutines.flow.Flow

/**
 * User preferences. Read as a [Flow] so the UI reacts to changes without
 * a custom subscription protocol. MVP defaults: sound on, reduced motion off.
 */
interface SettingsStore {
    val soundEnabled: Flow<Boolean>
    val reducedMotion: Flow<Boolean>
    val languageOverride: Flow<String?>

    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setReducedMotion(enabled: Boolean)
    suspend fun setLanguageOverride(language: String?)
}
