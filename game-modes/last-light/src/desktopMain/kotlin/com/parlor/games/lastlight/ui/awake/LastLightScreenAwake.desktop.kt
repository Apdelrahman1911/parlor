package com.parlor.games.lastlight.ui.awake

import androidx.compose.runtime.Composable

/** Desktop is a deterministic test/development target, not a background-play implementation. */
@Composable
internal actual fun rememberLastLightScreenAwake(): LastLightScreenAwake = DesktopScreenAwake

private object DesktopScreenAwake : LastLightScreenAwake {
    override fun prepare() = Unit
    override fun setEnabled(enabled: Boolean) = Unit
    override fun close() = Unit
}
