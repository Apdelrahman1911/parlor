package com.parlor.designsystem.localization

import androidx.compose.runtime.Composable

/**
 * Applies the requested process locale after composition commits. Before the
 * first locale is active it renders [loading]. Later runtime changes retain
 * [content] and its remembered state until the new locale becomes active.
 *
 * The pinned Compose Multiplatform runtime does not expose a public
 * resource-environment override. Its resolver reads the platform locale, so
 * each actual owns the smallest possible platform override and restores it
 * when the provider is disposed. Keeping this as a composable boundary
 * prevents global platform mutations from running in the composition body.
 */
@Composable
internal expect fun PlatformAppLocale(
    languageTag: String?,
    loading: @Composable () -> Unit,
    content: @Composable (activeLanguageTag: String?) -> Unit,
)
