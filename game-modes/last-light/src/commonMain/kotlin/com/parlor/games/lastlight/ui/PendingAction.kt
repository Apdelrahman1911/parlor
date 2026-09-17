package com.parlor.games.lastlight.ui

/** In-flight commands are owned by the flow; the table only presents their status. */
enum class PendingAction {
    PLAY_CARDS,
    CHALLENGE,
    NEXT_ROUND,
    RETURN_TO_LOBBY,
}
