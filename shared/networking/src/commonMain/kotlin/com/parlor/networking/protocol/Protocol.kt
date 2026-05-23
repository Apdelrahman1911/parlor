package com.parlor.networking.protocol

import com.parlor.core.ids.PlayerId
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
}

@Serializable
sealed interface PeerMessage : RoomMessage {
    @Serializable
    data class JoinRequest(val displayName: String) : PeerMessage
    @Serializable
    data class ActionSubmit(val payload: ByteArray) : PeerMessage
    @Serializable
    data object Heartbeat : PeerMessage
    @Serializable
    data object LeaveNotice : PeerMessage
}
