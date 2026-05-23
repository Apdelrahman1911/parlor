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
@Suppress("FunctionName")
actual object LocalAppLocale {
    private val Local = compositionLocalOf {
        NSLocale.currentLocale.languageCode
    }
    private var defaultLanguages: List<*>? = null

    actual val current: String
        @Composable get() = Local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val effective = value ?: NSLocale.currentLocale.languageCode
        val userDefaults = NSUserDefaults.standardUserDefaults
        if (defaultLanguages == null) {
            defaultLanguages = userDefaults.arrayForKey("AppleLanguages") as? List<*>
        }
        if (value == null) {
            userDefaults.setObject(defaultLanguages, "AppleLanguages")
        } else {
            userDefaults.setObject(listOf(value), "AppleLanguages")
        }
        return Local provides effective
    }
}
