package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Desktop Compose resources read the JVM default locale. The effect scopes an
 * explicit development-target override and restores it when disposed.
 */
@Composable
internal actual fun PlatformAppLocale(
    languageTag: String?,
    content: @Composable (activeLanguageTag: String?) -> Unit,
) {
    var appliedLanguageTag by remember(languageTag) { mutableStateOf<String?>(null) }

    DisposableEffect(languageTag) {
        val previousLocale = Locale.getDefault()
        val requestedLocale = languageTag?.let(Locale::forLanguageTag) ?: previousLocale
        if (languageTag != null) Locale.setDefault(requestedLocale)
        appliedLanguageTag = requestedLocale.toLanguageTag()

        onDispose {
            if (
                languageTag != null &&
                Locale.getDefault().toLanguageTag() == requestedLocale.toLanguageTag()
            ) {
                Locale.setDefault(previousLocale)
            }
        }
    }

    val activeTag = appliedLanguageTag ?: return
    content(activeTag)
}
