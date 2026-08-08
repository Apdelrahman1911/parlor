package com.parlor.networking.room

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The local-multi-device room abstraction. Production binds this contract to
 * P2pKit's authenticated LAN/TCP transport; in-memory implementations are
 * retained for deterministic tests.
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

    /** Logical session lifecycle; game commands are legal only while active. */
    val lifecycle: StateFlow<RoomLifecycleState>
        get() = activeRoomLifecycle

    /**
     * The local device's player id. On the host this equals
     * [RoomInfo.hostPlayerId]; on a peer this is the peer's own id, distinct
     * from `info.hostPlayerId`. Used to map the local device into the game's
     * player list when the host sends `SessionStarting`.
     */
    val selfPlayerId: PlayerId

    /**
     * Connection-lifecycle events. Implementations that don't surface
     * peer-state transitions return an empty flow — bridges still detect
     * `HostLost` via snapshot-silence timeouts. Wave 9H-5 added this
     * flow; pre-9H transports may default to [emptyPeerEvents].
     */
    val peerEvents: SharedFlow<PeerEvent>
        get() = emptyPeerEvents

    /** Authenticated peers that supplied the correct room code and await host approval. */
    val pendingAdmissions: StateFlow<List<PendingAdmission>>
        get() = emptyPendingAdmissions

    /** Rejoin capability issued after admission; peer-side only. Never log it. */
    val rejoinToken: String?
        get() = null

    suspend fun approveAdmission(playerId: PlayerId): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    suspend fun rejectAdmission(playerId: PlayerId): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    /** Stop accepting new seats once gameplay begins. Existing rejoin remains allowed. */
    suspend fun closeAdmissions() = Unit

    suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError>
    suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError>
    suspend fun leave()
}

/** Shared empty flow for transports that don't emit peer events. */
internal val emptyPeerEvents: SharedFlow<PeerEvent> =
    MutableSharedFlow<PeerEvent>(replay = 0, extraBufferCapacity = 0).asSharedFlow()

internal val emptyPendingAdmissions: StateFlow<List<PendingAdmission>> =
    MutableStateFlow(emptyList())

internal val activeRoomLifecycle: StateFlow<RoomLifecycleState> =
    MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)

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

data class PendingAdmission(
    val playerId: PlayerId,
    val displayName: String,
    val isRejoin: Boolean,
)

/** Network/transport errors. */
sealed interface NetError {
    data object NotConnected : NetError
    data object Timeout : NetError
    data object PayloadTooLarge : NetError
    data object WrongCode : NetError
    data object HostDeclined : NetError
    data object RoomFull : NetError
    data object SessionStarted : NetError
    data object IncompatibleProtocol : NetError
    data object RateLimited : NetError
    data object RejoinExpired : NetError
    data object AlreadyConnected : NetError
    data object SecureStorageUnavailable : NetError
    /** A mutating command is already awaiting an authoritative outcome. */
    data object CommandInFlight : NetError
    data object SessionSuspended : NetError
    data class TransportFailure(val reason: String) : NetError
    data object Unauthorized : NetError
}
