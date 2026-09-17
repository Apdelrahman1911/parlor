package com.parlor.games.lastlight.ui.awake

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf

/** Optional convenience, never a wake lock, background task, or manual-lock override. */
@Composable
internal fun LastLightScreenAwakeEffect(sessionId: String, enabled: Boolean) {
    val output = LocalLastLightScreenAwake.current ?: rememberLastLightScreenAwake()
    DisposableEffect(output) {
        output.prepare()
        onDispose { output.close() }
    }
    DisposableEffect(output, sessionId) {
        onDispose { output.setEnabled(false) }
    }
    SideEffect { output.setEnabled(enabled) }
}

internal interface LastLightScreenAwake {
    fun prepare()
    fun setEnabled(enabled: Boolean)
    fun close()
}

internal val LocalLastLightScreenAwake = staticCompositionLocalOf<LastLightScreenAwake?> { null }

@Composable
internal expect fun rememberLastLightScreenAwake(): LastLightScreenAwake

/** Main-thread ownership: overlapping table disposal cannot release another table's lease. */
internal class ScreenAwakeLeases(private val read: () -> Boolean, private val write: (Boolean) -> Unit) {
    private val owners = mutableSetOf<Any>()
    private var previous = false

    fun acquire(owner: Any) {
        if (!owners.add(owner)) return
        if (owners.size == 1) {
            previous = read()
            write(true)
        }
    }

    fun release(owner: Any) {
        if (owners.remove(owner) && owners.isEmpty()) write(previous)
    }
}
