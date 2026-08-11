package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable

/**
 * Applies the requested process locale after composition commits, then renders
 * [content] with the language tag actually visible to Compose resources.
 *
 * Compose Multiplatform 1.10 does not expose a public resource-environment
 * composition local. Its resource resolver reads the platform locale, so each
 * actual owns the smallest possible platform override and restores it when the
 * provider is disposed. Keeping this as a composable boundary prevents global
 * platform mutations from running in the composition body.
 */
@Composable
internal expect fun PlatformAppLocale(
    languageTag: String?,
    content: @Composable (activeLanguageTag: String?) -> Unit,
)
