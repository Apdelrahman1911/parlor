package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/**
 * Compose resources read [NSLocale.preferredLanguages]. An explicit in-app
 * language therefore needs the platform AppleLanguages override used by the
 * Compose resource implementation. The effect owns and restores only the value
 * it installed; System mode leaves platform language preferences untouched.
 */
@Composable
internal actual fun PlatformAppLocale(
    languageTag: String?,
    content: @Composable (activeLanguageTag: String?) -> Unit,
) {
    var appliedLanguageTag by remember(languageTag) { mutableStateOf<String?>(null) }

    DisposableEffect(languageTag) {
        val userDefaults = NSUserDefaults.standardUserDefaults
        val previousLanguages = userDefaults.arrayForKey(APPLE_LANGUAGES_KEY)
        if (languageTag != null) {
            userDefaults.setObject(listOf(languageTag), APPLE_LANGUAGES_KEY)
        }
        appliedLanguageTag = languageTag ?: NSLocale.preferredLanguages
            .firstOrNull()
            ?.toString()

        onDispose {
            val installedLanguage = languageTag ?: return@onDispose
            val currentLanguage = userDefaults.arrayForKey(APPLE_LANGUAGES_KEY)
                ?.firstOrNull()
                ?.toString()
            if (currentLanguage == installedLanguage) {
                if (previousLanguages == null) {
                    userDefaults.removeObjectForKey(APPLE_LANGUAGES_KEY)
                } else {
                    userDefaults.setObject(previousLanguages, APPLE_LANGUAGES_KEY)
                }
            }
        }
    }

    val activeTag = appliedLanguageTag ?: return
    content(activeTag)
}

private const val APPLE_LANGUAGES_KEY = "AppleLanguages"
