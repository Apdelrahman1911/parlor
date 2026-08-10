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
 * platform-specific locale provider updates the platform-level
 * locale so `stringResource()` resolves against the right `values-XX/`.
 */
var customAppLocale: String? by mutableStateOf<String?>(null)

/**
 * Returns the platform-shimmed composition-local value for the active language
 * tag. A top-level expect/actual function is sufficient here and avoids opting
 * the whole build into Kotlin's still-beta expect/actual-class ABI.
 */
@Composable
internal expect fun appLocaleProvidedValue(value: String?): ProvidedValue<*>
