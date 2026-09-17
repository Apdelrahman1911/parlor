package com.parlor.games.lastlight.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Supplied by the app shell; an interruption remains observable after foreground recovery. */
data class LastLightProcessVisibility(
    val isForeground: Boolean,
    val concealmentEpoch: Long,
)

/** Conceal private cards until the platform has positively reported an active app. */
val LocalLastLightProcessVisibility = staticCompositionLocalOf {
    LastLightProcessVisibility(isForeground = false, concealmentEpoch = 0L)
}

/** Synchronous platform check for queued callbacks that can run before Compose observes an interruption. */
val LocalLastLightVisibilityReader = staticCompositionLocalOf<(() -> LastLightProcessVisibility)?> { null }
