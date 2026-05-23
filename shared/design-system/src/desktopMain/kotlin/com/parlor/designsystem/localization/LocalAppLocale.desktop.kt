package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/**
 * Desktop (JVM) `actual`: updates the JVM default `Locale` so Compose
 * Multiplatform's resource lookup picks the right `values-XX/`.
 */
@Suppress("FunctionName")
actual object LocalAppLocale {
    private val Local = compositionLocalOf { Locale.getDefault().toLanguageTag() }
    private var defaultLocale: Locale? = null

    actual val current: String
        @Composable get() = Local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (defaultLocale == null) {
            defaultLocale = Locale.getDefault()
        }
        val newLocale = value?.let { Locale.forLanguageTag(it) } ?: defaultLocale!!
        Locale.setDefault(newLocale)
        return Local provides newLocale.toLanguageTag()
    }
}
