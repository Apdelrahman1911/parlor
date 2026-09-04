package com.parlor.storage.settings

import kotlinx.coroutines.flow.Flow

/** A platform preference store rejected or could not apply a requested value. */
class SettingsPersistenceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * User preferences. Read as a [Flow] so the UI reacts to changes without
 * a custom subscription protocol.
 *
 * Defaults are accessibility safe: reduced motion off and system
 * language/theme. Controls are exposed only for behavior implemented by the
 * shipping application.
 */
interface SettingsStore {
    val reducedMotion: Flow<Boolean>
    val languageOverride: Flow<String?>

    /**
     * Theme appearance tag — `"system"`, `"light"`, or `"dark"`. Stored as a
     * string so the storage layer does not depend on the design system.
     * Defaults to `"system"`.
     */
    val themeMode: Flow<String>

    suspend fun setReducedMotion(enabled: Boolean)
    suspend fun setLanguageOverride(language: String?)
    suspend fun setThemeMode(tag: String)
}
