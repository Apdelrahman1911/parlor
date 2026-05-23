package com.parlor.session

import com.parlor.core.ids.PlayerId

/**
 * Who is currently looking at the device.
 *
 * In pass-and-play, the UI advances this through the ceremony (cover → player N
 * → cover → player N+1 → …). In multi-device, each device's viewer is fixed
 * (a peer device is always its owning player; the host device is `Host`).
 *
 * The session controller uses this to choose the correct projection.
 */
sealed interface ViewerContext {
    /** Cover/public mode — no player-private data exposed. */
    data object Public : ViewerContext

    /** A specific player is holding the device. */
    data class Player(val id: PlayerId) : ViewerContext

    /** The host device — sees all buckets. */
    data object Host : ViewerContext
}
