package com.parlor.transport.p2p

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.AdmissionRejection
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARLOR_PROTOCOL_MAJOR
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PendingAdmission
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.JoinConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.networking.security.SecureIds
import dev.p2pkit.core.AppId
import dev.p2pkit.core.ConnectionState
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.P2pSession
import dev.p2pkit.core.Peer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/**
 * Transport diagnostics are disabled by default. Room codes, peer identifiers,
 * player names, payloads, and exception messages are never written to release
 * logs. A future observability adapter may emit only fixed event codes and
 * aggregate timings after consent.
 */
@Suppress("UNUSED_PARAMETER")
private fun p2pLog(message: String) = Unit

internal interface AppLifecycleAwareRoom {
    suspend fun appBackgrounded(atEpochMillis: Long)
    suspend fun appForegrounded(atEpochMillis: Long)
}

/**
 * Adapts P2pKit (`dev.p2pkit.core.P2pKit`) to Parlor's [RoomTransport].
 *
 * The mapping:
 *  - **host(config)** generates a short room code, builds a P2pKit with the
 *    `lan()` transport, advertises only a generic Parlor service name,
 *    and returns a [HostP2pRoom]. Approved inbound sessions become Parlor
 *    [RoomMember]s; outbound `HostMessage` rides each session's binary channel.
 *  - **join(code, displayName)** builds a P2pKit with `lan()`, discovers
 *    nearby Parlor peers, connects, and proves the room code inside the
 *    encrypted channel before returning a [PeerP2pRoom].
 *
 * Wire format: `RoomMessage` is compact CBOR encoded and shipped as
 * `P2pMessage.Binary`. P2pKit's text channel is reserved; only the binary
 * path is used so Parlor can enforce a smaller application-frame ceiling than
 * P2pKit's general transfer API.
 */
