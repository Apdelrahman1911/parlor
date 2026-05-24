package com.parlor.networking.protocol

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import kotlinx.serialization.Serializable

/**
 * Wire protocol for in-room play. The same `SnapshotCodec` the engine uses for
 * disk snapshots also serializes these payloads, keeping a single format.
 *
 * Phase 7 ships a shape test that exercises this protocol in-memory. Post-MVP
 * adds real transports.
 */
@Serializable
sealed interface RoomMessage

@Serializable
sealed interface HostMessage : RoomMessage {
    @Serializable
    data class PublicStateSnapshot(val payload: ByteArray) : HostMessage
    @Serializable
    data class PublicStateDelta(val patch: ByteArray) : HostMessage
    @Serializable
    data class PrivateStateForPlayer(val target: PlayerId, val payload: ByteArray) : HostMessage
    @Serializable
    data class EventBroadcast(val payload: ByteArray) : HostMessage
    @Serializable
    data class EventDirect(val target: PlayerId, val payload: ByteArray) : HostMessage
    @Serializable
    data class TimerSync(val timerId: String, val deadlineEpochMs: Long) : HostMessage
    @Serializable
    data object EndSession : HostMessage

    /**
     * Sent by the host once their lobby's *Start Game* is tapped. Tells every
     * peer "stop showing the lobby; load this case and switch to the game
     * flow." Carries the canonical session shape so peers can stand up their
     * shadow controller with the same `SessionConfig` the host uses.
     *
     * `seed` makes deterministic replay easier across peers — though the host
     * is always authoritative, so peer-side reduction is never run; the seed
     * is shipped so peer-side UI that depends on it (e.g., debug overlays) is
     * available without an extra round trip.
     */
    @Serializable
    data class SessionStarting(
        val caseId: String,
        val modeId: String,
        val players: List<Player>,
        val seed: Long,
    ) : HostMessage
}

@Serializable
sealed interface PeerMessage : RoomMessage {
    @Serializable
    data class JoinRequest(val displayName: String) : PeerMessage
    /**
     * Peer-submitted action. [sender] is the peer's self-attested PlayerId
     * (always equal to its `LocalRoom.selfPlayerId` at submit time). The
     * host validates [sender] against the action's authority scope via
     * `WhodunitActionAuthority.isAllowed`; mismatches are silently dropped.
     */
    @Serializable
    data class ActionSubmit(val sender: PlayerId, val payload: ByteArray) : PeerMessage
    @Serializable
    data object Heartbeat : PeerMessage
    @Serializable
    data object LeaveNotice : PeerMessage
}
