package com.parlor.games.whodunit

import com.parlor.session.PlayMode

/**
 * Shipping entry policy for Whodunit's single-device flow.
 *
 * Whodunit needs several independently controlled players and private hand-off
 * screens. A single-person Solo session therefore is not a supported product
 * mode. Keep this rule outside Compose so callers cannot bypass it by invoking
 * the game flow directly.
 */
object WhodunitPlayModePolicy {
    fun supportsLocalEntry(playMode: PlayMode): Boolean =
        playMode is PlayMode.PassAndPlay
}
