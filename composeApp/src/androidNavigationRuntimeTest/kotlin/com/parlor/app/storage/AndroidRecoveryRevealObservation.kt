package com.parlor.app.storage

/** Read-only decision for one sample; the caller owns/recycles the selected node. */
internal data class AndroidRecoveryReveal<T>(val node: T, val needsScroll: Boolean)

internal fun <T : Any> androidRecoveryRevealObservation(
    target: () -> T?,
    scroll: () -> T?,
): AndroidRecoveryReveal<T>? {
    val visible = target()
    if (visible != null) return AndroidRecoveryReveal(visible, needsScroll = false)
    return scroll()?.let { AndroidRecoveryReveal(it, needsScroll = true) }
}
