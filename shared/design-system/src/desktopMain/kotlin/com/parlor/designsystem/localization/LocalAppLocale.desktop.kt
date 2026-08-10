package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/**
 * Desktop (JVM) `actual`: updates the JVM default `Locale` so Compose
 * Multiplatform's resource lookup picks the right `values-XX/`.
 */
private val LocalAppLocale = compositionLocalOf { Locale.getDefault().toLanguageTag() }
private val defaultAppLocale: Locale = Locale.getDefault()

@Composable
internal actual fun appLocaleProvidedValue(value: String?): ProvidedValue<*> {
    val newLocale = value?.let { Locale.forLanguageTag(it) } ?: defaultAppLocale
    Locale.setDefault(newLocale)
    return LocalAppLocale provides newLocale.toLanguageTag()
}
