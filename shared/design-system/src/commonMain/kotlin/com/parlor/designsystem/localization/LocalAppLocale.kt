package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable

/**
 * Applies the requested process locale after composition commits. Until that
 * mutation is visible to Compose resources, renders [loading]; then renders
 * [content] with the active language tag.
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
    loading: @Composable () -> Unit,
    content: @Composable (activeLanguageTag: String?) -> Unit,
)
