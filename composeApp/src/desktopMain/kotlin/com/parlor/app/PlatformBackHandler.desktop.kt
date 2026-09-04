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
    // Compose Desktop installs a BackNavigationEventInput on its window-level
    // dispatcher. Register with that dispatcher so unconsumed Escape key-down
    // events follow the same application policy as every other Back source.
    val state = rememberNavigationEventState(DesktopBackNavigationInfo)
    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}

/** Non-sensitive history marker for Desktop's instantaneous Escape input. */
private data object DesktopBackNavigationInfo : NavigationEventInfo()
