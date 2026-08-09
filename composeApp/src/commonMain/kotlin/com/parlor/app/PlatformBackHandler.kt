package com.parlor.app

import androidx.compose.runtime.Composable

/** Installs a platform back callback when that platform exposes one. */
@Composable
internal expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
