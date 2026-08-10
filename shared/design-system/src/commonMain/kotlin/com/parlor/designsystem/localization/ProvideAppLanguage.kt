package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Drives runtime language switching. Wraps content with:
 *  - the platform-shimmed `LocalAppLocale` (so `stringResource(Res.string.X)`
 *    resolves against the right `values-XX/strings.xml`)
 *  - `LocalLayoutDirection` set from [AppLanguage.layoutDirection] (so the
 *    whole UI mirrors when Arabic is selected; per the official RTL docs)
 *  - a `key(...)` block that forces the subtree to recompose when the active
 *    language changes
 *
 * Mutate [customAppLocale] (or pass an updated [language]) to switch.
 *
 * **Sources used:**
 *  - *Localizing strings | Kotlin Multiplatform Documentation*
 *  - *Manage local resource environment | Kotlin Multiplatform Documentation*
 *  - *Handling Right-to-left languages | Kotlin Multiplatform Documentation*
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage?,
    content: @Composable () -> Unit,
) {
    val resolved = language ?: AppLanguage.fromTag(customAppLocale)
    val direction = resolved.layoutDirection

    CompositionLocalProvider(
        appLocaleProvidedValue(resolved.tag),
        LocalLayoutDirection provides direction,
    ) {
        key(resolved) {
            content()
        }
    }
}

/**
 * Convenience for `:composeApp` to mutate the active language at runtime.
 * Persistence is the caller's responsibility (typically via
 * `SettingsStore.setLanguageOverride`).
 */
fun setAppLanguage(language: AppLanguage) {
    customAppLocale = language.tag
}

fun setAppLanguageTag(tag: String?) {
    customAppLocale = tag
}
