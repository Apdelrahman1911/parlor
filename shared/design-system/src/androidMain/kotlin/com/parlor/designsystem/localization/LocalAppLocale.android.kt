package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Android `actual`: updates `Configuration` + the JVM default `Locale` so
 * Compose Multiplatform's resource lookup picks the right `values-XX/`.
 *
 * The first time `provides` is invoked we snapshot the original Locale so
 * passing `null` later restores the system default.
 */
private val defaultAppLocale: Locale = Locale.getDefault()

@Composable
internal actual fun appLocaleProvidedValue(value: String?): ProvidedValue<*> {
    val configuration = LocalConfiguration.current
    val newLocale = value?.let { Locale.forLanguageTag(it) } ?: defaultAppLocale
    Locale.setDefault(newLocale)
    configuration.setLocale(newLocale)
    val resources = LocalContext.current.resources
    @Suppress("DEPRECATION")
    resources.updateConfiguration(configuration, resources.displayMetrics)
    return LocalConfiguration provides configuration
}
