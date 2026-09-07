package com.parlor.networking.testing

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.state.Player
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolValidation
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.protocol.validateFor
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Test-only, already-admitted synthetic room. No P2pKit, OS lifecycle, credential,
 * socket, discovery, or physical LAN claim is made by this fixture.
 *
 * Real host coordinators must offer a session, receive Ready, and send a matching
 * commit before these transport-attested peer endpoints acknowledge it. Ready is
 * initially withheld so a test can verify that initialization actually waits.
 * A supplied production SessionProtocol and game-owned offer check bind responses;
 * this is not a room that unconditionally pretends a start succeeded.
 *
 * Run all methods/collectors on one caller-owned dispatcher. Retained observations
 * are bounded by the fixed roster and never retain snapshot payloads or roles.
 */
class ControlledStartRoom(
    val players: List<Player>,
    scope: CoroutineScope,
) : LocalRoom {
    private val bus = InMemoryRoomBus()
    private val host = InMemoryHostRoom(bus, players.first().id, players.first().displayName)
    override val info get() = host.info
    override val incoming get() = host.incoming
    override val peerEvents get() = host.peerEvents
    override val selfPlayerId get() = host.selfPlayerId
    override val isHost: Boolean = true
    private val memberState = MutableStateFlow(players.map { RoomMember(it.id, it.displayName, true) })
    override val members = memberState.asStateFlow()
    val lifecycleState = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)
    override val lifecycle = lifecycleState.asStateFlow()

    private val peers = players.drop(1).associate { player ->
        bus.registerPeer(player.id)
        player.id to InMemoryPeerRoom(bus, player.id, player.displayName, selfPlayerId)
    }
    private val offers = mutableMapOf<PlayerId, HostMessage.SessionStarting>()
    private val readyPeers = mutableSetOf<PlayerId>()
    private val committedPeers = mutableSetOf<PlayerId>()
    private val snapshotPeers = mutableSetOf<PlayerId>()
    private var protocol: SessionProtocol? = null
    private var acceptOffer: ((HostMessage.SessionStarting) -> Boolean)? = null
    private val jobs: List<Job> = peers.map { (id, peer) ->
        scope.launch {
            peer.incoming.collect { frame ->
                check(frame is HostMessage) { "Synthetic peer received a non-host frame" }
                observe(id, peer, frame)
            }
        }
    }

    private suspend fun observe(id: PlayerId, peer: InMemoryPeerRoom, frame: HostMessage) {
        when (frame) {
            is HostMessage.SessionStarting -> {
                val prior = offers[id]
                check(prior == null || prior == frame) { "Host changed its stable start offer" }
                offers[id] = frame
                if (protocol != null) ready(id, peer, frame)
            }
            is HostMessage.SessionStartCommitted -> {
                val expected = checkNotNull(protocol) { "Commit preceded Ready release" }
                check(id in readyPeers) { "Commit preceded this peer's Ready" }
                check(frame.validateFor(expected) == ProtocolValidation.Valid) {
                    "Commit protocol binding failed"
                }
                check(frame.startId == offers[id]?.startId) { "Commit changed start identity" }
                val result = peer.sendToHost(
                    PeerMessage.SessionStartCommitAck(
                        header = frame.header.copy(messageId = "commit-ack-${id.raw}-0123456789", sequence = 0L),
                        actor = id,
                        startId = frame.startId,
                    ),
                )
                check(result is Result.Success) { "Synthetic commit ACK send failed" }
                if (committedPeers.add(id)) {
                    // Production peer entry requests an authoritative snapshot
                    // after start. Eager revision zero may race commit delivery.
                    val requested = peer.sendToHost(
                        PeerMessage.SnapshotRequest(
                            header = frame.header.copy(messageId = "snapshot-${id.raw}-012345678901234", sequence = 0L),
                            actor = id,
                            lastAppliedRevision = -1L,
                        ),
                    )
                    check(requested is Result.Success) { "Synthetic initial snapshot request failed" }
                }
            }
            is HostMessage.PlayerSnapshot -> {
                val expected = checkNotNull(protocol) { "Snapshot preceded protocol binding" }
                check(frame.validateFor(expected) == ProtocolValidation.Valid) {
                    "Snapshot envelope binding failed"
                }
                // Like a real peer lobby, do not install eager snapshots before
                // observing a validated matching commit. Request above recovers it.
                if (id in committedPeers) snapshotPeers += id
            }
            else -> Unit // Drain bounded inboxes; never retain game payloads.
        }
    }

    val offeredPeerCount: Int get() = offers.size
    val readyPeerCount: Int get() = readyPeers.size
    val committedPeerCount: Int get() = committedPeers.size
    val snapshottedPeerCount: Int get() = snapshotPeers.size
    val workerCount: Int get() = jobs.count { !it.isCompleted }
    var admissionClosures: Int = 0
        private set
    var leaves: Int = 0
        private set

    suspend fun releaseReady(expected: SessionProtocol, validateOffer: (HostMessage.SessionStarting) -> Boolean) {
        check(protocol == null) { "Ready can be released only once" }
        check(offers.keys == peers.keys) { "Every frozen peer must receive an offer first" }
        protocol = expected
        acceptOffer = validateOffer
        peers.forEach { (id, peer) -> ready(id, peer, offers.getValue(id)) }
    }

    private suspend fun ready(id: PlayerId, peer: InMemoryPeerRoom, offer: HostMessage.SessionStarting) {
        val expected = checkNotNull(protocol)
        check(offer.validateFor(expected) == ProtocolValidation.Valid) { "Start protocol binding failed" }
        check(offer.players == players) { "Start roster differs from the frozen roster" }
        check(checkNotNull(acceptOffer).invoke(offer)) { "Game-specific start validation failed" }
        val result = peer.sendToHost(
            PeerMessage.SessionStartReady(
                header = offer.header.copy(messageId = "ready-${id.raw}-012345678901234", sequence = 0L),
                actor = id,
                startId = offer.startId,
            ),
        )
        check(result is Result.Success) { "Synthetic Ready send failed" }
        readyPeers += id
    }

    fun requireNoDrops() {
        check(bus.droppedHostMessageCount == 0) { "Synthetic host inbox dropped a frame" }
        check(bus.droppedPeerMessageCount == 0) { "Synthetic peer inbox dropped a frame" }
        check(bus.droppedPeerEventCount == 0) { "Synthetic peer event queue overflowed" }
    }

    override suspend fun closeAdmissions(): Result<List<RoomMember>, NetError> {
        check(lifecycleState.value == RoomLifecycleState.Active) { "Freeze requires an active room" }
        admissionClosures++
        return Result.Success(members.value.filter(RoomMember::connected))
    }

    override suspend fun send(
        target: SendTarget,
        message: HostMessage,
    ): Result<Unit, NetError> {
        if (message is HostMessage.PlayerSnapshot) {
            check(target is SendTarget.Direct && target.playerId in peers) {
                "Player snapshots require a direct admitted recipient"
            }
        }
        return host.send(target, message)
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun leave() {
        leaves++
        lifecycleState.value = RoomLifecycleState.Closed
        jobs.forEach { it.cancelAndJoin() }
    }
}
