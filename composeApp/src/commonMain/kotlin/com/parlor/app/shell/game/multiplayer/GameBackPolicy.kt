package com.parlor.app.shell.game.multiplayer

/** The active game route already consumes platform Back; this only resolves its in-place priority. */
internal fun dispatchGameBack(
    request: Long,
    busy: Boolean,
    confirmingLeave: Boolean,
    dismissLeave: () -> Unit,
    closeTransient: () -> Boolean,
    requestLeave: () -> Unit,
) {
    when {
        request == 0L || busy -> Unit
        confirmingLeave -> dismissLeave()
        closeTransient() -> Unit
        else -> requestLeave()
    }
}
