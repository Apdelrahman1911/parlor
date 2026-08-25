package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Drives runtime language switching. Wraps content with:
 *  - the platform-owned locale boundary (so `stringResource(Res.string.X)`
 *    resolves against the right `values-XX/strings.xml`)
 *  - `LocalLayoutDirection` set from [AppLanguage.layoutDirection] (so the
 *    whole UI mirrors when Arabic is selected; per the official RTL docs)
 *  - a `key(...)` block that forces the subtree to recompose when the active
 *    language changes
 *
 * Pass the persisted override, or `null` to follow the platform language.
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
    ProvideAppLanguage(
        language = language,
        loading = {
            // Platform locale effects commit after the first composition. Do
            // not render resource-bearing content against the previous locale.
            Surface(modifier = Modifier.fillMaxSize()) {}
        },
        content = content,
    )
}

/** Internal loading seam used by platform-composition tests. */
@Composable
internal fun ProvideAppLanguage(
    language: AppLanguage?,
    loading: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    PlatformAppLocale(
        languageTag = language?.tag,
        loading = loading,
    ) { activeLanguageTag ->
        val resolved = resolveAppLanguage(
            languageOverride = language?.tag,
            systemLanguageTag = activeLanguageTag,
        )

        CompositionLocalProvider(LocalLayoutDirection provides resolved.layoutDirection) {
            key(language, resolved) {
                content()
            }
        }
    }
}
