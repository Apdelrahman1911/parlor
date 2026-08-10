package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/**
 * iOS `actual`: writes the chosen locale to `NSUserDefaults["AppleLanguages"]`
 * so Compose Multiplatform's resource lookup picks the right `values-XX/`.
 *
 * The first time `provides` is invoked with a non-null value we snapshot the
 * original `AppleLanguages` so passing `null` later restores the system default.
 */
private val LocalAppLocale = compositionLocalOf {
    NSLocale.currentLocale.languageCode
}
private var defaultAppLanguages: List<*>? = null

@Composable
internal actual fun appLocaleProvidedValue(value: String?): ProvidedValue<*> {
    val effective = value ?: NSLocale.currentLocale.languageCode
    val userDefaults = NSUserDefaults.standardUserDefaults
    if (defaultAppLanguages == null) {
        defaultAppLanguages = userDefaults.arrayForKey("AppleLanguages")
    }
    if (value == null) {
        userDefaults.setObject(defaultAppLanguages, "AppleLanguages")
    } else {
        userDefaults.setObject(listOf(value), "AppleLanguages")
    }
    return LocalAppLocale provides effective
}
