package com.parlor.session.multidevice

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
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * Phase 7 in-memory transport for the shape test. NOT a production transport.
 * Lets a single test process simulate host + N peer "rooms" by passing
 * channels in lieu of a network.
 */
class InMemoryRoomBus {

    private val hostInbox = Channel<PeerMessage>(Channel.UNLIMITED)
    private val peerInboxes = mutableMapOf<PlayerId, Channel<HostMessage>>()

    /**
     * Connection-event broadcast. Both the host-side and peer-side rooms
     * subscribe to this; the bus emits synthetic events when tests call
     * [emitPeerLeft] etc. Production transports don't use this — they
     * source events from their own transport callbacks.
     */
    private val _peerEvents = MutableSharedFlow<PeerEvent>(
        replay = 0,
        extraBufferCapacity = 32,
    )
    val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()

    val hostMessagesIn: Flow<PeerMessage> = hostInbox.consumeAsFlow()

    fun registerPeer(id: PlayerId) {
        peerInboxes.getOrPut(id) { Channel(Channel.UNLIMITED) }
    }

    fun peerMessagesIn(id: PlayerId): Flow<HostMessage> =
        peerInboxes.getValue(id).consumeAsFlow()

    suspend fun fromPeer(message: PeerMessage) {
        hostInbox.send(message)
    }

    suspend fun fromHost(target: SendTarget, message: HostMessage) {
        when (target) {
            SendTarget.Broadcast -> peerInboxes.values.forEach { it.send(message) }
            is SendTarget.Direct -> peerInboxes[target.playerId]?.send(message)
        }
    }

    // -------------------------------------------------- Test-only event API --

    /** Test hook: synthesise a peer-left event for host + the leaving peer. */
    suspend fun emitPeerLeft(playerId: PlayerId, displayName: String) {
        _peerEvents.emit(PeerEvent.PeerLeft(playerId, displayName))
    }

    /** Test hook: synthesise a peer-reconnected event. */
    suspend fun emitPeerReconnected(playerId: PlayerId, displayName: String) {
        _peerEvents.emit(PeerEvent.PeerReconnected(playerId, displayName))
    }

    /** Test hook: synthesise a host-lost event (peer-side). */
    suspend fun emitHostLost() {
        _peerEvents.emit(PeerEvent.HostLost)
    }

    /** Test hook: synthesise a host-restored event (peer-side). */
    suspend fun emitHostRestored() {
        _peerEvents.emit(PeerEvent.HostRestored)
    }
}

/** A peer-side room that reads/writes through an [InMemoryRoomBus]. */
class InMemoryPeerRoom(
    private val bus: InMemoryRoomBus,
    override val selfPlayerId: PlayerId,
    private val displayName: String,
    private val hostId: PlayerId,
) : LocalRoom {
    private val _info = MutableStateFlow(
        RoomInfo("local", "Parlor Room", hostId, RoomInfo.Status.Joined),
    )
    private val _members = MutableStateFlow<List<RoomMember>>(emptyList())

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost = false
    override val incoming: Flow<RoomMessage> = bus.peerMessagesIn(selfPlayerId)
    override val peerEvents: SharedFlow<PeerEvent> = bus.peerEvents

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        return Result.Failure(NetError.Unauthorized)  // Peers cannot host-broadcast.
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        bus.fromPeer(message)
        return Result.Success(Unit)
    }

    override suspend fun leave() {}
}

/** Stub transport — only useful for in-process tests. */
class InMemoryRoomTransport(
    private val bus: InMemoryRoomBus,
    private val hostPlayerId: PlayerId,
) : RoomTransport {

    override val capability = TransportCapability(
        supportsDiscovery = false,
        latencyHintMs = 0,
        maxPayloadBytes = Int.MAX_VALUE,
    )

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
        // Host-side LocalRoom is a thin facade over the bus; not used in the
        // shape test (the host runs the canonical session directly).
        return Result.Failure(NetError.TransportFailure("use shape-test driver directly"))
    }

    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> {
        // Caller supplies its own PlayerId via [InMemoryPeerRoom] construction;
        // this stub does not implement a real join handshake.
        return Result.Failure(NetError.TransportFailure("use shape-test driver directly"))
    }
}
