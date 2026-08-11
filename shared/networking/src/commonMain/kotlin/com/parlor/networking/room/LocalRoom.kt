package com.parlor.networking.room

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import kotlinx.coroutines.CancellationException
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
     * player list during the acknowledged `SessionStarting` transaction.
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

    /**
     * Atomically closes initial admission and returns the exact connected
     * roster frozen for gameplay. A transport must fail with
     * [NetError.CommandInFlight] rather than produce a partial roster while a
     * credential handoff is still committing. Resumable rejoin for seats in
     * the returned roster remains allowed after this barrier.
     */
    suspend fun closeAdmissions(): Result<List<RoomMember>, NetError> =
        Result.Success(members.value.filter(RoomMember::connected))

    /**
     * Permanently retires an admitted remote game seat and revokes every
     * transport capability that could reconnect it.
     *
     * This is deliberately separate from a transient socket disconnect. The
     * host calls it only after the frozen game roster has marked [playerId]
     * disconnected and the authoritative game has chosen to continue without
     * that player. Implementations must make membership removal, credential
     * revocation, and cancellation of any in-flight resume transaction one
     * atomic state transition. A repeated call for the same previously
     * admitted seat is idempotent.
     *
     * The default is fail-closed so a transport cannot silently claim that a
     * resumable credential was revoked when it has no such implementation.
     */
    suspend fun retireDisconnectedMember(playerId: PlayerId): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError>
    suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError>

    /**
     * Closes this physical room after admission when session start cannot be
     * completed, while retaining any transport-managed resumable membership.
     *
     * The retained membership lets the next attempt resume the already-frozen
     * game seat instead of trying to enter through initial admission again.
     * Transports without resumable memberships safely fall back to [leave].
     * Calling [leave] after this method must remain harmless.
     */
    suspend fun closeForRetry(): Result<Unit, NetError> {
        return try {
            leave()
            Result.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            Result.Failure(NetError.TransportFailure("room close failed"))
        }
    }

    /**
     * Permanently discards the resumable membership retained by a successful
     * [closeForRetry]. This is the explicit final-Leave/Back transaction for an
     * already-closed physical room; it must be ownership checked so stale room
     * cleanup cannot revoke an unrelated or replacement membership.
     *
     * Transports without a distinct resumable capability safely fall back to
     * [leave]. A failure means the caller must keep the recovery UI available
     * rather than claiming that the membership was discarded.
     */
    suspend fun discardRejoinCapability(): Result<Unit, NetError> {
        return try {
            leave()
            Result.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            Result.Failure(NetError.TransportFailure("membership discard failed"))
        }
    }

    /**
     * Performs an explicit, permanent player Leave.
     *
     * Unlike lifecycle/disposal cleanup, callers that represent a user action
     * must observe this result and navigate away only after it succeeds. A
     * resumable transport uses the transaction to revoke the persisted logical
     * membership as well as closing the physical room. A failure is retryable:
     * the UI must remain on a recovery surface because the credential may still
     * exist even though the socket has already been closed.
     *
     * Existing transports without a resumable capability retain their prior
     * behavior through this compatibility default.
     */
    suspend fun finalLeave(): Result<Unit, NetError> {
        return try {
            leave()
            Result.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            Result.Failure(NetError.TransportFailure("final leave failed"))
        }
    }

    /** Best-effort physical cleanup for lifecycle/disposal ownership. */
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
    /** Canonical player-facing name of the authoritative host. */
    val hostDisplayName: String,
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
    data object DisplayNameInUse : NetError
    data object SecureStorageUnavailable : NetError
    data object InvalidInput : NetError
    /** A mutating command is already awaiting an authoritative outcome. */
    data object CommandInFlight : NetError
    data object SessionSuspended : NetError
    data class TransportFailure(val reason: String) : NetError
    data object Unauthorized : NetError
}