class P2pKitRoomTransport(
    private val appId: AppId,
    private val deviceName: String,
    private val scope: CoroutineScope,
    private val kitFactory: P2pKitFactory,
    private val codec: RoomMessageCodec = RoomMessageCodec(),
    // Upper bound on how long [join] will wait for a matching, *fresh*
    // host advertisement before failing with [NetError.Timeout]. Kept
    // configurable so tests can fail quickly instead of waiting the full
    // production budget (10s). Production wiring uses the default.
    private val joinTimeoutMs: Long = DEFAULT_JOIN_TIMEOUT_MS,
    // Maximum age of `kit.lastSeen(peerId)` for a peer with a populated
    // timestamp to be considered live. Older = treated as a stale Bonjour
    // leftover (a common iOS quirk after the host disappears without
    // flushing its goodbye) and ignored. Peers whose lastSeen is `null`
    // are accepted on the strength of the emission itself, because some
    // platforms — notably the current P2pKit Android adapter — do not
    // populate per-peer timestamps, and a strict null-rejects gate
    // blocks every Android-side join even when discovery is otherwise
    // working.
    private val peerFreshnessWindowMs: Long = DEFAULT_PEER_FRESHNESS_WINDOW_MS,
) : RoomTransport {

    private sealed interface AppLifecycleEvent {
        data class Backgrounded(val atEpochMillis: Long) : AppLifecycleEvent
        data class Foregrounded(val atEpochMillis: Long) : AppLifecycleEvent
        data class RoomClosed(val registrationId: String) : AppLifecycleEvent
    }

    private data class ActiveLifecycleRoom(
        val registrationId: String,
        val room: AppLifecycleAwareRoom,
    )

    private val lifecycleEvents = Channel<AppLifecycleEvent>(
        capacity = APP_LIFECYCLE_EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val activeLifecycleMutex = Mutex()
    private var activeLifecycleRoom: ActiveLifecycleRoom? = null
    private var appIsBackgrounded: Boolean = false
    private var lastBackgroundedAt: Long? = null

    init {
        scope.launch {
            for (event in lifecycleEvents) {
                try {
                    when (event) {
                        is AppLifecycleEvent.Backgrounded -> {
                            val room = activeLifecycleMutex.withLock {
                                appIsBackgrounded = true
                                lastBackgroundedAt = event.atEpochMillis
                                activeLifecycleRoom?.room
                            }
                            room?.appBackgrounded(event.atEpochMillis)
                        }
                        is AppLifecycleEvent.Foregrounded -> {
                            val room = activeLifecycleMutex.withLock {
                                appIsBackgrounded = false
                                activeLifecycleRoom?.room
                            }
                            room?.appForegrounded(event.atEpochMillis)
                        }
                        is AppLifecycleEvent.RoomClosed -> activeLifecycleMutex.withLock {
                            if (activeLifecycleRoom?.registrationId == event.registrationId) {
                                activeLifecycleRoom = null
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    failure.rethrowIfCancellation()
                    p2pLog("lifecycle: transition failed (${failure::class.simpleName})")
                }
            }
        }
    }

    override fun notifyAppBackgrounded() {
        lifecycleEvents.trySend(AppLifecycleEvent.Backgrounded(nowMillis()))
    }

    override fun notifyAppForegrounded() {
        lifecycleEvents.trySend(AppLifecycleEvent.Foregrounded(nowMillis()))
    }

    private suspend fun registerLifecycleRoom(
        registrationId: String,
        room: AppLifecycleAwareRoom,
    ) {
        val backgroundedAt = activeLifecycleMutex.withLock {
            activeLifecycleRoom = ActiveLifecycleRoom(registrationId, room)
            lastBackgroundedAt.takeIf { appIsBackgrounded }
        }
        if (backgroundedAt != null) {
            room.appBackgrounded(backgroundedAt)
        }
    }

    private fun roomClosed(registrationId: String) {
        lifecycleEvents.trySend(AppLifecycleEvent.RoomClosed(registrationId))
    }

    override val capability: TransportCapability = TransportCapability(
        // The UI currently uses explicit room-code entry. Do not claim the
        // RoomTransport discovery contract until discoverRooms() is mapped.
        supportsDiscovery = false,
        latencyHintMs = 25,
        maxPayloadBytes = MAX_ROOM_FRAME_BYTES,
    )

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
        p2pLog("host: entry roomDisplayName='${config.roomDisplayName}' deviceName='$deviceName'")
        return runCatching {
            val roomCode = generateRoomCode()
            // The low-entropy admission code never leaves the encrypted
            // channel. Discovery advertises only a generic Parlor room.
            val advertisedDeviceName = "$P2P_ROOM_PREFIX$deviceName"
            val lifecycleRegistrationId = SecureIds.id128()
            p2pLog("host: generated a room code; advertising a generic Parlor service")
            val kit = kitFactory.createKit(
                appId = appId,
                deviceName = advertisedDeviceName,
            )
            p2pLog("host: kit created localPeerId=${kit.localPeerId.value}; calling start()")
            // p2p-005: if start()/startAdvertising() throws, stop the kit before
            // propagating — otherwise the started instance (sockets, JmDNS/NSD
            // registration, kit scope) leaks for the process lifetime. join()
            // already does this; host() didn't.
            var room: HostP2pRoom? = null
            try {
                kit.start()
                // Construct the room first so its incomingSessions collector is
                // subscribed before the service becomes discoverable. Otherwise a
                // fast peer can connect into P2pKit's replay-zero session flow.
                room = HostP2pRoom(
                    kit = kit,
                    roomCode = roomCode,
                    roomDisplayName = config.roomDisplayName,
                    hostPlayerId = PlayerId(kit.localPeerId.value),
                    scope = scope,
                    codec = codec,
                    onClosed = { roomClosed(lifecycleRegistrationId) },
                )
                p2pLog("host: start() returned; calling startAdvertising()")
                kit.startAdvertising()
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    if (room == null) {
                        kit.stopAfterFailure()
                    } else {
                        room.leave()
                    }
                }
                t.rethrowIfCancellation()
                throw t
            }
            p2pLog("host: startAdvertising() returned; ready to accept incoming sessions")
            checkNotNull(room).also {
                registerLifecycleRoom(lifecycleRegistrationId, it)
            }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = {
                it.rethrowIfCancellation()
                p2pLog("host: FAILED message='${it.message}'")
                Result.Failure(NetError.TransportFailure(it.message ?: "host failed"))
            },
        )
    }

    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> {
        return join(JoinConfig(code = code, displayName = displayName))
    }

    override suspend fun join(config: JoinConfig): Result<LocalRoom, NetError> {
        p2pLog("join: entry")
        val kit = try {
            kitFactory.createKit(appId = appId, deviceName = config.displayName)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
            return Result.Failure(NetError.TransportFailure(t.message ?: "kit initialization failed"))
        }
        try {
            kit.also {
                p2pLog("join: kit created localPeerId=${it.localPeerId.value}; calling start()")
                it.start()
                p2pLog("join: start() returned; calling startDiscovery()")
                it.startDiscovery()
                p2pLog("join: startDiscovery() returned; awaiting fresh host advertisement")
            }
        } catch (t: Throwable) {
            kit.stopAfterFailure()
            t.rethrowIfCancellation()
            p2pLog("join: kit setup FAILED message='${t.message}'")
            return Result.Failure(NetError.TransportFailure(t.message ?: "join failed"))
        }
        return try {
            val result = withTimeout<Result<LocalRoom, NetError>>(joinTimeoutMs) {
                val attemptedPeerIds = mutableSetOf<String>()
                var admissionResult: Result<LocalRoom, NetError>? = null
                while (admissionResult == null) {
                    val hostPeer = kit.peers
                        .onEach { peers -> logPeerSnapshot(peers, P2P_ROOM_PREFIX, kit) }
                        .first { peers ->
                            peers.any {
                                it.id.value !in attemptedPeerIds && it.isFreshParlorHost(kit)
                            }
                        }
                        .first {
                            it.id.value !in attemptedPeerIds && it.isFreshParlorHost(kit)
                        }
                    attemptedPeerIds += hostPeer.id.value
                    val session = try {
                        kit.connect(hostPeer)
                    } catch (t: Throwable) {
                        t.rethrowIfCancellation()
                        continue
                    }
                    when (
                        val admission = awaitAdmission(
                            session = session,
                            code = config.code,
                            displayName = config.displayName,
                            rejoinToken = config.rejoinToken,
                            selfPlayerId = PlayerId(kit.localPeerId.value),
                        )
                    ) {
                        is HostMessage.AdmissionAccepted -> {
                            kit.stopDiscovery()
                            val lifecycleRegistrationId = SecureIds.id128()
                            val peerRoom = PeerP2pRoom(
                                kit = kit,
                                session = session,
                                hostPeer = hostPeer,
                                roomCode = config.code,
                                rejoinToken = admission.rejoinToken,
                                scope = scope,
                                codec = codec,
                                onClosed = { roomClosed(lifecycleRegistrationId) },
                            )
                            registerLifecycleRoom(lifecycleRegistrationId, peerRoom)
                            admissionResult = Result.Success(
                                peerRoom,
                            )
                        }
                        is HostMessage.AdmissionRejected -> {
                            runCatching { session.close() }
                            if (admission.reason == AdmissionRejection.WrongCode) {
                                val anotherVisibleHost = kit.peers.value.any {
                                    it.id.value !in attemptedPeerIds && it.isFreshParlorHost(kit)
                                }
                                if (anotherVisibleHost) continue
                            }
                            admissionResult = Result.Failure(admission.reason.toNetError())
                        }
                        else -> error("Admission wait returned a non-admission message")
                    }
                }
                admissionResult
            }
            if (result is Result.Failure) {
                runCatching { kit.stopDiscovery() }
                kit.stopAfterFailure()
            }
            result
        } catch (_: TimeoutCancellationException) {
            // No fresh advertisement appeared within the budget: clean up
            // the kit we started so we don't leak a discovering instance
            // for an abandoned join attempt.
            p2pLog("join: TIMEOUT")
            kit.stopAfterFailure()
            Result.Failure(NetError.Timeout)
        } catch (t: Throwable) {
            kit.stopAfterFailure()
            t.rethrowIfCancellation()
            p2pLog("join: FAILED with exception type=${t::class.simpleName} message='${t.message}'")
            Result.Failure(NetError.TransportFailure(t.message ?: "join failed"))
        }
    }

    private suspend fun awaitAdmission(
        session: P2pSession,
        code: String,
        displayName: String,
        rejoinToken: String?,
        selfPlayerId: PlayerId,
    ): HostMessage = coroutineScope {
        val response = async(start = CoroutineStart.UNDISPATCHED) {
            session.incoming
                .filterIsInstance<P2pMessage.Binary>()
                .mapNotNull { frame ->
                    if (frame.bytes.size > MAX_ROOM_FRAME_BYTES) return@mapNotNull null
                    runCatching {
                        codec.decode(frame.bytes)
                    }.getOrNull() as? HostMessage
                }
                .first {
                    it is HostMessage.AdmissionAccepted || it is HostMessage.AdmissionRejected
                }
        }
        val request = PeerMessage.AdmissionRequest(
            protocol = ProtocolVersion(),
            actor = selfPlayerId,
            roomCode = code,
            displayName = displayName,
            rejoinToken = rejoinToken,
        )
        val requestBytes = codec.encode(request)
        check(requestBytes.size <= MAX_ROOM_FRAME_BYTES)
        while (!response.isCompleted) {
            session.send(P2pMessage.Binary(requestBytes))
            delay(ADMISSION_RETRY_MS)
        }
        response.await()
    }

    private fun logPeerSnapshot(peers: List<Peer>, expectedPrefix: String, kit: P2pKit) {
        if (peers.isEmpty()) {
            p2pLog("join: kit.peers emitted size=0 (no peers visible yet)")
            return
        }
        p2pLog("join: kit.peers emitted size=${peers.size} expectedPrefix='$expectedPrefix'")
        peers.forEachIndexed { i, p ->
            val lastSeen = kit.lastSeen(p.id)
            val age = if (lastSeen != null) "${nowMillis() - lastSeen}ms ago" else "null (platform doesn't track)"
            val prefixOk = p.name.startsWith(expectedPrefix)
            p2pLog("join:   peer[$i] id=${p.id.value} name='${p.name}' lastSeen=$age prefixMatch=$prefixOk")
        }
    }

    private fun Peer.isFreshParlorHost(kit: P2pKit): Boolean {
        if (!name.startsWith(P2P_ROOM_PREFIX)) {
            p2pLog("freshness: rejected non-Parlor service")
            return false
        }
        // Absence of a per-peer timestamp is NOT evidence of staleness —
        // it's the absence of evidence. Trust the emission. The Android
        // adapter currently follows this path on every discovered peer.
        val seenAt = kit.lastSeen(id)
        if (seenAt == null) {
            p2pLog("freshness: ACCEPT peer id=${id.value} name='$name' reason=null-lastSeen (trust emission)")
            return true
        }
        val ageMs = nowMillis() - seenAt
        val fresh = ageMs <= peerFreshnessWindowMs
        if (fresh) {
            p2pLog("freshness: ACCEPT peer id=${id.value} name='$name' ageMs=$ageMs windowMs=$peerFreshnessWindowMs")
        } else {
            p2pLog("freshness: REJECT peer id=${id.value} name='$name' reason=stale ageMs=$ageMs windowMs=$peerFreshnessWindowMs")
        }
        return fresh
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun nowMillis(): Long =
        kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun generateRoomCode(): String =
        SecureIds.randomCharacters(length = 6, alphabet = ROOM_CODE_ALPHABET)

    companion object {
        const val P2P_ROOM_PREFIX = "parlor-room|"
        // Unambiguous alphabet — no 0/O, no 1/I — for in-person dictation.
        private const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        // Production budget for join() discovery + connect. The user can
        // dictate a 6-char code, hit Join, and within 10s either see a
        // room or get "no room found" — anything longer feels broken.
        internal const val DEFAULT_JOIN_TIMEOUT_MS: Long = 120_000L
        internal const val ADMISSION_RETRY_MS: Long = 400L
        internal const val ADMISSION_REJECTION_FLUSH_MS: Long = 100L
        internal const val REJOIN_GRACE_MS: Long = 120_000L
        internal const val MAX_DISPLAY_NAME_LENGTH: Int = 32
        // P2pKit publishes lastSeen on every heartbeat; a live host on the
        // same LAN refreshes well inside a 5s window. Tightening this
        // further risks false-rejecting a host whose Wi-Fi link briefly
        // hiccupped; loosening it re-opens the stale-ghost bug.
        internal const val DEFAULT_PEER_FRESHNESS_WINDOW_MS: Long = 5_000L
        // After stopAdvertising() the underlying Bonjour stack still has
        // to push the "service-removed" announcement on the wire; without
        // a tiny pause peers can keep showing the now-dead room for the
        // next service-eviction interval (5–30s on iOS in practice).
        internal const val BONJOUR_GOODBYE_FLUSH_MS: Long = 150L
        // After a peer sends LeaveNotice we briefly hold the session open
        // so the bytes actually flush over TCP before close() yanks the
        // socket. Without this, the notice can be lost in transit and the
        // host falls back to the slower TCP-teardown path.
        internal const val LEAVE_NOTICE_FLUSH_MS: Long = 100L
        // Application messages are intentionally much smaller than P2pKit's
        // general 4 MiB transfer ceiling.
        internal const val MAX_ROOM_FRAME_BYTES: Int =
            com.parlor.networking.protocol.MAX_ROOM_FRAME_BYTES
        internal const val APP_RESUME_GRACE_MS: Long = 120_000L
        private const val APP_LIFECYCLE_EVENT_CAPACITY: Int = 8
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

/** Ensure a partially-started kit is released even when its owner is cancelled. */
private suspend fun P2pKit.stopAfterFailure() {
    withContext(NonCancellable) {
        try {
            stop()
        } catch (failure: Throwable) {
            p2pLog("cleanup: kit stop failed (${failure::class.simpleName})")
        }
    }
}

private fun AdmissionRejection.toNetError(): NetError = when (this) {
    AdmissionRejection.WrongCode -> NetError.WrongCode
    AdmissionRejection.HostDeclined -> NetError.HostDeclined
    AdmissionRejection.RoomFull -> NetError.RoomFull
    AdmissionRejection.SessionStarted -> NetError.SessionStarted
    AdmissionRejection.IncompatibleProtocol -> NetError.IncompatibleProtocol
    AdmissionRejection.InvalidRequest -> NetError.Unauthorized
    AdmissionRejection.RateLimited -> NetError.RateLimited
}

// ============================================================================ Host room ==

internal class HostP2pRoom(
    private val kit: P2pKit,
    private val roomCode: String,
    roomDisplayName: String,
    private val hostPlayerId: PlayerId,
    private val scope: CoroutineScope,
    private val codec: RoomMessageCodec,
    private val onClosed: () -> Unit = {},
    private val appResumeGraceMs: Long = P2pKitRoomTransport.APP_RESUME_GRACE_MS,
) : LocalRoom, AppLifecycleAwareRoom {

    override val selfPlayerId: PlayerId = hostPlayerId

    private val _info = MutableStateFlow(
        RoomInfo(
            code = roomCode,
            displayName = roomDisplayName,
            hostPlayerId = hostPlayerId,
            status = RoomInfo.Status.Hosting,
        ),
    )
    private val _members = MutableStateFlow<List<RoomMember>>(emptyList())
    private val _pendingAdmissions = MutableStateFlow<List<PendingAdmission>>(emptyList())
    private val _peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 64)
    private val _lifecycle = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost: Boolean = true
    override val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()
    override val pendingAdmissions = _pendingAdmissions.asStateFlow()
    override val lifecycle = _lifecycle.asStateFlow()

    // A room has exactly one protocol owner. A channel buffers startup frames
    // until that owner subscribes; replay-zero SharedFlow previously dropped
    // SessionStarting/snapshots during content loading.
    private val incomingPeerMessages = Channel<RoomMessage>(Channel.UNLIMITED)
    override val incoming: Flow<RoomMessage> = incomingPeerMessages.receiveAsFlow()

    // Authoritative per-player membership. Survives transient session drops so
    // a returning peer is recognised as a reconnect (same playerId) rather
    // than a brand-new join. Updated under the assumption all session-state
    // callbacks run on [scope], so coarse mutex-free mutation is safe.
    private val membersByPlayer: MutableMap<PlayerId, RoomMember> = mutableMapOf()
    private val sessionsByPlayer: MutableMap<PlayerId, P2pSession> = mutableMapOf()
    private data class PendingConnection(
        val session: P2pSession,
        val displayName: String,
        val isRejoin: Boolean,
    )
    private val pendingByPlayer: MutableMap<PlayerId, PendingConnection> = mutableMapOf()
    private val rejoinTokenByPlayer: MutableMap<PlayerId, String> = mutableMapOf()
    private val rejoinDeadlineByPlayer: MutableMap<PlayerId, Long> = mutableMapOf()
    private var admissionsClosed: Boolean = false
    // Tracks every PlayerId we have ever accepted a session from in this
    // room's lifetime. A peer that fully disconnects (PeerLeft fired) and
    // then reconnects with the same PlayerId is a *reconnect*, not a new
    // join: both game-side host bridges (Mafia + Whodunit) no-op on
    // PeerJoined and only re-ship the snapshot on PeerReconnected, so
    // emitting the wrong event would strand the recovered peer in a
    // disconnected state forever.
    private val previouslySeenPlayerIds: MutableSet<PlayerId> = mutableSetOf()
    private val collectorJobs: MutableList<Job> = mutableListOf()

    // p2p-001: serialize every access to the membership maps above. `scope` is
    // Dispatchers.Default (multi-threaded) and the accept loop, each per-session
    // collector, and send() all touch these maps concurrently — the old
    // "all callbacks run on scope so mutex-free is safe" assumption was false
    // (genuine data race + ConcurrentModificationException while send() iterates).
    // A Mutex (rather than a confined dispatcher) keeps the coroutines on their
    // original dispatcher, preserving deterministic test scheduling. publishMembers()
    // is only ever called while this lock is held and never re-locks.
    // See PROBLEMS_PARLOR.md → p2p-001.
    private val stateMutex = Mutex()
    private var lifecycleExpiryJob: Job? = null

    // p2p-016: leave() runs from a "Leave" tap AND from DisposableEffect.onDispose,
    // so a real double-call is expected. kit.stop() is terminal — a second call
    // throws IllegalStateException. Guard so leave() is idempotent. (Plain var:
    // leave() calls are sequential on the UI/teardown path, and kit.stop() is
    // additionally runCatching-guarded; no cross-platform @Volatile needed.)
    private var left = false

    private val acceptJob: Job = scope.launch {
        kit.incomingSessions.collect { session ->
            handleIncomingSession(session)
        }
    }

    private suspend fun handleIncomingSession(session: P2pSession) {
        val playerId = PlayerId(session.peer.id.value)
        val displayName = session.peer.name

        collectorJobs += scope.launch {
            session.incoming.collect { msg ->
                if (msg !is P2pMessage.Binary) return@collect
                if (msg.bytes.size > P2pKitRoomTransport.MAX_ROOM_FRAME_BYTES) {
                    p2pLog("host: dropping oversized frame")
                    return@collect
                }
                // p2p-002: never let a malformed/oversized/version-skewed frame
                // throw out of this collect — that would cancel the coroutine and
                // permanently kill THIS session's inbound stream. Skip + log.
                val rawDecoded = runCatching {
                    codec.decode(msg.bytes)
                }.getOrElse {
                    p2pLog("host: dropping undecodable frame from peerId=${playerId.raw} (${it::class.simpleName})")
                    return@collect
                }
                // wu-ui-01 / NN-03: the actor identity is the AUTHENTICATED
                // session peer id, never the self-attested ActionSubmit.sender.
                // Overwrite the body field so the authority gate downstream can
                // only ever see who actually owns this connection — a peer can no
                // longer forge another player's vote/action by lying in the body.
                val decoded = when (rawDecoded) {
                    is PeerMessage.ActionSubmit -> rawDecoded.copy(sender = playerId)
                    is PeerMessage.AdmissionRequest -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.ClientCommand -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.SnapshotRequest -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.SessionHeartbeat -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.CommandOutcomeRequest -> rawDecoded.copy(actor = playerId)
                    else -> rawDecoded
                }
                val admitted = stateMutex.withLock {
                    sessionsByPlayer[playerId] === session
                }
                if (admitted && decoded is PeerMessage.AdmissionRequest) {
                    val token = stateMutex.withLock { rejoinTokenByPlayer[playerId] }
                    if (token != null) {
                        sendRaw(session, HostMessage.AdmissionAccepted(playerId, token))
                    }
                    return@collect
                }
                if (!admitted) {
                    if (decoded is PeerMessage.AdmissionRequest) {
                        handleAdmissionRequest(
                            playerId = playerId,
                            transportDisplayName = displayName,
                            session = session,
                            request = decoded,
                        )
                    }
                    // No gameplay or lifecycle frame is accepted before the
                    // encrypted room-code + host-approval handshake.
                    return@collect
                }
                if (decoded is PeerMessage.LeaveNotice) {
                    // Application-level "I'm leaving" — process it
                    // immediately so the lobby reflects the departure
                    // without waiting for TCP teardown (which can lag
                    // by seconds on flaky LANs). LeaveNotice is
                    // transport plumbing; do NOT forward it to game
                    // modules via incomingPeerMessages.
                    p2pLog("host: received LeaveNotice from peerId=${playerId.raw}")
                    handleExplicitLeave(playerId, displayName, session)
                    return@collect
                }
                incomingPeerMessages.send(decoded)
            }
        }

        collectorJobs += scope.launch {
            // Tracks whether THIS session has previously dropped into a
            // soft-disconnect (Reconnecting) state, so the Connected
            // transition out of it can emit PeerReconnected without
            // spuriously emitting it on the very first Connected.
            var wasReconnecting = false
            session.state.collect { state ->
                when (state) {
                    ConnectionState.Reconnecting -> {
                        wasReconnecting = true
                        stateMutex.withLock {
                            val current = membersByPlayer[playerId]
                            if (current != null && current.connected) {
                                membersByPlayer[playerId] = current.copy(connected = false)
                                publishMembers()
                            }
                        }
                    }
                    ConnectionState.Connected -> {
                        if (wasReconnecting) {
                            wasReconnecting = false
                            val restored = stateMutex.withLock {
                                val current = membersByPlayer[playerId]
                                if (
                                    sessionsByPlayer[playerId] === session &&
                                    current != null &&
                                    !current.connected
                                ) {
                                    membersByPlayer[playerId] = current.copy(connected = true)
                                    publishMembers()
                                    true
                                } else {
                                    false
                                }
                            }
                            if (restored) {
                                _peerEvents.emit(PeerEvent.PeerReconnected(playerId, displayName))
                            }
                        }
                    }
                    ConnectionState.Closed, ConnectionState.Failed -> {
                        // Only act if this is still the registered session
                        // for this playerId — a newer session may have
                        // superseded it via handleIncomingSession.
                        val removed = stateMutex.withLock {
                            if (pendingByPlayer[playerId]?.session === session) {
                                pendingByPlayer.remove(playerId)
                                publishPendingAdmissions()
                            }
                            if (sessionsByPlayer[playerId] === session) {
                                sessionsByPlayer.remove(playerId)
                                membersByPlayer[playerId]?.let { current ->
                                    membersByPlayer[playerId] = current.copy(connected = false)
                                }
                                rejoinDeadlineByPlayer[playerId] =
                                    kotlin.time.Clock.System.now().toEpochMilliseconds() +
                                        P2pKitRoomTransport.REJOIN_GRACE_MS
                                publishMembers()
                                true
                            } else {
                                false
                            }
                        }
                        if (removed) {
                            _peerEvents.emit(PeerEvent.PeerLeft(playerId, displayName))
                        }
                    }
                    ConnectionState.Idle,
                    ConnectionState.Connecting,
                    ConnectionState.Handshaking,
                    ConnectionState.Closing -> Unit
                }
            }
        }
    }

    private suspend fun handleAdmissionRequest(
        playerId: PlayerId,
        transportDisplayName: String,
        session: P2pSession,
        request: PeerMessage.AdmissionRequest,
    ) {
        if (request.protocol.major != PARLOR_PROTOCOL_MAJOR) {
            rejectSession(session, AdmissionRejection.IncompatibleProtocol)
            return
        }
        if (request.roomCode != roomCode) {
            rejectSession(session, AdmissionRejection.WrongCode)
            return
        }
        val displayName = transportDisplayName.trim()
        if (
            displayName.isEmpty() ||
            displayName.length > P2pKitRoomTransport.MAX_DISPLAY_NAME_LENGTH ||
            request.displayName.trim() != displayName
        ) {
            rejectSession(session, AdmissionRejection.InvalidRequest)
            return
        }

        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val validRejoin = stateMutex.withLock {
            val expectedToken = rejoinTokenByPlayer[playerId]
            val deadline = rejoinDeadlineByPlayer[playerId] ?: Long.MIN_VALUE
            request.rejoinToken != null &&
                request.rejoinToken == expectedToken &&
                now <= deadline
        }
        if (validRejoin) {
            admit(playerId, session, displayName, isRejoin = true)
            return
        }

        val requestEvent = stateMutex.withLock {
            if (admissionsClosed) {
                null
            } else {
                val existing = pendingByPlayer[playerId]
                val isKnownPlayer = playerId in previouslySeenPlayerIds
                if (existing?.session === session) {
                    PendingAdmission(playerId, displayName, isRejoin = isKnownPlayer) to false
                } else {
                    pendingByPlayer[playerId] = PendingConnection(
                        session = session,
                        displayName = displayName,
                        isRejoin = isKnownPlayer,
                    )
                    publishPendingAdmissions()
                    PendingAdmission(playerId, displayName, isRejoin = isKnownPlayer) to true
                }
            }
        }
        if (requestEvent == null) {
            rejectSession(session, AdmissionRejection.SessionStarted)
        } else if (requestEvent.second) {
            _peerEvents.emit(
                PeerEvent.AdmissionRequested(
                    playerId = requestEvent.first.playerId,
                    displayName = requestEvent.first.displayName,
                    isRejoin = requestEvent.first.isRejoin,
                ),
            )
        }
    }

    override suspend fun approveAdmission(playerId: PlayerId): Result<Unit, NetError> {
        val pending = stateMutex.withLock { pendingByPlayer[playerId] }
            ?: return Result.Failure(NetError.NotConnected)
        admit(playerId, pending.session, pending.displayName, pending.isRejoin)
        return Result.Success(Unit)
    }

    override suspend fun rejectAdmission(playerId: PlayerId): Result<Unit, NetError> {
        val pending = stateMutex.withLock {
            pendingByPlayer.remove(playerId).also { publishPendingAdmissions() }
        } ?: return Result.Failure(NetError.NotConnected)
        rejectSession(pending.session, AdmissionRejection.HostDeclined)
        return Result.Success(Unit)
    }

    override suspend fun closeAdmissions() {
        val pending = stateMutex.withLock {
            admissionsClosed = true
            pendingByPlayer.values.toList().also {
                pendingByPlayer.clear()
                publishPendingAdmissions()
            }
        }
        pending.forEach { rejectSession(it.session, AdmissionRejection.SessionStarted) }
    }

    private suspend fun admit(
        playerId: PlayerId,
        session: P2pSession,
        displayName: String,
        isRejoin: Boolean,
    ) {
        val (token, oldSession) = stateMutex.withLock {
            pendingByPlayer.remove(playerId)
            publishPendingAdmissions()
            val old = sessionsByPlayer[playerId]?.takeIf { it !== session }
            sessionsByPlayer[playerId] = session
            membersByPlayer[playerId] = RoomMember(playerId, displayName, connected = true)
            previouslySeenPlayerIds += playerId
            rejoinDeadlineByPlayer.remove(playerId)
            publishMembers()
            rejoinTokenByPlayer.getOrPut(playerId, SecureIds::rejoinToken256) to old
        }
        oldSession?.let { runCatching { it.close() } }
        sendRaw(session, HostMessage.AdmissionAccepted(playerId, token))
        if (isRejoin) {
            _peerEvents.emit(PeerEvent.PeerReconnected(playerId, displayName))
        } else {
            _peerEvents.emit(PeerEvent.PeerJoined(playerId, displayName))
        }
        markActiveIfRestored()
    }

    private suspend fun rejectSession(
        session: P2pSession,
        reason: AdmissionRejection,
    ) {
        runCatching { sendRaw(session, HostMessage.AdmissionRejected(reason)) }
        delay(P2pKitRoomTransport.ADMISSION_REJECTION_FLUSH_MS)
        runCatching { session.close() }
    }

    private suspend fun sendRaw(session: P2pSession, message: HostMessage) {
        val bytes = codec.encode(message)
        check(bytes.size <= P2pKitRoomTransport.MAX_ROOM_FRAME_BYTES)
        session.send(P2pMessage.Binary(bytes))
    }

    private fun publishMembers() {
        _members.value = membersByPlayer.values.toList()
    }

    private fun publishPendingAdmissions() {
        _pendingAdmissions.value = pendingByPlayer.map { (playerId, pending) ->
            PendingAdmission(playerId, pending.displayName, pending.isRejoin)
        }
    }

    override suspend fun appBackgrounded(atEpochMillis: Long) {
        val expiry = stateMutex.withLock {
            when (val current = _lifecycle.value) {
                RoomLifecycleState.Active -> {
                    val deadline = atEpochMillis + appResumeGraceMs
                    _lifecycle.value = RoomLifecycleState.Suspended(
                        deadline,
                    )
                    membersByPlayer.keys.toList().forEach { playerId ->
                        membersByPlayer[playerId] =
                            checkNotNull(membersByPlayer[playerId]).copy(connected = false)
                    }
                    publishMembers()
                    deadline to appResumeGraceMs
                }
                is RoomLifecycleState.Resuming -> {
                    _lifecycle.value = RoomLifecycleState.Suspended(
                        current.resumeDeadlineEpochMillis,
                    )
                    current.resumeDeadlineEpochMillis to
                        (current.resumeDeadlineEpochMillis - atEpochMillis).coerceAtLeast(0L)
                }
                is RoomLifecycleState.Suspended,
                RoomLifecycleState.Expired,
                RoomLifecycleState.Closed -> null
            }
        }
        if (expiry == null) return
        if (expiry.second == 0L) {
            expireLifecycle(expiry.first)
            return
        }
        scheduleLifecycleExpiry(expiry.first, expiry.second)
        kit.notifyAppBackgrounded()
        try {
            // P2pKit's notification starts cleanup asynchronously. Await the
            // host feature here so a rapid foreground cannot race an old stop.
            kit.stopAdvertising()
        } catch (failure: Throwable) {
            failure.rethrowIfCancellation()
            p2pLog("lifecycle: host stopAdvertising failed (${failure::class.simpleName})")
        }
    }

    override suspend fun appForegrounded(atEpochMillis: Long) {
        val deadline = stateMutex.withLock {
            when (val current = _lifecycle.value) {
                is RoomLifecycleState.Suspended -> current.resumeDeadlineEpochMillis
                is RoomLifecycleState.Resuming -> current.resumeDeadlineEpochMillis
                RoomLifecycleState.Active,
                RoomLifecycleState.Expired,
                RoomLifecycleState.Closed -> null
            }
        } ?: return
        if (atEpochMillis >= deadline) {
            expireLifecycle(deadline)
            return
        }
        stateMutex.withLock {
            _lifecycle.value = RoomLifecycleState.Resuming(deadline)
        }
        scheduleLifecycleExpiry(deadline, deadline - atEpochMillis)
        kit.notifyAppForegrounded()
        kit.startAdvertising()
        markActiveIfRestored()
    }

    private suspend fun markActiveIfRestored() {
        val expiryJob = stateMutex.withLock {
            if (
                _lifecycle.value is RoomLifecycleState.Resuming &&
                membersByPlayer.values.all(RoomMember::connected)
            ) {
                _lifecycle.value = RoomLifecycleState.Active
                lifecycleExpiryJob.also { lifecycleExpiryJob = null }
            } else {
                null
            }
        }
        expiryJob?.cancel()
    }

    private suspend fun scheduleLifecycleExpiry(deadline: Long, delayMs: Long) {
        stateMutex.withLock {
            lifecycleExpiryJob?.cancel()
            lifecycleExpiryJob = scope.launch {
                delay(delayMs.coerceAtLeast(0L))
                expireLifecycle(deadline)
            }
        }
    }

    private suspend fun expireLifecycle(deadline: Long) {
        val shouldLeave = stateMutex.withLock {
            val currentDeadline = when (val current = _lifecycle.value) {
                is RoomLifecycleState.Suspended -> current.resumeDeadlineEpochMillis
                is RoomLifecycleState.Resuming -> current.resumeDeadlineEpochMillis
                RoomLifecycleState.Active,
                RoomLifecycleState.Expired,
                RoomLifecycleState.Closed -> null
            }
            if (currentDeadline == deadline) {
                lifecycleExpiryJob = null
                _lifecycle.value = RoomLifecycleState.Expired
                true
            } else {
                false
            }
        }
        if (shouldLeave) leave()
    }

    /**
     * Removes [playerId] from the lobby in response to an explicit
     * [PeerMessage.LeaveNotice], and closes the underlying session so the
     * per-session collectors finish naturally. The state-watch job will
     * later see [ConnectionState.Closed] but will skip its own cleanup
     * because we already cleared `sessionsByPlayer[playerId]` here.
     *
     * Guards against a stale-session race: a returning peer (same
     * `PlayerId`) can land a new incoming session in `handleIncomingSession`
     * before this LeaveNotice handler runs — in which case the registered
     * session no longer `===` ours, and we must NOT remove the new member.
     */
    private suspend fun handleExplicitLeave(
        playerId: PlayerId,
        displayName: String,
        session: P2pSession,
    ) {
        val removed = stateMutex.withLock {
            if (sessionsByPlayer[playerId] !== session) {
                false
            } else {
                sessionsByPlayer.remove(playerId)
                membersByPlayer.remove(playerId)
                rejoinTokenByPlayer.remove(playerId)
                rejoinDeadlineByPlayer.remove(playerId)
                publishMembers()
                true
            }
        }
        if (!removed) {
            p2pLog("host: handleExplicitLeave SKIP (superseded session) peerId=${playerId.raw}")
            return
        }
        p2pLog("host: emitting PeerLeft (explicit LeaveNotice) peerId=${playerId.raw}")
        _peerEvents.tryEmit(PeerEvent.PeerLeft(playerId, displayName))
        // Best-effort cooperative close so the per-session collectors
        // complete. We deliberately don't await it (close is suspending);
        // launching keeps this helper synchronous for the collect lambda.
        runCatching { session.close() }
    }

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        val bytes = try {
            codec.encode(message)
        } catch (_: IllegalArgumentException) {
            return Result.Failure(NetError.PayloadTooLarge)
        }
        val payload = P2pMessage.Binary(bytes)
        return runCatching {
            when (target) {
                SendTarget.Broadcast -> {
                    // p2p-001: snapshot the session list under the lock
                    // (avoids ConcurrentModificationException racing
                    // handleIncomingSession / removals), then send off-lock.
                    val targets = stateMutex.withLock { sessionsByPlayer.values.toList() }
                    var delivered = 0
                    targets.forEach { session ->
                        if (session.state.value == ConnectionState.Connected) {
                            session.send(payload)
                            delivered++
                        }
                    }
                    // p2p-014: a broadcast that reached zero Connected peers is a
                    // delivery failure, not a silent success — otherwise a caller
                    // treats a snapshot that reached nobody as delivered and never
                    // resyncs. (A solo host with no peers legitimately gets this.)
                    if (delivered == 0) Result.Failure(NetError.NotConnected) else Result.Success(Unit)
                }
                is SendTarget.Direct -> {
                    val session = stateMutex.withLock { sessionsByPlayer[target.playerId] }
                        ?: return Result.Failure(NetError.NotConnected)
                    if (session.state.value != ConnectionState.Connected) {
                        return Result.Failure(NetError.NotConnected)
                    }
                    session.send(payload)
                    Result.Success(Unit)
                }
            }
        }.getOrElse {
            it.rethrowIfCancellation()
            Result.Failure(NetError.TransportFailure(it.message ?: "send failed"))
        }
    }

    /** A host cannot author a [PeerMessage]; calling this is a contract violation. */
    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun leave() {
        val (shouldLeave, expiryJob) = stateMutex.withLock {
            if (left) false to null else {
                left = true
                true to lifecycleExpiryJob.also { lifecycleExpiryJob = null }
            }
        }
        if (!shouldLeave) {
            p2pLog("host: leave() ignored (already left)")
            return
        }
        expiryJob?.cancel()
        p2pLog("host: leave() entry; closing ${sessionsByPlayer.size} sessions")
        // Stop advertising FIRST and give Bonjour a beat to actually push
        // the "service-removed" packet before the kit goes down. Skipping
        // this is the root cause of dead rooms lingering in remote join
        // lobbies for the entire Bonjour eviction window (5–30s on iOS).
        runCatching { kit.stopAdvertising() }
        delay(P2pKitRoomTransport.BONJOUR_GOODBYE_FLUSH_MS)
        acceptJob.cancelAndJoin()
        collectorJobs.forEach { it.cancelAndJoin() }
        collectorJobs.clear()
        val toClose = stateMutex.withLock {
            val sessions = sessionsByPlayer.values.toList() +
                pendingByPlayer.values.map(PendingConnection::session)
            sessionsByPlayer.clear()
            pendingByPlayer.clear()
            membersByPlayer.clear()
            previouslySeenPlayerIds.clear()
            rejoinTokenByPlayer.clear()
            rejoinDeadlineByPlayer.clear()
            publishMembers()
            publishPendingAdmissions()
            sessions
        }
        toClose.forEach { runCatching { it.close() } }
        // kit.stop() is terminal; guard it so a late/duplicate teardown can't
        // throw out of a disposal path. See PROBLEMS_PARLOR.md → p2p-016.
        runCatching { kit.stop() }
        if (_lifecycle.value != RoomLifecycleState.Expired) {
            _lifecycle.value = RoomLifecycleState.Closed
        }
        onClosed()
        p2pLog("host: leave() done")
    }
}

// ============================================================================ Peer room ==

internal class PeerP2pRoom(
    private val kit: P2pKit,
    private val session: P2pSession,
    hostPeer: Peer,
    private val roomCode: String,
    private val scope: CoroutineScope,
    private val codec: RoomMessageCodec,
    rejoinToken: String? = null,
    private val onClosed: () -> Unit = {},
    private val appResumeGraceMs: Long = P2pKitRoomTransport.APP_RESUME_GRACE_MS,
) : LocalRoom, AppLifecycleAwareRoom {

    override val selfPlayerId: PlayerId = PlayerId(kit.localPeerId.value)

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
    private val _peerEvents = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 64)
    private val _lifecycle = MutableStateFlow<RoomLifecycleState>(RoomLifecycleState.Active)

    override val info = _info.asStateFlow()
    override val members = _members.asStateFlow()
    override val isHost: Boolean = false
    override val peerEvents: SharedFlow<PeerEvent> = _peerEvents.asSharedFlow()
    override val lifecycle = _lifecycle.asStateFlow()

    private val incomingHostMessages = Channel<RoomMessage>(Channel.UNLIMITED)
    override val incoming: Flow<RoomMessage> = incomingHostMessages.receiveAsFlow()

    private val hostPlayerId: PlayerId = PlayerId(hostPeer.id.value)
    private val activeRejoinToken = MutableStateFlow(rejoinToken)
    override val rejoinToken: String?
        get() = activeRejoinToken.value
    private val rejoinResponses = Channel<Boolean>(Channel.CONFLATED)

    // p2p-016 (peer side): leave() runs from both a "Leave" tap and onDispose,
    // and kit.stop() is terminal — guard so the duplicate call is a no-op.
    private var left = false
    private val lifecycleMutex = Mutex()
    private var lifecycleExpiryJob: Job? = null

    override suspend fun appBackgrounded(atEpochMillis: Long) {
        val expiry = lifecycleMutex.withLock {
            when (val current = _lifecycle.value) {
                RoomLifecycleState.Active -> {
                    val deadline = atEpochMillis + appResumeGraceMs
                    _lifecycle.value = RoomLifecycleState.Suspended(
                        deadline,
                    )
                    deadline to appResumeGraceMs
                }
                is RoomLifecycleState.Resuming -> {
                    _lifecycle.value = RoomLifecycleState.Suspended(
                        current.resumeDeadlineEpochMillis,
                    )
                    current.resumeDeadlineEpochMillis to
                        (current.resumeDeadlineEpochMillis - atEpochMillis).coerceAtLeast(0L)
                }
                is RoomLifecycleState.Suspended,
                RoomLifecycleState.Expired,
                RoomLifecycleState.Closed -> null
            }
        }
        if (expiry != null) {
            if (expiry.second == 0L) {
                expireLifecycle(expiry.first)
                return
            }
            scheduleLifecycleExpiry(expiry.first, expiry.second)
            kit.notifyAppBackgrounded()
            markHostConnected(false)
            _info.value = _info.value.copy(status = RoomInfo.Status.Lost)
        }
    }

    override suspend fun appForegrounded(atEpochMillis: Long) {
        val deadline = lifecycleMutex.withLock {
            when (val current = _lifecycle.value) {
                is RoomLifecycleState.Suspended -> current.resumeDeadlineEpochMillis
                is RoomLifecycleState.Resuming -> current.resumeDeadlineEpochMillis
                RoomLifecycleState.Active,
                RoomLifecycleState.Expired,
                RoomLifecycleState.Closed -> null
            }
        } ?: return
        if (atEpochMillis >= deadline) {
            expireLifecycle(deadline)
            return
        }
        lifecycleMutex.withLock {
            _lifecycle.value = RoomLifecycleState.Resuming(deadline)
        }
        scheduleLifecycleExpiry(deadline, deadline - atEpochMillis)
        kit.notifyAppForegrounded()
        // BackgroundPolicy.CloseActiveSessions makes this P2pSession
        // terminal. P2P-02 replaces it through the protected rejoin flow;
        // until then the room remains Resuming and rejects new intents.
    }

    private suspend fun scheduleLifecycleExpiry(deadline: Long, delayMs: Long) {
        lifecycleMutex.withLock {
            lifecycleExpiryJob?.cancel()
            lifecycleExpiryJob = scope.launch {
                delay(delayMs.coerceAtLeast(0L))
                expireLifecycle(deadline)
            }
        }
    }

    private suspend fun expireLifecycle(deadline: Long) {
        val shouldLeave = lifecycleMutex.withLock {
            val currentDeadline = when (val current = _lifecycle.value) {
                is RoomLifecycleState.Suspended -> current.resumeDeadlineEpochMillis
                is RoomLifecycleState.Resuming -> current.resumeDeadlineEpochMillis
                RoomLifecycleState.Active,
                RoomLifecycleState.Expired,
                RoomLifecycleState.Closed -> null
            }
            if (currentDeadline == deadline) {
                lifecycleExpiryJob = null
                _lifecycle.value = RoomLifecycleState.Expired
                true
            } else {
                false
            }
        }
        if (shouldLeave) leave(sendNotice = false)
    }

    private val collectorJob: Job = scope.launch {
        session.incoming.collect { msg ->
            if (msg is P2pMessage.Binary) {
                if (msg.bytes.size > P2pKitRoomTransport.MAX_ROOM_FRAME_BYTES) {
                    p2pLog("peer: dropping oversized host frame")
                    return@collect
                }
                // p2p-002: a malformed host frame must not cancel this collector
                // (which would permanently sever the peer's inbound stream).
                val decoded = runCatching {
                    codec.decode(msg.bytes)
                }.getOrElse {
                    p2pLog("peer: dropping undecodable host frame (${it::class.simpleName})")
                    return@collect
                }
                when (decoded) {
                    is HostMessage.AdmissionAccepted -> {
                        activeRejoinToken.value = decoded.rejoinToken
                        rejoinResponses.trySend(true)
                    }
                    is HostMessage.AdmissionRejected -> {
                        rejoinResponses.trySend(false)
                    }
                    else -> incomingHostMessages.send(decoded)
                }
            }
        }
    }

    private val stateJob: Job = scope.launch {
        // The initial state emission for an already-Connected session must
        // not be reported as HostRestored; gate on whether we've previously
        // entered a lost state.
        var hostLost = false
        session.state.collect { state ->
            p2pLog("peer: session state -> $state (hostPid=${hostPlayerId.raw})")
            when (state) {
                ConnectionState.Reconnecting,
                ConnectionState.Failed -> {
                    if (!hostLost) {
                        hostLost = true
                        markHostConnected(false)
                        _info.value = _info.value.copy(status = RoomInfo.Status.Lost)
                        p2pLog("peer: emitting HostLost (state=$state)")
                        _peerEvents.tryEmit(PeerEvent.HostLost)
                    }
                }
                ConnectionState.Connected -> {
                    if (hostLost) {
                        if (!reestablishAdmission()) return@collect
                        hostLost = false
                        markHostConnected(true)
                        _info.value = _info.value.copy(status = RoomInfo.Status.Joined)
                        p2pLog("peer: emitting HostRestored")
                        _peerEvents.tryEmit(PeerEvent.HostRestored)
                    }
                }
                ConnectionState.Closed -> {
                    if (!hostLost) {
                        hostLost = true
                        markHostConnected(false)
                        _info.value = _info.value.copy(status = RoomInfo.Status.Lost)
                        p2pLog("peer: emitting HostLost (state=Closed)")
                        _peerEvents.tryEmit(PeerEvent.HostLost)
                    }
                }
                ConnectionState.Idle,
                ConnectionState.Connecting,
                ConnectionState.Handshaking,
                ConnectionState.Closing -> Unit
            }
        }
    }

    private fun markHostConnected(connected: Boolean) {
        _members.value = _members.value.map { member ->
            if (member.playerId == hostPlayerId) member.copy(connected = connected) else member
        }
    }

    /**
     * P2pKit restores transport encryption before Parlor's room admission is
     * restored. Re-send the opaque capability and wait for the host's answer so
     * game code cannot race a snapshot/command onto an unauthorised session.
     */
    private suspend fun reestablishAdmission(): Boolean {
        val token = activeRejoinToken.value ?: return true // legacy/test-only room
        while (rejoinResponses.tryReceive().isSuccess) Unit
        val request = PeerMessage.AdmissionRequest(
            protocol = ProtocolVersion(),
            actor = selfPlayerId,
            roomCode = roomCode,
            displayName = kit.localDeviceName,
            rejoinToken = token,
        )
        val bytes = codec.encode(request)
        val accepted = withTimeoutOrNull(REJOIN_ADMISSION_TIMEOUT_MS) {
            var decision: Boolean? = null
            while (decision == null) {
                try {
                    session.send(P2pMessage.Binary(bytes))
                } catch (t: Throwable) {
                    t.rethrowIfCancellation()
                }
                val received = rejoinResponses.tryReceive()
                if (received.isSuccess) {
                    decision = received.getOrThrow()
                } else {
                    delay(P2pKitRoomTransport.ADMISSION_RETRY_MS)
                }
            }
            checkNotNull(decision)
        } ?: false
        return accepted
    }

    /** A peer cannot author a [HostMessage]. */
    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        if (session.state.value != ConnectionState.Connected) {
            return Result.Failure(NetError.NotConnected)
        }
        val bytes = try {
            codec.encode(message)
        } catch (_: IllegalArgumentException) {
            return Result.Failure(NetError.PayloadTooLarge)
        }
        return runCatching {
            val payload = P2pMessage.Binary(bytes)
            session.send(payload)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = {
                it.rethrowIfCancellation()
                Result.Failure(NetError.TransportFailure(it.message ?: "send failed"))
            },
        )
    }

    override suspend fun leave() = leave(sendNotice = true)

    private suspend fun leave(sendNotice: Boolean) {
        val (shouldLeave, expiryJob) = lifecycleMutex.withLock {
            if (left) false to null else {
                left = true
                true to lifecycleExpiryJob.also { lifecycleExpiryJob = null }
            }
        }
        if (!shouldLeave) {
            p2pLog("peer: leave() ignored (already left)")
            return
        }
        expiryJob?.cancel()
        p2pLog("peer: leave() entry sessionState=${session.state.value}")
        // Best-effort: tell the host we're leaving so the lobby updates
        // immediately, instead of waiting for the TCP teardown to surface
        // (Closed/Failed). Guarded by Connected because send() is unsafe
        // otherwise; any send failure is non-fatal — the host's
        // state-watcher will still emit PeerLeft once the socket dies.
        if (sendNotice && session.state.value == ConnectionState.Connected) {
            runCatching {
                val notice = P2pMessage.Binary(
                    codec.encode(PeerMessage.LeaveNotice),
                )
                session.send(notice)
                p2pLog("peer: sent LeaveNotice to host")
                // Tiny window for the bytes to actually flush across the
                // wire before close() yanks the socket out from under
                // them. See P2pKitRoomTransport.LEAVE_NOTICE_FLUSH_MS.
                delay(P2pKitRoomTransport.LEAVE_NOTICE_FLUSH_MS)
            }
        }
        collectorJob.cancelAndJoin()
        stateJob.cancelAndJoin()
        rejoinResponses.close()
        runCatching { session.close() }
        // kit.stop() is terminal — guard against a duplicate/late teardown.
        runCatching { kit.stop() }
        if (_lifecycle.value != RoomLifecycleState.Expired) {
            _lifecycle.value = RoomLifecycleState.Closed
        }
        onClosed()
        p2pLog("peer: leave() done")
    }

    private companion object {
        const val REJOIN_ADMISSION_TIMEOUT_MS: Long = P2pKitRoomTransport.REJOIN_GRACE_MS
    }
}
