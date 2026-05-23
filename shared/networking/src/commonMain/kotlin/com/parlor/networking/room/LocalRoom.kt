package com.parlor.networking.room

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The local-multi-device room abstraction. Phase 7 implements an in-memory
 * stub; Post-MVP wires real transports per platform (Android Nearby, iOS
 * Multipeer, Desktop mDNS+WebSocket).
 *
 * Multi-device privacy is enforced at this layer: the host computes per-player
 * private projections and uses [send] with [SendTarget.Direct] to deliver them;
 * `HostOnlyState` is never transmitted.
 */
interface LocalRoom {
    val info: StateFlow<RoomInfo>
    val members: StateFlow<List<RoomMember>>
    val isHost: Boolean
    val incoming: Flow<RoomMessage>

    suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError>
    suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError>
    suspend fun leave()
}

sealed interface SendTarget {
    data object Broadcast : SendTarget
    data class Direct(val playerId: PlayerId) : SendTarget
}

data class RoomInfo(
    val code: String,
    val displayName: String,
    val hostPlayerId: PlayerId,
    val status: Status,
) {
    enum class Status { Idle, Hosting, Joined, Lost }
}

data class RoomMember(
    val playerId: PlayerId,
    val displayName: String,
    val connected: Boolean,
)

/** Network/transport errors. */
sealed interface NetError {
    data object NotConnected : NetError
    data object Timeout : NetError
    data class TransportFailure(val reason: String) : NetError
    data object Unauthorized : NetError
}
