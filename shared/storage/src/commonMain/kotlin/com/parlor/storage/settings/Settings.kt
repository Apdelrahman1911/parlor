package com.parlor.storage.settings

import kotlinx.coroutines.flow.Flow

/**
 * User preferences. Read as a [Flow] so the UI reacts to changes without
 * a custom subscription protocol.
 *
 * Defaults are privacy/accessibility safe: sound on, reduced motion off,
 * system language/theme, and analytics/crash reporting both off.
 */
interface SettingsStore {
    val soundEnabled: Flow<Boolean>
    val reducedMotion: Flow<Boolean>
    val languageOverride: Flow<String?>
    val analyticsEnabled: Flow<Boolean>
    val crashReportingEnabled: Flow<Boolean>

    /**
     * Theme appearance tag — `"system"`, `"light"`, or `"dark"`. Stored as a
     * string so the storage layer does not depend on the design system.
     * Defaults to `"system"`.
     */
    val themeMode: Flow<String>

    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setReducedMotion(enabled: Boolean)
    suspend fun setLanguageOverride(language: String?)
    suspend fun setThemeMode(tag: String)
    suspend fun setAnalyticsEnabled(enabled: Boolean)
    suspend fun setCrashReportingEnabled(enabled: Boolean)
}
