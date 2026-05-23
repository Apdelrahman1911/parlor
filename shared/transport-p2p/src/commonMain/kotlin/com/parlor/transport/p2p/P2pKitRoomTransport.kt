package com.parlor.transport.p2p

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.P2pSession
import dev.p2pkit.core.Peer
import dev.p2pkit.transport.lan.lan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Adapts P2pKit (`dev.p2pkit.core.P2pKit`) to Parlor's [RoomTransport].
 *
 * The mapping:
 *  - **host(config)** generates a short room code, builds a P2pKit with the
 *    `lan()` transport, advertises the code inside the broadcast deviceName,
 *    and returns a [HostP2pRoom]. Inbound P2pKit sessions become Parlor
 *    [RoomMember]s; outbound `HostMessage` rides each session's binary channel.
 *  - **join(code, displayName)** builds a P2pKit with `lan()`, discovers
 *    nearby peers, picks the first one whose advertised deviceName matches
 *    our room-code prefix, connects, and returns a [PeerP2pRoom].
 *
 * Wire format: `RoomMessage` is `@Serializable`, so we JSON-encode the
 * `HostMessage` / `PeerMessage` envelope and ship it as
 * `P2pMessage.Binary`. P2pKit's text channel is reserved; only the binary
 * path is used so payload size limits stay deterministic (4 MiB per send in
 * v0.3).
 *
 * Opt-in posture: this whole module is included only when
 * `parlor.p2p.enabled=true` in `gradle.properties` — pass-and-play builds
 * are completely unaffected. The adapter is verified end-to-end by
 * `P2pKitRoomTransportLoopbackTest` in `desktopTest`.
 */
class P2pKitRoomTransport(
    private val appId: AppId,
    private val deviceName: String,
    private val scope: CoroutineScope,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    },
) : RoomTransport {

    override val capability: TransportCapability = TransportCapability(
        supportsDiscovery = true,
        latencyHintMs = 25,
        maxPayloadBytes = 4 * 1024 * 1024,
    )

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
        return runCatching {
            val roomCode = generateRoomCode()
            val kit = P2pKit.create {
                this.appId = this@P2pKitRoomTransport.appId
                // Publish the room code in the deviceName so joining peers can
                // discriminate without a separate advertise-metadata API in
                // P2pKit v0.3. Joining peers match by exact prefix.
                this.deviceName = "${P2P_ROOM_PREFIX}$roomCode|$deviceName"
                transports { lan() }
            }
            kit.start()
            kit.startAdvertising()
            HostP2pRoom(
                kit = kit,
                roomCode = roomCode,
                roomDisplayName = config.roomDisplayName,
                hostPlayerId = PlayerId(kit.localPeerId.value),
                scope = scope,
                json = json,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(NetError.TransportFailure(it.message ?: "host failed")) },
        )
    }

    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> {
        return runCatching {
            val kit = P2pKit.create {
                this.appId = this@P2pKitRoomTransport.appId
                this.deviceName = displayName
                transports { lan() }
            }
            kit.start()
            kit.startDiscovery()
            // Wait for a peer whose advertised name carries our prefix + code.
            // We .first() so the call suspends until a match exists — caller
            // is responsible for cancellation/timeout policy.
            val hostPeer = kit.peers
                .first { peers ->
                    peers.any { it.name.startsWith("${P2P_ROOM_PREFIX}$code|") }
                }
                .first { it.name.startsWith("${P2P_ROOM_PREFIX}$code|") }
            val session = kit.connect(hostPeer)
            PeerP2pRoom(
                kit = kit,
                session = session,
                hostPeer = hostPeer,
                roomCode = code,
                scope = scope,
                json = json,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(NetError.TransportFailure(it.message ?: "join failed")) },
        )
    }

    private fun generateRoomCode(): String = buildString(6) {
        repeat(6) { append(ROOM_CODE_ALPHABET[Random.nextInt(ROOM_CODE_ALPHABET.length)]) }
    }

    companion object {
        const val P2P_ROOM_PREFIX = "parlor-room:"
        // Unambiguous alphabet — no 0/O, no 1/I — for in-person dictation.
        private const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}

// ============================================================================ Host room ==

