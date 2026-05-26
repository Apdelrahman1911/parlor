package com.parlor.session

import com.parlor.core.ids.PlayerId

/**
 * How a game session is being played, captured as a typed value so the
 * rest of the app branches on it explicitly instead of inferring from
 * accidental signals like "is `selfPlayerId` null".
 *
 * Three modes today, each with its own behaviour:
 *
 *  - [Solo] — one phone, one user. No party-readiness ceremony at any
 *    phase; the host advances directly. Useful for testing and for games
 *    that genuinely make sense solo.
 *
 *  - [PassAndPlay] — one phone, multiple players passing it around.
 *    Privacy-sensitive screens (e.g. dossier reveal) belong here and
 *    use cover/handoff/hide UI ceremony. Readiness gates are owned by
 *    the host device on behalf of everyone at the table.
 *
 *  - [MultiDevice] — one phone per player, same room. Each peer device
 *    acknowledges its own readiness; the host's gated advance waits on
 *    real acks coming back over the room transport.
 *
 * [Solo] and [PassAndPlay] are both [isLocal] — single device, host
 * owns every player's state. They differ in UI ceremony only; the
 * session/readiness behaviour collapses for them.
 */
sealed interface PlayMode {

    /** Single device, single user. */
    data object Solo : PlayMode

    /** Single device, multiple players sharing it. */
    data object PassAndPlay : PlayMode

    /** Multiple devices, each player on their own phone. */
    data class MultiDevice(
        val selfPlayerId: PlayerId,
        val isHost: Boolean,
    ) : PlayMode
}

/** True when the whole session runs on one device (host owns everyone's state). */
val PlayMode.isLocal: Boolean
    get() = this is PlayMode.Solo || this is PlayMode.PassAndPlay

/**
 * True for the device that runs the canonical session: solo player,
 * pass-and-play host, or multi-device host. Multi-device peers are
 * the only `false` case.
 */
val PlayMode.isHost: Boolean
    get() = when (this) {
        is PlayMode.Solo, is PlayMode.PassAndPlay -> true
        is PlayMode.MultiDevice -> isHost
    }

/**
 * The local device's player identity if it has one, or `null` when
 * the device isn't tied to a single player (solo / pass-and-play).
 * Most UI should branch on the typed [PlayMode] instead of this.
 */
val PlayMode.selfPlayerId: PlayerId?
    get() = (this as? PlayMode.MultiDevice)?.selfPlayerId
