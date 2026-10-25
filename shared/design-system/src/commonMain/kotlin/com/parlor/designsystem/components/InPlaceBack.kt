package com.parlor.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Route-scoped, in-place UI cleanup before the shell's existing leave guard.
 * It never listens to platform input, exposes previous entries, or pops a stack.
 * The most recently composed active control (private reveal/confirmation) wins.
 */
class InPlaceBackOwner {
    private val handlers = linkedMapOf<Any, () -> Boolean>()

    internal fun register(token: Any, handler: () -> Boolean) {
        check(token !in handlers && handlers.size < MAX_HANDLERS)
        handlers[token] = handler
    }

    internal fun unregister(token: Any) {
        handlers.remove(token)
    }

    fun handleBack(): Boolean = handlers.values.toList().asReversed().any { it() }

    private companion object { const val MAX_HANDLERS = 8 }
}

private val LocalInPlaceBackOwner = staticCompositionLocalOf<InPlaceBackOwner?> { null }

@Composable
fun ProvideInPlaceBackOwner(owner: InPlaceBackOwner, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInPlaceBackOwner provides owner, content = content)
}

@Composable
fun InPlaceBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val owner = LocalInPlaceBackOwner.current ?: return
    val active by rememberUpdatedState(enabled)
    val callback by rememberUpdatedState(onBack)
    val token = remember(owner) { Any() }
    DisposableEffect(owner, token) {
        owner.register(token) {
            if (active) {
                callback()
                true
            } else false
        }
        // Identity-based disposal cannot remove a newer entry's handler.
        onDispose { owner.unregister(token) }
    }
}
