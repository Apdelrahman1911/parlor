package com.parlor.designsystem.theme

/**
 * User-selectable appearance preference. `System` follows the host OS;
 * `Light` and `Dark` force the corresponding palette regardless of system
 * setting.
 *
 * Persisted via `SettingsStore.themeMode` as a stable string tag so the
 * storage layer doesn't depend on the design system.
 */
enum class ThemeMode(val tag: String) {
    System(tag = "system"),
    Light(tag = "light"),
    Dark(tag = "dark");

    companion object {
        val Default: ThemeMode = System

        /** Best-effort parse from a stored tag. Unknown values fall to [Default]. */
        fun fromTag(tag: String?): ThemeMode = when (tag) {
            "light" -> Light
            "dark" -> Dark
            "system" -> System
            else -> Default
        }
    }
}
