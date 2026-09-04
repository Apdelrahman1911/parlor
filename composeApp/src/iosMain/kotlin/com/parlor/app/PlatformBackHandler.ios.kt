package com.parlor.app

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // The Compose UIKit host publishes start-edge gestures through the
    // Navigation Event dispatcher. Register here so guarded game exits and
    // the Settings-to-Games action use the same policy as toolbar Back.
    val state = rememberNavigationEventState(IosBackNavigationInfo)
    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}

/** Non-sensitive history marker for the iOS start-edge Back input. */
private data object IosBackNavigationInfo : NavigationEventInfo()
