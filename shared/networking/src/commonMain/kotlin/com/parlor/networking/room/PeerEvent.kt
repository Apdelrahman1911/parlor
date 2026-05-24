package com.parlor.networking.room

import com.parlor.core.ids.PlayerId

/**
 * Connection-lifecycle events that a [LocalRoom] may emit via
 * [LocalRoom.peerEvents]. UI / bridges subscribe to translate transport
 * state changes into game-state actions and user-facing toasts.
 *
 * Two scopes:
 *  - **Host-side**: `PeerJoined` / `PeerLeft` / `PeerReconnected` carry a
 *    `PlayerId` so the host bridge knows which player to mark as
 *    disconnected/reconnected in the canonical game state.
 *  - **Peer-side**: `HostLost` / `HostRestored` signal whether the local
 *    device can see the host. `SelfOffline` / `SelfOnline` signal whether
 *    the local device's transport is up.
 *
 * Real transports (LAN, P2pKit) source these from their existing peer-
 * state callbacks or from heartbeat timeouts; the in-memory bus
 * synthesises them in test fixtures. A transport that cannot detect a
 * given event simply never emits it; the timeout-based fallbacks in the
 * bridges still surface the user-visible state.
 */
sealed interface PeerEvent {

    /** A peer has joined the room. Host-side. */
    data class PeerJoined(val playerId: PlayerId, val displayName: String) : PeerEvent

    /** A peer has left or dropped from the room. Host-side. */
    data class PeerLeft(val playerId: PlayerId, val displayName: String) : PeerEvent

    /**
     * A peer that was previously [PeerLeft] is back. Host-side.
     * Distinct from [PeerJoined] so the host bridge can decide whether
     * to ship them a fresh snapshot (reconnect) vs treat them as
     * brand-new (initial join).
     */
    data class PeerReconnected(val playerId: PlayerId, val displayName: String) : PeerEvent

    /** Peer device has lost connectivity with the host. Peer-side. */
    data object HostLost : PeerEvent

    /** Peer device's connection to the host is restored. Peer-side. */
    data object HostRestored : PeerEvent

    /** The local device's transport is offline (any side). */
    data object SelfOffline : PeerEvent

    /** The local device's transport is back online (any side). */
    data object SelfOnline : PeerEvent
}