internal class HostP2pRoom(
    private val kit: P2pKit,
    roomCode: String,
    roomDisplayName: String,
    private val hostPlayerId: PlayerId,
    private val scope: CoroutineScope,
    private val json: Json,
) : LocalRoom {

    private val _info = MutableStateFlow(
        RoomInfo(
            code = roomCode,
            displayName = roomDisplayName,
            hostPlayerId = hostPlayerId,
            status = RoomInfo.Status.Hosting,
        ),
    )
    private val _members = MutableStateFlow<List<RoomMember>>(emptyList())

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost: Boolean = true

    private val incomingPeerMessages = MutableSharedFlow<RoomMessage>(extraBufferCapacity = 256)
    override val incoming: Flow<RoomMessage> = incomingPeerMessages.asSharedFlow()

    private val sessionsByPlayer: MutableMap<PlayerId, P2pSession> = mutableMapOf()
    private val collectorJobs: MutableList<Job> = mutableListOf()

    private val acceptJob: Job = scope.launch {
        kit.incomingSessions.collect { session ->
            val playerId = PlayerId(session.peer.id.value)
            sessionsByPlayer[playerId] = session
            _members.value = _members.value + RoomMember(
                playerId = playerId,
                displayName = session.peer.name,
                connected = true,
            )
            collectorJobs += scope.launch {
                session.incoming.collect { msg ->
                    if (msg is P2pMessage.Binary) {
                        val decoded = json.decodeFromString(
                            RoomMessage.serializer(),
                            msg.bytes.decodeToString(),
                        )
                        incomingPeerMessages.tryEmit(decoded)
                    }
                }
            }
        }
    }

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        return runCatching {
            val payload = P2pMessage.Binary(
                json.encodeToString(RoomMessage.serializer(), message).encodeToByteArray(),
            )
            when (target) {
                SendTarget.Broadcast -> sessionsByPlayer.values.forEach { it.send(payload) }
                is SendTarget.Direct -> sessionsByPlayer[target.playerId]?.send(payload)
            }
            Unit
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { Result.Failure(NetError.TransportFailure(it.message ?: "send failed")) },
        )
    }

    /** A host cannot author a [PeerMessage]; calling this is a contract violation. */
    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun leave() {
        acceptJob.cancel()
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        sessionsByPlayer.values.forEach { runCatching { it.close() } }
        sessionsByPlayer.clear()
        kit.stop()
    }
}

// ============================================================================ Peer room ==

internal class PeerP2pRoom(
    private val kit: P2pKit,
    private val session: P2pSession,
    hostPeer: Peer,
    roomCode: String,
    scope: CoroutineScope,
    private val json: Json,
) : LocalRoom {

    private val _info = MutableStateFlow(
        RoomInfo(
            code = roomCode,
            displayName = hostPeer.name,
            hostPlayerId = PlayerId(hostPeer.id.value),
            status = RoomInfo.Status.Joined,
        ),
    )
    private val _members = MutableStateFlow<List<RoomMember>>(
        listOf(
            RoomMember(
                playerId = PlayerId(hostPeer.id.value),
                displayName = hostPeer.name,
                connected = true,
            ),
        ),
    )

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost: Boolean = false

    private val incomingHostMessages = MutableSharedFlow<RoomMessage>(extraBufferCapacity = 256)
    override val incoming: Flow<RoomMessage> = incomingHostMessages.asSharedFlow()

    private val collectorJob: Job = scope.launch {
        session.incoming.collect { msg ->
            if (msg is P2pMessage.Binary) {
                val decoded = json.decodeFromString(
                    RoomMessage.serializer(),
                    msg.bytes.decodeToString(),
                )
                incomingHostMessages.tryEmit(decoded)
            }
        }
    }

    /** A peer cannot author a [HostMessage]. */
    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        return runCatching {
            val payload = P2pMessage.Binary(
                json.encodeToString(RoomMessage.serializer(), message).encodeToByteArray(),
            )
            session.send(payload)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { Result.Failure(NetError.TransportFailure(it.message ?: "send failed")) },
        )
    }

    override suspend fun leave() {
        collectorJob.cancel()
        runCatching { session.close() }
        kit.stop()
    }
}
