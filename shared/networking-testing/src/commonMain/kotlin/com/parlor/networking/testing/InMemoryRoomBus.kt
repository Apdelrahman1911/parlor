package com.parlor.networking.testing

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Bounded, in-process room bus for cross-module protocol and game tests.
 *
 * This module is a test-support dependency only. It deliberately models the
 * production transport's authenticated actor stamping so authority tests do
 * not accidentally rely on peer-controlled identity fields.
 */
@OptIn(ExperimentalAtomicApi::class)
class InMemoryRoomBus {
    private val hostInbox = Channel<PeerMessage>(TEST_HOST_QUEUE_CAPACITY)
    private val peerInboxes = mutableMapOf<PlayerId, Channel<HostMessage>>()
    private val droppedHostMessages = AtomicInt(0)
    private val droppedPeerMessages = AtomicInt(0)
    private val droppedPeerEvents = AtomicInt(0)
    private val _peerEvents = MutableSharedFlow<PeerEvent>(
        replay = 0,
        extraBufferCapacity = TEST_EVENT_QUEUE_CAPACITY,
    )

    val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()
    val peerEventSubscriberCount: Int
        get() = _peerEvents.subscriptionCount.value
    val hostMessagesIn: Flow<PeerMessage> = hostInbox.consumeAsFlow()

    /** Number of peer-to-host frames rejected because the bounded inbox was full or closed. */
    val droppedHostMessageCount: Int
        get() = droppedHostMessages.load()

    /** Number of host-to-peer frames rejected by any bounded peer inbox. */
    val droppedPeerMessageCount: Int
        get() = droppedPeerMessages.load()

    /** Number of lifecycle events rejected because the shared event buffer was full or closed. */
    val droppedPeerEventCount: Int
        get() = droppedPeerEvents.load()

    fun registerPeer(id: PlayerId) {
        peerInboxes.getOrPut(id) { Channel(TEST_PEER_QUEUE_CAPACITY) }
    }

    fun peerMessagesIn(id: PlayerId): Flow<HostMessage> =
        peerInboxes.getValue(id).consumeAsFlow()

    suspend fun fromPeer(message: PeerMessage) {
        if (hostInbox.trySend(message).isFailure) {
            droppedHostMessages.addAndFetch(1)
        }
    }

    suspend fun fromHost(target: SendTarget, message: HostMessage) {
        when (target) {
            SendTarget.Broadcast -> peerInboxes.values.forEach { inbox ->
                if (inbox.trySend(message).isFailure) {
                    droppedPeerMessages.addAndFetch(1)
                }
            }
            is SendTarget.Direct -> peerInboxes.getValue(target.playerId).let { inbox ->
                if (inbox.trySend(message).isFailure) {
                    droppedPeerMessages.addAndFetch(1)
                }
            }
        }
    }

    suspend fun emitPeerLeft(playerId: PlayerId, displayName: String) {
        if (!_peerEvents.tryEmit(PeerEvent.PeerLeft(playerId, displayName))) {
            droppedPeerEvents.addAndFetch(1)
        }
    }

    suspend fun emitPeerReconnected(playerId: PlayerId, displayName: String) {
        if (!_peerEvents.tryEmit(PeerEvent.PeerReconnected(playerId, displayName))) {
            droppedPeerEvents.addAndFetch(1)
        }
    }

    suspend fun emitHostLost() {
        if (!_peerEvents.tryEmit(PeerEvent.HostLost)) {
            droppedPeerEvents.addAndFetch(1)
        }
    }

    suspend fun emitHostRestored() {
        if (!_peerEvents.tryEmit(PeerEvent.HostRestored)) {
            droppedPeerEvents.addAndFetch(1)
        }
    }

    private companion object {
        const val TEST_HOST_QUEUE_CAPACITY = 64
        const val TEST_PEER_QUEUE_CAPACITY = 32
        const val TEST_EVENT_QUEUE_CAPACITY = 32
    }
}

/** Host-side [LocalRoom] endpoint backed by [InMemoryRoomBus]. */
class InMemoryHostRoom(
    private val bus: InMemoryRoomBus,
    override val selfPlayerId: PlayerId,
    hostDisplayName: String,
) : LocalRoom {
    private val _info = MutableStateFlow(
        RoomInfo("local", hostDisplayName, selfPlayerId, RoomInfo.Status.Hosting),
    )
    private val _members = MutableStateFlow<List<RoomMember>>(emptyList())

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost = true
    override val incoming: Flow<RoomMessage> = bus.hostMessagesIn
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> {
        bus.fromHost(target, message)
        return Result.Success(Unit)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun leave() = Unit
}

/** A peer-side room whose sender identity is bound by construction. */
class InMemoryPeerRoom(
    private val bus: InMemoryRoomBus,
    override val selfPlayerId: PlayerId,
    @Suppress("UNUSED_PARAMETER") displayName: String,
    hostId: PlayerId,
    initialStatus: RoomInfo.Status = RoomInfo.Status.Joined,
) : LocalRoom {
    private val _info = MutableStateFlow(
        RoomInfo("local", "Parlor Room", hostId, initialStatus),
    )
    private val _members = MutableStateFlow<List<RoomMember>>(emptyList())

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost = false
    override val incoming: Flow<RoomMessage> = bus.peerMessagesIn(selfPlayerId)
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents

    var simulateNotConnected: Boolean = false

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> = Result.Failure(NetError.Unauthorized)

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        if (simulateNotConnected) return Result.Failure(NetError.NotConnected)
        bus.fromPeer(message.withAuthenticatedActor(selfPlayerId))
        return Result.Success(Unit)
    }

    override suspend fun leave() = Unit
}

/** Mirrors the actor overwrite performed by the real authenticated transport. */
private fun PeerMessage.withAuthenticatedActor(playerId: PlayerId): PeerMessage = when (this) {
    is PeerMessage.AdmissionRequest -> copy(actor = playerId)
    is PeerMessage.AdmissionConfirmed -> copy(actor = playerId)
    is PeerMessage.AdmissionCommitAck -> copy(actor = playerId)
    is PeerMessage.AdmissionReady -> copy(actor = playerId)
    is PeerMessage.ResumeRequested -> copy(actor = playerId)
    is PeerMessage.ResumeConfirmed -> copy(actor = playerId)
    is PeerMessage.ResumeCommitAck -> copy(actor = playerId)
    is PeerMessage.ResumeReady -> copy(actor = playerId)
    is PeerMessage.ClientCommand -> copy(actor = playerId)
    is PeerMessage.SnapshotRequest -> copy(actor = playerId)
    is PeerMessage.SessionHeartbeat -> copy(actor = playerId)
    is PeerMessage.CommandOutcomeRequest -> copy(actor = playerId)
    is PeerMessage.SessionStartReady -> copy(actor = playerId)
    is PeerMessage.SessionStartCommitAck -> copy(actor = playerId)
    else -> this
}
