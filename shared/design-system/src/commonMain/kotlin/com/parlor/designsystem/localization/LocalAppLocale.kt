package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Top-level state holding the active app-language override. Assigning to
 * this triggers the `ProvideAppLanguage` subtree to recompose via the
 * `key(...)` block, picking up the new locale's strings.
 *
 * Per the official Compose Multiplatform workaround for runtime locale
 * override (Kotlin docs: *Manage local resource environment*). The
 * platform-specific `actual` for [LocalAppLocale] updates the platform-level
 * locale so `stringResource()` resolves against the right `values-XX/`.
 */
var customAppLocale: String? by mutableStateOf<String?>(null)

/**
 * The platform-shimmed composition local for the active language tag.
 * Each platform's `actual` updates the system locale so resource lookup
 * picks the right `values-<lang>/strings.xml`.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}
