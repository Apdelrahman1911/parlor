package com.parlor.designsystem.localization

import androidx.compose.ui.unit.LayoutDirection

/**
 * Languages the Parlor chrome supports. Case content (dossiers, clues, reveal
 * narratives) is *not* translated here — that lives in the validated case
 * payload with its own `language` field. This enum drives **app chrome only**:
 * button labels, screen headings, instructions composed in Composable code.
 */
enum class AppLanguage(
    val tag: String,
    val displayName: String,
    val layoutDirection: LayoutDirection,
) {
    English(tag = "en", displayName = "English", layoutDirection = LayoutDirection.Ltr),
    Arabic(tag = "ar", displayName = "العربية", layoutDirection = LayoutDirection.Rtl);

    companion object {
        val Default: AppLanguage = English

        /** Best-effort parse from a BCP-47 language tag. */
        fun fromTag(tag: String?): AppLanguage = when {
            tag == null -> Default
            tag.startsWith("ar", ignoreCase = true) -> Arabic
            tag.startsWith("en", ignoreCase = true) -> English
            else -> Default
        }
    }
}
