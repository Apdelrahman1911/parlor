package com.parlor.transport.p2p

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.EmptyResult
import com.parlor.core.result.Result
import com.parlor.networking.protocol.AdmissionRejection
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.MAX_COMMAND_PAYLOAD_BYTES
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.ResumableCredentialOffer
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
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.JoinConfig
import com.parlor.networking.transport.LocalNetworkAccess
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.networking.transport.TransportCapability
import com.parlor.networking.security.SecureHashes
import com.parlor.networking.security.SecureIds
import com.parlor.storage.secure.SecureStorage
import dev.p2pkit.core.AppId
import dev.p2pkit.core.ConnectionState
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pError
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.P2pSession
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerFingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

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
class P2pKitRoomTransport private constructor(
    private val appId: AppId,
    private val deviceName: String,
    private val scope: CoroutineScope,
    private val kitFactory: P2pKitFactory,
    secureStorage: SecureStorage,
    private val codec: RoomMessageCodec,
    private val diagnostics: P2pDiagnostics,
    @Suppress("UNUSED_PARAMETER") privateMarker: Unit,
    // Upper bound on how long [join] will wait for a matching, *fresh*
    // host advertisement before failing with [NetError.Timeout]. Kept
    // configurable so tests can fail quickly instead of waiting the full
    // production budget (10s). Production wiring uses the default.
    private val joinTimeoutMs: Long,
    // Maximum age of `kit.lastSeen(peerId)` for a peer with a populated
    // timestamp to be considered live. Older = treated as a stale Bonjour
    // leftover (a common iOS quirk after the host disappears without
    // flushing its goodbye) and ignored. Peers whose lastSeen is `null`
    // are accepted on the strength of the emission itself, because some
    // platforms — notably the current P2pKit Android adapter — do not
    // populate per-peer timestamps, and a strict null-rejects gate
    // blocks every Android-side join even when discovery is otherwise
    // working.
    private val peerFreshnessWindowMs: Long,
) : RoomTransport {
    /** Public constructor retained without exposing the internal diagnostics contract. */
    constructor(
        appId: AppId,
        deviceName: String,
        scope: CoroutineScope,
        kitFactory: P2pKitFactory,
        secureStorage: SecureStorage = UnavailableSecureStorage,
        codec: RoomMessageCodec = RoomMessageCodec(),
        joinTimeoutMs: Long = DEFAULT_JOIN_TIMEOUT_MS,
        peerFreshnessWindowMs: Long = DEFAULT_PEER_FRESHNESS_WINDOW_MS,
    ) : this(
        appId = appId,
        deviceName = deviceName,
        scope = scope,
        kitFactory = kitFactory,
        secureStorage = secureStorage,
        codec = codec,
        diagnostics = NoOpP2pDiagnostics,
        privateMarker = Unit,
        joinTimeoutMs = joinTimeoutMs,
        peerFreshnessWindowMs = peerFreshnessWindowMs,
    )

    /** Production/test wiring with a privacy-safe recorder. */
    internal constructor(
        appId: AppId,
        deviceName: String,
        scope: CoroutineScope,
        kitFactory: P2pKitFactory,
        diagnostics: P2pDiagnostics,
        secureStorage: SecureStorage = UnavailableSecureStorage,
        codec: RoomMessageCodec = RoomMessageCodec(),
        joinTimeoutMs: Long = DEFAULT_JOIN_TIMEOUT_MS,
        peerFreshnessWindowMs: Long = DEFAULT_PEER_FRESHNESS_WINDOW_MS,
    ) : this(
        appId = appId,
        deviceName = deviceName,
        scope = scope,
        kitFactory = kitFactory,
        secureStorage = secureStorage,
        codec = codec,
        diagnostics = diagnostics,
        privateMarker = Unit,
        joinTimeoutMs = joinTimeoutMs,
        peerFreshnessWindowMs = peerFreshnessWindowMs,
    )

    private val credentialStore = ResumableCredentialStore(secureStorage)
    private val _localNetworkAccess =
        MutableStateFlow<LocalNetworkAccess>(LocalNetworkAccess.Unknown)
    override val localNetworkAccess = _localNetworkAccess.asStateFlow()

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
                    diagnostics.event(
                        P2pDiagnosticEventName.CLEANUP_FAILED,
                        reason = P2pDiagnosticReason.LIFECYCLE,
                    )
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

    private fun recordLocalNetworkFailure(failure: Throwable) {
        // An authenticated connection or successful advertisement already
        // proved the LAN path. A later application/protocol failure must not
        // relabel that evidence as a permission or network problem.
        if (_localNetworkAccess.value == LocalNetworkAccess.Operational) return
        val permissionWasProven = generateSequence(failure as Throwable?) { it.cause }
            .any { it is P2pError.PermissionMissing }
        _localNetworkAccess.value = if (permissionWasProven) {
            LocalNetworkAccess.PermissionDenied
        } else {
            LocalNetworkAccess.FailureUnclassified
        }
    }

    override val capability: TransportCapability = TransportCapability(
        // The UI currently uses explicit room-code entry. Do not claim the
        // RoomTransport discovery contract until discoverRooms() is mapped.
        supportsDiscovery = false,
        latencyHintMs = 25,
        maxPayloadBytes = MAX_ROOM_FRAME_BYTES,
        // Room-code entry still discovers a generic LAN advertisement. Parlor
        // does not configure P2pKit's platform provisioning sidecars or expose
        // a host:port + authenticated-fingerprint input contract.
        supportsManualEndpointConnection = false,
    )

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
        _localNetworkAccess.value = LocalNetworkAccess.Attempting
        diagnostics.event(P2pDiagnosticEventName.SESSION_CREATE_STARTED, P2pDiagnosticRole.HOST)
        return runCatching {
            val roomCode = generateRoomCode()
            // The low-entropy admission code never leaves the encrypted
            // channel. Discovery advertises only a generic Parlor room.
            val advertisedDeviceName = "$P2P_ROOM_PREFIX$deviceName"
            val lifecycleRegistrationId = SecureIds.id128()
            val kit = kitFactory.createKit(
                appId = appId,
                deviceName = advertisedDeviceName,
            )
            // p2p-005: if start()/startAdvertising() throws, stop the kit before
            // propagating — otherwise the started instance (sockets, JmDNS/NSD
            // registration, kit scope) leaks for the process lifetime. join()
            // already does this; host() didn't.
            var room: HostP2pRoom? = null
            try {
                kit.start()
                checkNotNull(kit.localFingerprint) {
                    "P2pKit authenticated identity is unavailable"
                }
                // Construct the room first so its incomingSessions collector is
                // subscribed before the service becomes discoverable. Otherwise a
                // fast peer can connect into P2pKit's replay-zero session flow.
                room = HostP2pRoom(
                    kit = kit,
                    roomCode = roomCode,
                    roomDisplayName = config.roomDisplayName,
                    hostPlayerId = PlayerId(kit.localPeerId.value),
                    maxRemotePlayers = config.maxRemotePlayers,
                    gameProtocol = config.gameProtocol,
                    scope = scope,
                    codec = codec,
                    diagnostics = diagnostics,
                    onClosed = { roomClosed(lifecycleRegistrationId) },
                )
                kit.startAdvertising()
                _localNetworkAccess.value = LocalNetworkAccess.Operational
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    if (room == null) {
                        kit.stopAfterFailure(diagnostics)
                    } else {
                        room.leave()
                    }
                }
                t.rethrowIfCancellation()
                throw t
            }
            checkNotNull(room).also {
                registerLifecycleRoom(lifecycleRegistrationId, it)
            }
        }.fold(
            onSuccess = {
                diagnostics.event(
                    P2pDiagnosticEventName.SESSION_CREATE_SUCCEEDED,
                    P2pDiagnosticRole.HOST,
                    P2pDiagnosticResult.SUCCESS,
                )
                Result.Success(it)
            },
            onFailure = {
                it.rethrowIfCancellation()
                recordLocalNetworkFailure(it)
                diagnostics.event(
                    P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                    P2pDiagnosticRole.HOST,
                    P2pDiagnosticResult.FAILURE,
                    it.toDiagnosticReason(),
                )
                Result.Failure(NetError.TransportFailure(it.message ?: "host failed"))
            },
        )
    }

    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> {
        return join(JoinConfig(code = code, displayName = displayName))
    }

    override suspend fun join(config: JoinConfig): Result<LocalRoom, NetError> {
        if (config.rejoinToken != null) {
            return Result.Failure(NetError.Unauthorized)
        }
        _localNetworkAccess.value = LocalNetworkAccess.Attempting
        diagnostics.event(P2pDiagnosticEventName.SESSION_CREATE_STARTED, P2pDiagnosticRole.PEER)
        val kit = try {
            kitFactory.createKit(appId = appId, deviceName = config.displayName)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
            recordLocalNetworkFailure(t)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                t.toDiagnosticReason(),
            )
            return Result.Failure(NetError.TransportFailure(t.message ?: "kit initialization failed"))
        }
        try {
            kit.also {
                it.start()
                it.startDiscovery()
                diagnostics.event(P2pDiagnosticEventName.DISCOVERY_STARTED, P2pDiagnosticRole.PEER)
            }
        } catch (t: Throwable) {
            kit.stopAfterFailure(diagnostics)
            t.rethrowIfCancellation()
            recordLocalNetworkFailure(t)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                t.toDiagnosticReason(),
            )
            return Result.Failure(NetError.TransportFailure(t.message ?: "join failed"))
        }
        return try {
            val startedAt = TimeSource.Monotonic.markNow()
            val scheduler = DiscoveryCandidateScheduler(totalDeadlineMs = joinTimeoutMs)
            var result: Result<LocalRoom, NetError>? = null
            var lastVisibleCount: Int? = null
            while (result == null && startedAt.elapsedNow().inWholeMilliseconds < joinTimeoutMs) {
                val elapsedMs = startedAt.elapsedNow().inWholeMilliseconds
                val visiblePeers = kit.peers.value.filter { it.isFreshParlorHost(kit) }
                if (lastVisibleCount != visiblePeers.size) {
                    lastVisibleCount = visiblePeers.size
                    diagnostics.event(
                        P2pDiagnosticEventName.DISCOVERY_CANDIDATES,
                        P2pDiagnosticRole.PEER,
                        count = diagnosticCount(visiblePeers.size),
                    )
                }
                scheduler.update(
                    visiblePeers.map { peer ->
                        DiscoveryCandidate(peer.id.value, peer.endpointVersion())
                    },
                    nowMs = elapsedMs,
                )
                val candidate = scheduler.next(elapsedMs)
                if (candidate == null) {
                    val remainingMs = joinTimeoutMs - elapsedMs
                    val wakeDelayMs = minOf(
                        scheduler.nextWakeDelayMs(elapsedMs),
                        DISCOVERY_REFRESH_POLL_MS,
                        remainingMs,
                    ).coerceAtLeast(1L)
                    val snapshot = kit.peers.value
                    withTimeoutOrNull(wakeDelayMs) {
                        kit.peers.first { it != snapshot }
                    }
                    continue
                }
                val hostPeer = visiblePeers.firstOrNull {
                    it.id.value == candidate.key && it.endpointVersion() == candidate.endpointVersion
                }
                if (hostPeer == null) {
                    scheduler.recordResult(
                        candidate,
                        DiscoveryAttemptResult.TransientFailure,
                        elapsedMs,
                    )
                    continue
                }
                val remainingDialBudgetMs = joinTimeoutMs -
                    startedAt.elapsedNow().inWholeMilliseconds
                if (remainingDialBudgetMs <= 0L) break
                diagnostics.event(
                    P2pDiagnosticEventName.DISCOVERY_ATTEMPTED,
                    P2pDiagnosticRole.PEER,
                )
                val session = try {
                    withTimeoutOrNull(minOf(DIAL_AND_HANDSHAKE_TIMEOUT_MS, remainingDialBudgetMs)) {
                        kit.connect(hostPeer)
                    }
                } catch (failure: Throwable) {
                    failure.rethrowIfCancellation()
                    null
                }
                if (session == null) {
                    diagnostics.event(
                        P2pDiagnosticEventName.DISCOVERY_ATTEMPTED,
                        P2pDiagnosticRole.PEER,
                        P2pDiagnosticResult.FAILURE,
                        P2pDiagnosticReason.TRANSPORT,
                    )
                    scheduler.recordResult(
                        candidate,
                        DiscoveryAttemptResult.TransientFailure,
                        startedAt.elapsedNow().inWholeMilliseconds,
                    )
                    continue
                }
                // A completed authenticated P2pKit connection is stronger
                // evidence than any permission preflight Apple makes public.
                _localNetworkAccess.value = LocalNetworkAccess.Operational
                diagnostics.event(
                    P2pDiagnosticEventName.CONNECTION_SECURE,
                    P2pDiagnosticRole.PEER,
                    P2pDiagnosticResult.SUCCESS,
                )
                val remainingFirstResponseBudgetMs = joinTimeoutMs -
                    startedAt.elapsedNow().inWholeMilliseconds
                if (remainingFirstResponseBudgetMs <= 0L) {
                    runCatching { session.close() }
                    break
                }
                when (
                    val admission = awaitAdmission(
                        session = session,
                        hostPeer = hostPeer,
                        code = config.code,
                        displayName = config.displayName,
                        selfPlayerId = PlayerId(kit.localPeerId.value),
                        firstResponseTimeoutMs = minOf(
                            FIRST_ADMISSION_RESPONSE_TIMEOUT_MS,
                            remainingFirstResponseBudgetMs,
                        ),
                    )
                ) {
                    is AdmissionOutcome.Accepted -> {
                        kit.stopDiscovery()
                        val lifecycleRegistrationId = SecureIds.id128()
                        val peerRoom = PeerP2pRoom(
                            kit = kit,
                            session = session,
                            hostPeer = hostPeer,
                            roomCode = config.code,
                            initialCredential = admission.credential,
                            credentialStore = credentialStore,
                            resumeConnector = { credential ->
                                resumeConnection(kit, credential)
                            },
                            scope = scope,
                            codec = codec,
                            diagnostics = diagnostics,
                            onClosed = { roomClosed(lifecycleRegistrationId) },
                        )
                        if (!peerRoom.finishInitialAdmissionHandoff(admission.credential)) {
                            peerRoom.abandonFailedResume()
                            result = Result.Failure(
                                NetError.TransportFailure("admission handoff failed"),
                            )
                        } else {
                            registerLifecycleRoom(lifecycleRegistrationId, peerRoom)
                            result = Result.Success(peerRoom)
                        }
                    }
                    is AdmissionOutcome.Rejected -> {
                        runCatching { session.close() }
                        when (admission.reason) {
                            AdmissionRejection.WrongCode -> scheduler.recordResult(
                                candidate,
                                DiscoveryAttemptResult.WrongRoom,
                                startedAt.elapsedNow().inWholeMilliseconds,
                            )
                            AdmissionRejection.IncompatibleProtocol -> scheduler.recordResult(
                                candidate,
                                DiscoveryAttemptResult.IncompatibleProtocol,
                                startedAt.elapsedNow().inWholeMilliseconds,
                            )
                            else -> result = Result.Failure(admission.reason.toNetError())
                        }
                    }
                    is AdmissionOutcome.TransientFailure -> {
                        runCatching { session.close() }
                        scheduler.recordResult(
                            candidate,
                            DiscoveryAttemptResult.TransientFailure,
                            startedAt.elapsedNow().inWholeMilliseconds,
                        )
                    }
                    is AdmissionOutcome.Failed -> {
                        runCatching { session.close() }
                        result = Result.Failure(admission.error)
                    }
                }
            }
            if (result == null) {
                result = Result.Failure(scheduler.finalError().toNetError())
            }
            if (result is Result.Failure) {
                runCatching { kit.stopDiscovery() }
                kit.stopAfterFailure(diagnostics)
                if (_localNetworkAccess.value != LocalNetworkAccess.Operational) {
                    _localNetworkAccess.value = LocalNetworkAccess.FailureUnclassified
                }
            }
            checkNotNull(result).also { completed ->
                when (completed) {
                    is Result.Success -> {
                        diagnostics.event(
                            P2pDiagnosticEventName.DISCOVERY_FINISHED,
                            P2pDiagnosticRole.PEER,
                            P2pDiagnosticResult.SUCCESS,
                        )
                        diagnostics.event(
                            P2pDiagnosticEventName.SESSION_CREATE_SUCCEEDED,
                            P2pDiagnosticRole.PEER,
                            P2pDiagnosticResult.SUCCESS,
                        )
                    }
                    is Result.Failure -> {
                        diagnostics.event(
                            P2pDiagnosticEventName.DISCOVERY_FINISHED,
                            P2pDiagnosticRole.PEER,
                            completed.error.toDiagnosticResult(),
                            completed.error.toDiagnosticReason(),
                        )
                        diagnostics.event(
                            P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                            P2pDiagnosticRole.PEER,
                            completed.error.toDiagnosticResult(),
                            completed.error.toDiagnosticReason(),
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            kit.stopAfterFailure(diagnostics)
            t.rethrowIfCancellation()
            recordLocalNetworkFailure(t)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                t.toDiagnosticReason(),
            )
            Result.Failure(NetError.TransportFailure(t.message ?: "join failed"))
        }
    }

    override suspend fun resumableSession(): Result<ResumableSessionInfo?, NetError> =
        when (val loaded = credentialStore.loadResumeCandidate()) {
            is Result.Failure -> Result.Failure(NetError.SecureStorageUnavailable)
            is Result.Success -> {
                val credential = loaded.data ?: return Result.Success(null)
                if (credential.expiresAtEpochMillis <= nowMillis()) {
                    credentialStore.clear()
                    Result.Success(null)
                } else {
                    val gameId = credential.gameId?.let(::GameId)
                        ?: return Result.Failure(NetError.IncompatibleProtocol)
                    val gameVersion = credential.gameVersion
                        ?: return Result.Failure(NetError.IncompatibleProtocol)
                    Result.Success(
                        ResumableSessionInfo(
                            gameId = gameId,
                            gameVersion = gameVersion,
                            displayName = credential.displayName,
                            expiresAtEpochMillis = credential.expiresAtEpochMillis,
                        ),
                    )
                }
            }
        }

    override suspend fun resumeLastSession(): Result<LocalRoom, NetError> {
        val credential = when (val loaded = credentialStore.loadResumeCandidate()) {
            is Result.Failure -> return Result.Failure(NetError.SecureStorageUnavailable)
            is Result.Success -> loaded.data ?: return Result.Failure(NetError.NotConnected)
        }
        if (credential.expiresAtEpochMillis <= nowMillis()) {
            credentialStore.clear()
            diagnostics.event(
                P2pDiagnosticEventName.LIFECYCLE_EXPIRED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.REJECTED,
                P2pDiagnosticReason.LIFECYCLE,
            )
            return Result.Failure(NetError.RejoinExpired)
        }
        diagnostics.event(
            P2pDiagnosticEventName.LIFECYCLE_RESUME_STARTED,
            P2pDiagnosticRole.PEER,
        )
        _localNetworkAccess.value = LocalNetworkAccess.Attempting
        val kit = try {
            kitFactory.createKit(appId = appId, deviceName = credential.displayName)
        } catch (failure: Throwable) {
            failure.rethrowIfCancellation()
            recordLocalNetworkFailure(failure)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                failure.toDiagnosticReason(),
            )
            return Result.Failure(
                NetError.TransportFailure(failure.message ?: "resume initialization failed"),
            )
        }
        try {
            kit.start()
        } catch (failure: Throwable) {
            kit.stopAfterFailure(diagnostics)
            failure.rethrowIfCancellation()
            recordLocalNetworkFailure(failure)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                failure.toDiagnosticReason(),
            )
            return Result.Failure(
                NetError.TransportFailure(failure.message ?: "resume initialization failed"),
            )
        }
        return try {
            when (val resumed = resumeConnection(kit, credential)) {
                is Result.Failure -> {
                    kit.stopAfterFailure(diagnostics)
                    if (_localNetworkAccess.value != LocalNetworkAccess.Operational) {
                        _localNetworkAccess.value = LocalNetworkAccess.FailureUnclassified
                    }
                    diagnostics.event(
                        if (resumed.error == NetError.RejoinExpired) {
                            P2pDiagnosticEventName.LIFECYCLE_EXPIRED
                        } else {
                            P2pDiagnosticEventName.SESSION_CREATE_FAILED
                        },
                        P2pDiagnosticRole.PEER,
                        resumed.error.toDiagnosticResult(),
                        resumed.error.toDiagnosticReason(),
                    )
                    resumed
                }
                is Result.Success -> {
                    val lifecycleRegistrationId = SecureIds.id128()
                    val room = PeerP2pRoom(
                        kit = kit,
                        session = resumed.data.session,
                        hostPeer = resumed.data.hostPeer,
                        roomCode = resumed.data.credential.roomCode,
                        scope = scope,
                        codec = codec,
                        diagnostics = diagnostics,
                        initialCredential = resumed.data.credential,
                        credentialStore = credentialStore,
                        resumeConnector = { next -> resumeConnection(kit, next) },
                        onClosed = { roomClosed(lifecycleRegistrationId) },
                    )
                    if (!room.finishInitialResumeHandoff(resumed.data)) {
                        room.abandonFailedResume()
                        Result.Failure(NetError.TransportFailure("resume handoff failed"))
                    } else {
                        registerLifecycleRoom(lifecycleRegistrationId, room)
                        diagnostics.event(
                            P2pDiagnosticEventName.LIFECYCLE_RESUMED,
                            P2pDiagnosticRole.PEER,
                            P2pDiagnosticResult.SUCCESS,
                        )
                        Result.Success(room)
                    }
                }
            }
        } catch (failure: Throwable) {
            kit.stopAfterFailure(diagnostics)
            failure.rethrowIfCancellation()
            recordLocalNetworkFailure(failure)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                failure.toDiagnosticReason(),
            )
            Result.Failure(NetError.TransportFailure(failure.message ?: "resume failed"))
        }
    }

    private sealed interface AdmissionOutcome {
        data class Accepted(val credential: ResumableSessionCredential) : AdmissionOutcome
        data class Rejected(val reason: AdmissionRejection) : AdmissionOutcome
        data class TransientFailure(val error: NetError) : AdmissionOutcome
        data class Failed(val error: NetError) : AdmissionOutcome
    }

    private suspend fun awaitAdmission(
        session: P2pSession,
        hostPeer: Peer,
        code: String,
        displayName: String,
        selfPlayerId: PlayerId,
        firstResponseTimeoutMs: Long,
    ): AdmissionOutcome = coroutineScope {
        val responses = Channel<HostMessage>(capacity = 8)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.incoming
                .filterIsInstance<P2pMessage.Binary>()
                .collect { frame ->
                    if (frame.bytes.size > MAX_ROOM_FRAME_BYTES) return@collect
                    val decoded = runCatching {
                        codec.decode(frame.bytes)
                    }.getOrNull() as? HostMessage ?: return@collect
                    when (decoded) {
                        is HostMessage.AdmissionOffered,
                        is HostMessage.AdmissionPending,
                        is HostMessage.AdmissionCommitted,
                        is HostMessage.AdmissionRejected,
                        is HostMessage.AdmissionAccepted -> responses.send(decoded)
                        else -> Unit
                    }
                }
        }
        try {
            val first = try {
                sendUntilResponse(
                    session = session,
                    responses = responses,
                    message = PeerMessage.AdmissionRequest(
                        protocol = ProtocolVersion(),
                        actor = selfPlayerId,
                        roomCode = code,
                        displayName = displayName,
                    ),
                    timeoutMs = firstResponseTimeoutMs,
                )
            } catch (_: TimeoutCancellationException) {
                return@coroutineScope AdmissionOutcome.TransientFailure(NetError.Timeout)
            }
            val decision = if (first is HostMessage.AdmissionPending) {
                if (first.playerId != selfPlayerId) {
                    return@coroutineScope AdmissionOutcome.Rejected(
                        AdmissionRejection.InvalidCredential,
                    )
                }
                try {
                    withTimeout<HostMessage>(HOST_APPROVAL_TIMEOUT_MS) {
                        var next: HostMessage? = null
                        while (next == null) {
                            val received = responses.receive()
                            next = if (
                                received is HostMessage.AdmissionPending &&
                                received.playerId == selfPlayerId
                            ) {
                                null
                            } else {
                                received
                            }
                        }
                        next
                    }
                } catch (_: TimeoutCancellationException) {
                    return@coroutineScope AdmissionOutcome.Failed(NetError.Timeout)
                }
            } else {
                first
            }
            when (decision) {
                is HostMessage.AdmissionRejected -> AdmissionOutcome.Rejected(decision.reason)
                is HostMessage.AdmissionAccepted ->
                    AdmissionOutcome.Rejected(AdmissionRejection.IncompatibleProtocol)
                is HostMessage.AdmissionOffered -> {
                    val credential = decision.offer.toStoredCredentialOrNull(
                        session = session,
                        hostPeer = hostPeer,
                        roomCode = code,
                        displayName = displayName,
                        selfPlayerId = selfPlayerId,
                    ) ?: return@coroutineScope AdmissionOutcome.Rejected(
                        AdmissionRejection.InvalidCredential,
                    )
                    when (credentialStore.stage(credential)) {
                        is Result.Failure ->
                            return@coroutineScope AdmissionOutcome.Failed(
                                NetError.SecureStorageUnavailable,
                            )
                        is Result.Success -> Unit
                    }
                    val committed = sendUntilResponse(
                        session = session,
                        responses = responses,
                        message = PeerMessage.AdmissionConfirmed(
                            actor = selfPlayerId,
                            offerId = credential.offerId,
                            generation = credential.generation,
                        ),
                        timeoutMs = DIAL_AND_HANDSHAKE_TIMEOUT_MS,
                    )
                    when (committed) {
                        is HostMessage.AdmissionRejected -> {
                            credentialStore.discardPending(credential.offerId)
                            AdmissionOutcome.Rejected(committed.reason)
                        }
                        is HostMessage.AdmissionCommitted -> {
                            if (
                                committed.playerId != selfPlayerId ||
                                committed.offerId != credential.offerId ||
                                committed.generation != credential.generation
                            ) {
                                credentialStore.discardPending(credential.offerId)
                                AdmissionOutcome.Rejected(AdmissionRejection.InvalidCredential)
                            } else if (
                                credentialStore.commit(
                                    credential.offerId,
                                    credential.generation,
                                ) is Result.Failure
                            ) {
                                AdmissionOutcome.Failed(NetError.SecureStorageUnavailable)
                            } else {
                                AdmissionOutcome.Accepted(credential)
                            }
                        }
                        else -> AdmissionOutcome.Rejected(AdmissionRejection.InvalidCredential)
                    }
                }
                else -> AdmissionOutcome.Rejected(AdmissionRejection.InvalidCredential)
            }
        } finally {
            collector.cancelAndJoin()
            responses.close()
        }
    }

    private suspend fun sendUntilResponse(
        session: P2pSession,
        responses: Channel<HostMessage>,
        message: PeerMessage,
        timeoutMs: Long,
    ): HostMessage = withTimeout<HostMessage>(timeoutMs) {
        var response: HostMessage? = null
        while (response == null) {
            sendRoomMessage(session, message)
            response = withTimeoutOrNull(ADMISSION_RETRY_MS) { responses.receive() }
        }
        response
    }

    private suspend fun sendRoomMessage(session: P2pSession, message: PeerMessage) {
        val bytes = codec.encode(message)
        check(bytes.size <= MAX_ROOM_FRAME_BYTES)
        session.send(P2pMessage.Binary(bytes))
    }

    /**
     * Replaces a terminal physical connection without changing logical player
     * identity. Discovery is used only to locate the recorded host peer id;
     * the connection is cryptographically pinned to the fingerprint captured
     * during initial admission.
     */
    private suspend fun resumeConnection(
        kit: P2pKit,
        credential: ResumableSessionCredential,
    ): Result<ResumedPeerConnection, NetError> {
        if (credential.expiresAtEpochMillis <= nowMillis()) {
            return Result.Failure(NetError.RejoinExpired)
        }
        val expectedFingerprint = runCatching {
            PeerFingerprint(credential.hostFingerprint)
        }.getOrElse {
            return Result.Failure(NetError.Unauthorized)
        }
        return try {
            kit.startDiscovery()
            diagnostics.event(P2pDiagnosticEventName.DISCOVERY_STARTED, P2pDiagnosticRole.PEER)
            withTimeout(REJOIN_GRACE_MS) {
                while (true) {
                    val hostPeer = kit.peers.first { peers ->
                        peers.any { peer ->
                            peer.id.value == credential.hostPeerId &&
                                peer.isFreshParlorHost(kit)
                        }
                    }.first { peer ->
                        peer.id.value == credential.hostPeerId &&
                            peer.isFreshParlorHost(kit)
                    }
                    val session = try {
                        diagnostics.event(
                            P2pDiagnosticEventName.DISCOVERY_ATTEMPTED,
                            P2pDiagnosticRole.PEER,
                        )
                        kit.connect(hostPeer, expectedFingerprint)
                    } catch (failure: Throwable) {
                        failure.rethrowIfCancellation()
                        delay(ADMISSION_RETRY_MS)
                        continue
                    }
                    _localNetworkAccess.value = LocalNetworkAccess.Operational
                    val identityMatches =
                        session.peer.id == hostPeer.id &&
                            session.peerIdentity.peerId == hostPeer.id &&
                            session.peerIdentity.fingerprint == expectedFingerprint
                    if (!identityMatches) {
                        runCatching { session.close() }
                        return@withTimeout Result.Failure(NetError.Unauthorized)
                    }
                    diagnostics.event(
                        P2pDiagnosticEventName.CONNECTION_SECURE,
                        P2pDiagnosticRole.PEER,
                        P2pDiagnosticResult.SUCCESS,
                    )
                    when (val resumed = awaitResume(session, hostPeer, credential)) {
                        is Result.Success -> return@withTimeout resumed
                        is Result.Failure -> {
                            runCatching { session.close() }
                            if (
                                resumed.error == NetError.Unauthorized ||
                                resumed.error == NetError.RejoinExpired ||
                                resumed.error == NetError.IncompatibleProtocol ||
                                resumed.error == NetError.AlreadyConnected
                            ) {
                                return@withTimeout resumed
                            }
                            delay(ADMISSION_RETRY_MS)
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Result.Failure(NetError.Timeout)
            }
        } catch (_: TimeoutCancellationException) {
            Result.Failure(NetError.Timeout)
        } catch (failure: Throwable) {
            failure.rethrowIfCancellation()
            Result.Failure(NetError.TransportFailure(failure.message ?: "resume failed"))
        } finally {
            runCatching { kit.stopDiscovery() }
        }
    }

    private suspend fun awaitResume(
        session: P2pSession,
        hostPeer: Peer,
        credential: ResumableSessionCredential,
    ): Result<ResumedPeerConnection, NetError> = coroutineScope {
        val responses = Channel<HostMessage>(capacity = 8)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.incoming.filterIsInstance<P2pMessage.Binary>().collect { frame ->
                if (frame.bytes.size > MAX_ROOM_FRAME_BYTES) return@collect
                val decoded = runCatching { codec.decode(frame.bytes) }.getOrNull()
                    as? HostMessage ?: return@collect
                when (decoded) {
                    is HostMessage.ResumeOffered,
                    is HostMessage.ResumeCommitted,
                    is HostMessage.AdmissionRejected -> responses.send(decoded)
                    else -> Unit
                }
            }
        }
        var stagedOfferId: String? = null
        var committedCredential = false
        try {
            val first = sendUntilResponse(
                session = session,
                responses = responses,
                message = PeerMessage.ResumeRequested(
                    protocol = ProtocolVersion(),
                    actor = PlayerId(credential.playerId),
                    roomCode = credential.roomCode,
                    displayName = credential.displayName,
                    secret = credential.secret,
                    generation = credential.generation,
                ),
                timeoutMs = FIRST_ADMISSION_RESPONSE_TIMEOUT_MS,
            )
            if (first is HostMessage.AdmissionRejected) {
                return@coroutineScope Result.Failure(first.reason.toNetError())
            }
            val offer = (first as? HostMessage.ResumeOffered)?.offer
                ?: return@coroutineScope Result.Failure(NetError.Unauthorized)
            val rotated = offer.toRotatedCredentialOrNull(session, hostPeer, credential)
                ?: return@coroutineScope Result.Failure(NetError.Unauthorized)
            stagedOfferId = rotated.offerId
            if (credentialStore.stage(rotated) is Result.Failure) {
                return@coroutineScope Result.Failure(NetError.SecureStorageUnavailable)
            }
            when (
                val committed = sendUntilResponse(
                    session = session,
                    responses = responses,
                    message = PeerMessage.ResumeConfirmed(
                        actor = PlayerId(rotated.playerId),
                        offerId = rotated.offerId,
                        generation = rotated.generation,
                    ),
                    timeoutMs = ADMISSION_CONFIRM_TIMEOUT_MS,
                )
            ) {
                is HostMessage.AdmissionRejected -> {
                    credentialStore.discardPending(rotated.offerId)
                    Result.Failure(committed.reason.toNetError())
                }
                is HostMessage.ResumeCommitted -> {
                    if (
                        committed.playerId.raw != rotated.playerId ||
                        committed.offerId != rotated.offerId ||
                        committed.generation != rotated.generation
                    ) {
                        credentialStore.discardPending(rotated.offerId)
                        Result.Failure(NetError.Unauthorized)
                    } else if (
                        credentialStore.commit(rotated.offerId, rotated.generation) is Result.Failure
                    ) {
                        Result.Failure(NetError.SecureStorageUnavailable)
                    } else {
                        committedCredential = true
                        Result.Success(ResumedPeerConnection(session, hostPeer, rotated))
                    }
                }
                else -> Result.Failure(NetError.Unauthorized)
            }
        } finally {
            collector.cancelAndJoin()
            responses.close()
            val pending = stagedOfferId
            if (pending != null && !committedCredential) {
                // On an interrupted handshake retain the last committed
                // generation and remove only this staged rotation.
                credentialStore.discardPending(pending)
            }
        }
    }

    private fun ResumableCredentialOffer.toStoredCredentialOrNull(
        session: P2pSession,
        hostPeer: Peer,
        roomCode: String,
        displayName: String,
        selfPlayerId: PlayerId,
    ): ResumableSessionCredential? {
        val authenticatedFingerprint = session.peerIdentity.fingerprint?.value ?: return null
        val gameFieldsMatch = (gameId == null) == (gameVersion == null)
        if (
            playerId != selfPlayerId ||
            hostPeer.id != session.peer.id ||
            hostPeer.id.value != hostPeerId ||
            authenticatedFingerprint != hostFingerprint ||
            generation != INITIAL_CREDENTIAL_GENERATION ||
            !gameFieldsMatch
        ) {
            return null
        }
        return runCatching {
            ResumableSessionCredential(
                offerId = offerId,
                roomCode = roomCode,
                displayName = displayName,
                playerId = selfPlayerId.raw,
                hostPeerId = hostPeerId,
                hostFingerprint = hostFingerprint,
                secret = secret,
                generation = generation,
                issuedAtEpochMillis = issuedAtEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
                gameId = gameId,
                gameVersion = gameVersion,
            ).requireValid()
        }.getOrNull()
    }

    private fun ResumableCredentialOffer.toRotatedCredentialOrNull(
        session: P2pSession,
        hostPeer: Peer,
        current: ResumableSessionCredential,
    ): ResumableSessionCredential? {
        val authenticatedFingerprint = session.peerIdentity.fingerprint?.value ?: return null
        if (
            playerId.raw != current.playerId ||
            hostPeer.id != session.peer.id ||
            hostPeer.id.value != current.hostPeerId ||
            hostPeerId != current.hostPeerId ||
            authenticatedFingerprint != current.hostFingerprint ||
            hostFingerprint != current.hostFingerprint ||
            generation <= current.generation ||
            gameId != current.gameId ||
            gameVersion != current.gameVersion
        ) {
            return null
        }
        return runCatching {
            current.copy(
                offerId = offerId,
                secret = secret,
                generation = generation,
                issuedAtEpochMillis = issuedAtEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
            ).requireValid()
        }.getOrNull()
    }

    private fun Peer.isFreshParlorHost(kit: P2pKit): Boolean {
        if (!name.startsWith(P2P_ROOM_PREFIX)) {
            return false
        }
        // Absence of a per-peer timestamp is NOT evidence of staleness —
        // it's the absence of evidence. Trust the emission. The Android
        // adapter currently follows this path on every discovered peer.
        val seenAt = kit.lastSeen(id)
        if (seenAt == null) {
            return true
        }
        val ageMs = nowMillis() - seenAt
        return ageMs <= peerFreshnessWindowMs
    }

    private fun Peer.endpointVersion(): String = buildString {
        append(name)
        append('|')
        append(platform.name)
        append('|')
        supportedTransports.map { it.name }.sorted().joinTo(this, separator = ",")
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
        /** Discovery, dial, secure handshake and first admission-state response budget. */
        internal const val DEFAULT_JOIN_TIMEOUT_MS: Long = 30_000L
        internal const val DIAL_AND_HANDSHAKE_TIMEOUT_MS: Long = 5_000L
        internal const val FIRST_ADMISSION_RESPONSE_TIMEOUT_MS: Long = 5_000L
        /** Starts only after a valid request receives AdmissionPending. */
        internal const val HOST_APPROVAL_TIMEOUT_MS: Long = 60_000L
        internal const val DISCOVERY_REFRESH_POLL_MS: Long = 1_000L
        internal const val ADMISSION_RETRY_MS: Long = 400L
        internal const val ADMISSION_REJECTION_FLUSH_MS: Long = 100L
        internal const val REJOIN_GRACE_MS: Long = 120_000L
        internal const val ADMISSION_CONFIRM_TIMEOUT_MS: Long = 60_000L
        internal const val CREDENTIAL_MAX_AGE_MS: Long = 24L * 60L * 60L * 1_000L
        internal const val INITIAL_CREDENTIAL_GENERATION: Long = 1L
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

/** Source-compatible default that fails closed; production DI always supplies a device-bound store. */
private data object UnavailableSecureStorage : SecureStorage {
    override suspend fun put(key: String, value: ByteArray): EmptyResult<DataError> =
        Result.Failure(DataError.PermissionDenied)

    override suspend fun get(key: String): Result<ByteArray?, DataError> =
        Result.Failure(DataError.PermissionDenied)

    override suspend fun remove(key: String): EmptyResult<DataError> =
        Result.Failure(DataError.PermissionDenied)
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

private fun P2pDiagnostics.event(
    name: P2pDiagnosticEventName,
    role: P2pDiagnosticRole = P2pDiagnosticRole.NONE,
    result: P2pDiagnosticResult = P2pDiagnosticResult.NONE,
    reason: P2pDiagnosticReason = P2pDiagnosticReason.NONE,
    count: P2pDiagnosticCountBucket = P2pDiagnosticCountBucket.NONE,
) {
    record(P2pDiagnosticEvent(name, role, result, reason, count))
}

private fun Throwable.toDiagnosticReason(): P2pDiagnosticReason =
    if (generateSequence(this as Throwable?) { it.cause }.any { it is P2pError.PermissionMissing }) {
        P2pDiagnosticReason.PERMISSION
    } else {
        P2pDiagnosticReason.TRANSPORT
    }

private fun NetError.toDiagnosticResult(): P2pDiagnosticResult = when (this) {
    NetError.Timeout -> P2pDiagnosticResult.TIMEOUT
    else -> P2pDiagnosticResult.FAILURE
}

private fun NetError.toDiagnosticReason(): P2pDiagnosticReason = when (this) {
    NetError.WrongCode -> P2pDiagnosticReason.WRONG_ROOM
    NetError.RoomFull -> P2pDiagnosticReason.ROOM_FULL
    NetError.SessionStarted -> P2pDiagnosticReason.SESSION_STARTED
    NetError.IncompatibleProtocol -> P2pDiagnosticReason.INCOMPATIBLE_PROTOCOL
    NetError.Unauthorized -> P2pDiagnosticReason.UNAUTHORIZED
    NetError.RateLimited -> P2pDiagnosticReason.RATE_LIMIT
    NetError.PayloadTooLarge -> P2pDiagnosticReason.PAYLOAD_LIMIT
    NetError.NotConnected,
    NetError.Timeout,
    NetError.HostDeclined,
    NetError.RejoinExpired,
    NetError.AlreadyConnected,
    NetError.SecureStorageUnavailable,
    NetError.CommandInFlight,
    NetError.SessionSuspended,
    is NetError.TransportFailure -> P2pDiagnosticReason.TRANSPORT
}

private fun AdmissionRejection.toDiagnosticReason(): P2pDiagnosticReason = when (this) {
    AdmissionRejection.WrongCode -> P2pDiagnosticReason.WRONG_ROOM
    AdmissionRejection.RoomFull -> P2pDiagnosticReason.ROOM_FULL
    AdmissionRejection.SessionStarted -> P2pDiagnosticReason.SESSION_STARTED
    AdmissionRejection.IncompatibleProtocol -> P2pDiagnosticReason.INCOMPATIBLE_PROTOCOL
    AdmissionRejection.RateLimited -> P2pDiagnosticReason.RATE_LIMIT
    AdmissionRejection.HostDeclined,
    AdmissionRejection.InvalidRequest,
    AdmissionRejection.InvalidCredential,
    AdmissionRejection.ExpiredCredential,
    AdmissionRejection.AlreadyConnected -> P2pDiagnosticReason.UNAUTHORIZED
}

private fun CommandStatus.toDiagnosticReason(): P2pDiagnosticReason = when (this) {
    CommandStatus.Applied,
    CommandStatus.Duplicate -> P2pDiagnosticReason.NONE
    CommandStatus.InvalidAction -> P2pDiagnosticReason.INVALID_ACTION
    CommandStatus.Unauthorized -> P2pDiagnosticReason.UNAUTHORIZED
    CommandStatus.StaleRevision -> P2pDiagnosticReason.STALE_REVISION
    CommandStatus.SequenceGap -> P2pDiagnosticReason.SEQUENCE_GAP
    CommandStatus.IncompatibleVersion -> P2pDiagnosticReason.INCOMPATIBLE_PROTOCOL
    CommandStatus.PayloadTooLarge -> P2pDiagnosticReason.PAYLOAD_LIMIT
    CommandStatus.SessionEnded -> P2pDiagnosticReason.SESSION_ENDED
    CommandStatus.UnknownCommand -> P2pDiagnosticReason.UNKNOWN_COMMAND
    CommandStatus.SessionSuspended -> P2pDiagnosticReason.LIFECYCLE
}

private fun P2pDiagnostics.recordCommandResult(
    status: CommandStatus,
    role: P2pDiagnosticRole,
) {
    val (eventName, result) = when (status) {
        CommandStatus.Applied ->
            P2pDiagnosticEventName.COMMAND_ACCEPTED to P2pDiagnosticResult.SUCCESS
        CommandStatus.Duplicate ->
            P2pDiagnosticEventName.COMMAND_DUPLICATE to P2pDiagnosticResult.DUPLICATE
        else -> P2pDiagnosticEventName.COMMAND_REJECTED to P2pDiagnosticResult.REJECTED
    }
    event(eventName, role, result, status.toDiagnosticReason())
}

private fun PeerMessage.hasValidPeerPayloadBounds(): Boolean = when (this) {
    is PeerMessage.ClientCommand -> payload.size <= MAX_COMMAND_PAYLOAD_BYTES
    is PeerMessage.ActionSubmit -> payload.size <= MAX_COMMAND_PAYLOAD_BYTES
    else -> true
}

/** Ensure a partially-started kit is released even when its owner is cancelled. */
private suspend fun P2pKit.stopAfterFailure(diagnostics: P2pDiagnostics) {
    diagnostics.event(P2pDiagnosticEventName.CLEANUP_STARTED)
    withContext(NonCancellable) {
        try {
            stop()
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_COMPLETED,
                result = P2pDiagnosticResult.SUCCESS,
            )
        } catch (failure: Throwable) {
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_FAILED,
                result = P2pDiagnosticResult.FAILURE,
                reason = failure.toDiagnosticReason(),
            )
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
    AdmissionRejection.InvalidCredential -> NetError.Unauthorized
    AdmissionRejection.ExpiredCredential -> NetError.RejoinExpired
    AdmissionRejection.AlreadyConnected -> NetError.AlreadyConnected
}

private fun DiscoveryFinalError.toNetError(): NetError = when (this) {
    DiscoveryFinalError.IncompatibleProtocol -> NetError.IncompatibleProtocol
    DiscoveryFinalError.WrongCode -> NetError.WrongCode
    DiscoveryFinalError.Timeout -> NetError.Timeout
}

// ============================================================================ Host room ==

internal class HostP2pRoom(
    private val kit: P2pKit,
    private val roomCode: String,
    roomDisplayName: String,
    private val hostPlayerId: PlayerId,
    private val maxRemotePlayers: Int,
    private val gameProtocol: HostedGameProtocol? = null,
    private val scope: CoroutineScope,
    private val codec: RoomMessageCodec,
    private val diagnostics: P2pDiagnostics = NoOpP2pDiagnostics,
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
    private val incomingPeerMessages = Channel<RoomMessage>(
        capacity = P2pTrafficLimits.HOST_APPLICATION_QUEUE_CAPACITY,
    )
    override val incoming: Flow<RoomMessage> = incomingPeerMessages.receiveAsFlow()

    // Authoritative per-player membership. Survives transient session drops so
    // a returning peer is recognised as a reconnect (same playerId) rather
    // than a brand-new join. Every access is serialized by [stateMutex].
    private val membersByPlayer: MutableMap<PlayerId, RoomMember> = mutableMapOf()
    private val sessionsByPlayer: MutableMap<PlayerId, P2pSession> = mutableMapOf()
    private data class PendingConnection(
        val session: P2pSession,
        val displayName: String,
        val isRejoin: Boolean,
        val peerFingerprint: String,
        var transaction: AdmissionOfferTransaction? = null,
    )
    private data class AdmissionOfferTransaction(
        val offer: ResumableCredentialOffer,
        val confirmation: CompletableDeferred<Unit>,
        val kind: CredentialTransactionKind,
    )
    private data class ResumeReadyBarrier(
        val session: P2pSession,
        val offerId: String,
        val generation: Long,
        val signal: CompletableDeferred<Unit> = CompletableDeferred(),
        var ready: Boolean = false,
    )
    private data class AdmissionReadyBarrier(
        val session: P2pSession,
        val offerId: String,
        val generation: Long,
        val signal: CompletableDeferred<Unit> = CompletableDeferred(),
        var ready: Boolean = false,
    )
    private enum class CredentialTransactionKind { Admission, Resume }
    private data class HostCredential(
        val digest: ByteArray,
        val generation: Long,
        val peerFingerprint: String,
        val offerId: String,
        val expiresAtEpochMillis: Long,
        var previousDigest: ByteArray? = null,
        var previousGeneration: Long? = null,
    ) {
        fun wipe() {
            digest.fill(0)
            previousDigest?.fill(0)
        }
    }
    private val pendingByPlayer: MutableMap<PlayerId, PendingConnection> = mutableMapOf()
    /** Seats approved by the host but not yet committed after acceptance delivery. */
    private val admissionReservations: MutableSet<PlayerId> = mutableSetOf()
    private val credentialsByPlayer: MutableMap<PlayerId, HostCredential> = mutableMapOf()
    private val rejoinDeadlineByPlayer: MutableMap<PlayerId, Long> = mutableMapOf()
    private val resumeReadyByPlayer: MutableMap<PlayerId, ResumeReadyBarrier> = mutableMapOf()
    private val admissionReadyByPlayer: MutableMap<PlayerId, AdmissionReadyBarrier> = mutableMapOf()
    private var admissionsClosed: Boolean = false
    // Tracks every PlayerId we have ever accepted a session from in this
    // room's lifetime. A peer that fully disconnects (PeerLeft fired) and
    // then reconnects with the same PlayerId is a *reconnect*, not a new
    // join: both game-side host bridges (Mafia + Whodunit) no-op on
    // PeerJoined and only re-ship the snapshot on PeerReconnected, so
    // emitting the wrong event would strand the recovered peer in a
    // disconnected state forever.
    private val previouslySeenPlayerIds: MutableSet<PlayerId> = mutableSetOf()
    private val trackedSessions: MutableSet<P2pSession> = mutableSetOf()
    private val admissionAttemptLimiter = AdmissionAttemptLimiter(nowMillis())

    /** Session collectors/transactions are children of this room, not the app scope. */
    private val sessionSupervisor = SupervisorJob(scope.coroutineContext[Job])
    private val sessionScope = CoroutineScope(scope.coroutineContext + sessionSupervisor)

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

    private val acceptJob: Job = sessionScope.launch {
        kit.incomingSessions.collect { session ->
            handleIncomingSession(session)
        }
    }

    private suspend fun handleIncomingSession(session: P2pSession) {
        val playerId = PlayerId(session.peer.id.value)
        val displayName = session.peer.name
        val peerFingerprint = session.peerIdentity.fingerprint?.value
        if (
            peerFingerprint == null ||
            session.peerIdentity.peerId != session.peer.id
        ) {
            rejectSession(session, AdmissionRejection.InvalidCredential)
            return
        }

        val trackingDecision = stateMutex.withLock {
            when {
                session in trackedSessions -> null
                left || trackedSessions.size >= trackedSessionLimit() -> false
                else -> true.also { trackedSessions += session }
            }
        }
        if (trackingDecision == null) return
        if (!trackingDecision) {
            rejectSession(session, AdmissionRejection.RateLimited)
            return
        }

        val trafficGuard = InboundTrafficGuard(
            maxFrameBytes = P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES,
            nowMillis = nowMillis(),
        )

        val incomingJob = sessionScope.launch {
            session.incoming.collect { msg ->
                if (msg !is P2pMessage.Binary) {
                    enforceTrafficDecision(
                        trafficGuard.malformedFrame(nowMillis()),
                        session,
                        P2pDiagnosticReason.MALFORMED,
                    )
                    return@collect
                }
                if (
                    !enforceTrafficDecision(
                        trafficGuard.inspectFrame(msg.bytes.size, nowMillis()),
                        session,
                        P2pDiagnosticReason.RATE_LIMIT,
                    )
                ) {
                    return@collect
                }
                // p2p-002: never let a malformed/oversized/version-skewed frame
                // throw out of this collect — that would cancel the coroutine and
                // permanently kill THIS session's inbound stream. Skip + log.
                val rawDecoded = runCatching {
                    codec.decode(msg.bytes)
                }.getOrElse {
                    enforceTrafficDecision(
                        trafficGuard.malformedFrame(nowMillis()),
                        session,
                        P2pDiagnosticReason.MALFORMED,
                    )
                    return@collect
                }
                if (rawDecoded !is PeerMessage || !rawDecoded.hasValidPeerPayloadBounds()) {
                    enforceTrafficDecision(
                        trafficGuard.malformedFrame(nowMillis()),
                        session,
                        P2pDiagnosticReason.WRONG_DIRECTION,
                    )
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
                    is PeerMessage.AdmissionConfirmed -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.AdmissionCommitAck -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.AdmissionReady -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.ResumeRequested -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.ResumeConfirmed -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.ResumeCommitAck -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.ResumeReady -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.ClientCommand -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.SnapshotRequest -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.SessionHeartbeat -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.CommandOutcomeRequest -> rawDecoded.copy(actor = playerId)
                    else -> rawDecoded
                }
                val admitted = stateMutex.withLock {
                    sessionsByPlayer[playerId] === session
                }
                if (admitted) {
                    when (decoded) {
                        is PeerMessage.AdmissionRequest,
                        is PeerMessage.AdmissionConfirmed -> {
                            val credential = stateMutex.withLock {
                                credentialsByPlayer[playerId]
                            }
                            if (credential != null) {
                                sendRaw(
                                    session,
                                    HostMessage.AdmissionCommitted(
                                        playerId = playerId,
                                        offerId = credential.offerId,
                                        generation = credential.generation,
                                    ),
                                )
                            }
                            return@collect
                        }
                        is PeerMessage.AdmissionCommitAck -> return@collect
                        is PeerMessage.AdmissionReady -> {
                            handleAdmissionReady(playerId, session, decoded)
                            return@collect
                        }
                        is PeerMessage.ResumeCommitAck -> {
                            acknowledgeResumeCommit(playerId, decoded)
                            return@collect
                        }
                        is PeerMessage.ResumeReady -> {
                            handleResumeReady(playerId, session, decoded)
                            return@collect
                        }
                        is PeerMessage.ResumeConfirmed -> {
                            val credential = stateMutex.withLock {
                                credentialsByPlayer[playerId]
                            }
                            if (
                                credential != null &&
                                credential.offerId == decoded.offerId &&
                                credential.generation == decoded.generation
                            ) {
                                sendRaw(
                                    session,
                                    HostMessage.ResumeCommitted(
                                        playerId,
                                        credential.offerId,
                                        credential.generation,
                                    ),
                                )
                            } else {
                                rejectSession(session, AdmissionRejection.InvalidCredential)
                            }
                            return@collect
                        }
                        is PeerMessage.ResumeRequested -> {
                            rejectSession(session, AdmissionRejection.AlreadyConnected)
                            return@collect
                        }
                        else -> Unit
                    }
                }
                if (!admitted) {
                    when (decoded) {
                        is PeerMessage.AdmissionRequest -> handleAdmissionRequest(
                            playerId,
                            displayName,
                            peerFingerprint,
                            session,
                            decoded,
                        )
                        is PeerMessage.AdmissionConfirmed -> handleCredentialConfirmation(
                            playerId,
                            session,
                            decoded.offerId,
                            decoded.generation,
                            CredentialTransactionKind.Admission,
                        )
                        is PeerMessage.ResumeRequested -> handleResumeRequest(
                            playerId,
                            displayName,
                            peerFingerprint,
                            session,
                            decoded,
                        )
                        is PeerMessage.ResumeConfirmed -> handleCredentialConfirmation(
                            playerId,
                            session,
                            decoded.offerId,
                            decoded.generation,
                            CredentialTransactionKind.Resume,
                        )
                        is PeerMessage.ResumeReady ->
                            rejectSession(session, AdmissionRejection.InvalidCredential)
                        is PeerMessage.AdmissionReady ->
                            rejectSession(session, AdmissionRejection.InvalidCredential)
                        is PeerMessage.ResumeCommitAck,
                        is PeerMessage.AdmissionCommitAck ->
                            rejectSession(session, AdmissionRejection.InvalidCredential)
                        else -> Unit
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
                    handleExplicitLeave(playerId, displayName, session)
                    return@collect
                }
                if (decoded is PeerMessage.ClientCommand) {
                    diagnostics.event(
                        P2pDiagnosticEventName.COMMAND_RECEIVED,
                        P2pDiagnosticRole.HOST,
                    )
                }
                incomingPeerMessages.send(decoded)
            }
        }

        sessionScope.launch {
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
                        incomingJob.cancel()
                        // Only act if this is still the registered session
                        // for this playerId — a newer session may have
                        // superseded it via handleIncomingSession.
                        val removed = stateMutex.withLock {
                            admissionReadyByPlayer[playerId]
                                ?.takeIf { it.session === session }
                                ?.let { barrier ->
                                    admissionReadyByPlayer.remove(playerId)
                                    barrier.signal.complete(Unit)
                                }
                            resumeReadyByPlayer[playerId]
                                ?.takeIf { it.session === session }
                                ?.let { barrier ->
                                    resumeReadyByPlayer.remove(playerId)
                                    barrier.signal.complete(Unit)
                                }
                            if (pendingByPlayer[playerId]?.session === session) {
                                pendingByPlayer.remove(playerId)
                                    ?.transaction
                                    ?.confirmation
                                    ?.complete(Unit)
                                admissionReservations.remove(playerId)
                                publishPendingAdmissions()
                            }
                            trackedSessions.remove(session)
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
        peerFingerprint: String,
        session: P2pSession,
        request: PeerMessage.AdmissionRequest,
    ) {
        val attemptAllowed = stateMutex.withLock {
            pendingByPlayer[playerId]?.session === session ||
                admissionAttemptLimiter.tryAcquire(playerId.raw, nowMillis())
        }
        if (!attemptAllowed) {
            rejectSession(session, AdmissionRejection.RateLimited)
            return
        }
        if (!request.protocol.isCompatibleWith(ProtocolVersion())) {
            rejectSession(session, AdmissionRejection.IncompatibleProtocol)
            return
        }
        if (request.roomCode != roomCode) {
            rejectSession(session, AdmissionRejection.WrongCode)
            return
        }
        if (request.rejoinToken != null) {
            rejectSession(session, AdmissionRejection.InvalidCredential)
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

        val requestEvent = stateMutex.withLock {
            val existing = pendingByPlayer[playerId]
            val isKnownPlayer = playerId in previouslySeenPlayerIds
            when {
                admissionsClosed -> AdmissionRequestResult.Rejected(
                    AdmissionRejection.SessionStarted,
                )
                existing?.session === session -> AdmissionRequestResult.Accepted(
                    PendingAdmission(playerId, displayName, isRejoin = isKnownPlayer),
                    emitEvent = false,
                )
                existing != null || pendingByPlayer.size >= pendingAdmissionLimit() ->
                    AdmissionRequestResult.Rejected(AdmissionRejection.RateLimited)
                else -> {
                    pendingByPlayer[playerId] = PendingConnection(
                        session = session,
                        displayName = displayName,
                        isRejoin = isKnownPlayer,
                        peerFingerprint = peerFingerprint,
                    )
                    publishPendingAdmissions()
                    AdmissionRequestResult.Accepted(
                        PendingAdmission(playerId, displayName, isRejoin = isKnownPlayer),
                        emitEvent = true,
                    )
                }
            }
        }
        when (requestEvent) {
            is AdmissionRequestResult.Rejected -> rejectSession(session, requestEvent.reason)
            is AdmissionRequestResult.Accepted -> {
                try {
                    sendRaw(
                        session,
                        HostMessage.AdmissionPending(requestEvent.admission.playerId),
                    )
                } catch (failure: Throwable) {
                    failure.rethrowIfCancellation()
                    runCatching { session.close() }
                    return
                }
                if (requestEvent.emitEvent) {
                    diagnostics.event(
                        P2pDiagnosticEventName.ADMISSION_REQUESTED,
                        P2pDiagnosticRole.HOST,
                    )
                    _peerEvents.emit(
                        PeerEvent.AdmissionRequested(
                            playerId = requestEvent.admission.playerId,
                            displayName = requestEvent.admission.displayName,
                            isRejoin = requestEvent.admission.isRejoin,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handleCredentialConfirmation(
        playerId: PlayerId,
        session: P2pSession,
        offerId: String,
        generation: Long,
        kind: CredentialTransactionKind,
    ) {
        val matched = stateMutex.withLock {
            val pending = pendingByPlayer[playerId]
            val transaction = pending?.transaction
            if (
                pending?.session === session &&
                transaction != null &&
                transaction.offer.offerId == offerId &&
                transaction.offer.generation == generation &&
                transaction.kind == kind
            ) {
                transaction.confirmation.complete(Unit)
                true
            } else {
                false
            }
        }
        if (!matched) {
            rejectSession(session, AdmissionRejection.InvalidCredential)
        }
    }

    private suspend fun handleResumeRequest(
        playerId: PlayerId,
        transportDisplayName: String,
        peerFingerprint: String,
        session: P2pSession,
        request: PeerMessage.ResumeRequested,
    ) {
        val repeatedPendingRequest = stateMutex.withLock {
            pendingByPlayer[playerId]?.let { pending ->
                pending.session === session &&
                    pending.transaction?.kind == CredentialTransactionKind.Resume
            } == true
        }
        if (repeatedPendingRequest) return
        if (
            !stateMutex.withLock {
                admissionAttemptLimiter.tryAcquire(playerId.raw, nowMillis())
            }
        ) {
            rejectSession(session, AdmissionRejection.RateLimited)
            return
        }
        val displayName = transportDisplayName.trim()
        if (
            !request.protocol.isCompatibleWith(ProtocolVersion()) ||
            request.roomCode != roomCode ||
            displayName.isEmpty() ||
            displayName.length > P2pKitRoomTransport.MAX_DISPLAY_NAME_LENGTH ||
            request.displayName.trim() != displayName ||
            request.secret.length != 64 ||
            request.secret.any { it !in '0'..'9' && it !in 'a'..'f' }
        ) {
            rejectSession(session, AdmissionRejection.InvalidCredential)
            return
        }

        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val providedDigest = SecureHashes.sha256Utf8(request.secret)
        val prepared = try {
            stateMutex.withLock {
                val credential = credentialsByPlayer[playerId]
                    ?: return@withLock ResumePreparation.Rejected(
                        AdmissionRejection.InvalidCredential,
                    )
                val member = membersByPlayer[playerId]
                    ?: return@withLock ResumePreparation.Rejected(
                        AdmissionRejection.InvalidCredential,
                    )
                val currentSession = sessionsByPlayer[playerId]
                val deadline = rejoinDeadlineByPlayer[playerId] ?: Long.MIN_VALUE
                when {
                    currentSession?.state?.value == ConnectionState.Connected ->
                        ResumePreparation.Rejected(AdmissionRejection.AlreadyConnected)
                    playerId in admissionReservations || playerId in pendingByPlayer ||
                        pendingByPlayer.size >= pendingAdmissionLimit() ->
                        ResumePreparation.Rejected(AdmissionRejection.RateLimited)
                    now > deadline || now > credential.expiresAtEpochMillis ->
                        ResumePreparation.Rejected(AdmissionRejection.ExpiredCredential)
                    member.displayName != displayName ||
                        credential.peerFingerprint != peerFingerprint ->
                        ResumePreparation.Rejected(AdmissionRejection.InvalidCredential)
                    else -> {
                        val matchedGeneration = when {
                            request.generation == credential.generation &&
                                SecureHashes.constantTimeEquals(
                                    providedDigest,
                                    credential.digest,
                                ) -> credential.generation
                            request.generation == credential.previousGeneration &&
                                credential.previousDigest != null &&
                                SecureHashes.constantTimeEquals(
                                    providedDigest,
                                    credential.previousDigest!!,
                                ) -> checkNotNull(credential.previousGeneration)
                            else -> null
                        } ?: return@withLock ResumePreparation.Rejected(
                            AdmissionRejection.InvalidCredential,
                        )
                        if (credential.generation == Long.MAX_VALUE) {
                            return@withLock ResumePreparation.Rejected(
                                AdmissionRejection.InvalidCredential,
                            )
                        }
                        val offer = ResumableCredentialOffer(
                            offerId = SecureIds.id128(),
                            playerId = playerId,
                            hostPeerId = kit.localPeerId.value,
                            hostFingerprint = checkNotNull(kit.localFingerprint).value,
                            secret = SecureIds.rejoinToken256(),
                            generation = credential.generation + 1L,
                            issuedAtEpochMillis = now,
                            expiresAtEpochMillis = now + P2pKitRoomTransport.CREDENTIAL_MAX_AGE_MS,
                            gameId = gameProtocol?.gameId?.raw,
                            gameVersion = gameProtocol?.gameVersion,
                        )
                        val transaction = AdmissionOfferTransaction(
                            offer = offer,
                            confirmation = CompletableDeferred(),
                            kind = CredentialTransactionKind.Resume,
                        )
                        pendingByPlayer[playerId] = PendingConnection(
                            session = session,
                            displayName = displayName,
                            isRejoin = true,
                            peerFingerprint = peerFingerprint,
                            transaction = transaction,
                        )
                        admissionReservations += playerId
                        publishPendingAdmissions()
                        val matchedDigest = if (matchedGeneration == credential.generation) {
                            credential.digest.copyOf()
                        } else {
                            checkNotNull(credential.previousDigest).copyOf()
                        }
                        ResumePreparation.Ready(transaction, matchedGeneration, matchedDigest)
                    }
                }
            }
        } finally {
            providedDigest.fill(0)
        }

        when (prepared) {
            is ResumePreparation.Rejected -> rejectSession(session, prepared.reason)
            is ResumePreparation.Ready -> {
                val job = sessionScope.launch(start = CoroutineStart.LAZY) {
                    completeResume(playerId, session, displayName, prepared)
                }
                job.start()
            }
        }
    }

    private suspend fun completeResume(
        playerId: PlayerId,
        session: P2pSession,
        displayName: String,
        prepared: ResumePreparation.Ready,
    ) {
        val transaction = prepared.transaction
        try {
            sendRaw(session, HostMessage.ResumeOffered(transaction.offer))
            val confirmed = withTimeoutOrNull(P2pKitRoomTransport.ADMISSION_CONFIRM_TIMEOUT_MS) {
                transaction.confirmation.await()
                true
            } ?: false
            if (!confirmed) {
                prepared.matchedDigest.fill(0)
                rollbackAdmission(playerId, session)
                runCatching { session.close() }
                return
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                rollbackAdmission(playerId, session)
                runCatching { session.close() }
            }
            prepared.matchedDigest.fill(0)
            failure.rethrowIfCancellation()
            return
        }

        val commit = stateMutex.withLock {
            val pending = pendingByPlayer[playerId]
            val previousCredential = credentialsByPlayer[playerId]
            if (
                playerId !in admissionReservations ||
                pending?.session !== session ||
                pending.transaction !== transaction ||
                previousCredential == null ||
                session.state.value != ConnectionState.Connected
            ) {
                null
            } else {
                admissionReservations.remove(playerId)
                pendingByPlayer.remove(playerId)
                publishPendingAdmissions()
                val oldSession = sessionsByPlayer[playerId]?.takeIf { it !== session }
                sessionsByPlayer[playerId] = session
                membersByPlayer[playerId] = RoomMember(playerId, displayName, connected = true)
                rejoinDeadlineByPlayer.remove(playerId)
                credentialsByPlayer[playerId] = HostCredential(
                    digest = SecureHashes.sha256Utf8(transaction.offer.secret),
                    generation = transaction.offer.generation,
                    peerFingerprint = pending.peerFingerprint,
                    offerId = transaction.offer.offerId,
                    expiresAtEpochMillis = transaction.offer.expiresAtEpochMillis,
                    previousDigest = prepared.matchedDigest.copyOf(),
                    previousGeneration = prepared.matchedGeneration,
                )
                val readyBarrier = ResumeReadyBarrier(
                    session = session,
                    offerId = transaction.offer.offerId,
                    generation = transaction.offer.generation,
                )
                resumeReadyByPlayer[playerId] = readyBarrier
                publishMembers()
                AdmissionCommit(oldSession, previousCredential, readyBarrier)
            }
        } ?: run {
            prepared.matchedDigest.fill(0)
            rollbackAdmission(playerId, session)
            runCatching { session.close() }
            return
        }
        prepared.matchedDigest.fill(0)
        commit.previousSession?.let { runCatching { it.close() } }
        commit.previousCredential?.wipe()
        try {
            sendRaw(
                session,
                HostMessage.ResumeCommitted(
                    playerId = playerId,
                    offerId = transaction.offer.offerId,
                    generation = transaction.offer.generation,
                ),
            )
        } catch (failure: Throwable) {
            withContext(NonCancellable) { runCatching { session.close() } }
            failure.rethrowIfCancellation()
            return
        }
        val expectedBarrier = checkNotNull(commit.readyBarrier)
        val ready = withTimeoutOrNull(P2pKitRoomTransport.ADMISSION_CONFIRM_TIMEOUT_MS) {
            expectedBarrier.signal.await()
            stateMutex.withLock {
                val barrier = resumeReadyByPlayer[playerId]
                if (
                    barrier === expectedBarrier &&
                    barrier.ready &&
                    barrier.session === session &&
                    sessionsByPlayer[playerId] === session &&
                    session.state.value == ConnectionState.Connected
                ) {
                    resumeReadyByPlayer.remove(playerId)
                    true
                } else {
                    false
                }
            }
        } ?: false
        if (!ready) {
            stateMutex.withLock {
                if (resumeReadyByPlayer[playerId] === expectedBarrier) {
                    resumeReadyByPlayer.remove(playerId)
                }
            }
            runCatching { session.close() }
            return
        }
        _peerEvents.emit(PeerEvent.PeerReconnected(playerId, displayName))
        markActiveIfRestored()
    }

    private suspend fun handleAdmissionReady(
        playerId: PlayerId,
        session: P2pSession,
        ready: PeerMessage.AdmissionReady,
    ) {
        val matched = stateMutex.withLock {
            val barrier = admissionReadyByPlayer[playerId]
            if (
                barrier != null &&
                barrier.session === session &&
                barrier.offerId == ready.offerId &&
                barrier.generation == ready.generation
            ) {
                barrier.ready = true
                barrier.signal.complete(Unit)
                true
            } else {
                val credential = credentialsByPlayer[playerId]
                credential?.offerId == ready.offerId &&
                    credential.generation == ready.generation &&
                    sessionsByPlayer[playerId] === session
            }
        }
        if (!matched) rejectSession(session, AdmissionRejection.InvalidCredential)
    }

    private suspend fun handleResumeReady(
        playerId: PlayerId,
        session: P2pSession,
        ready: PeerMessage.ResumeReady,
    ) {
        val matched = stateMutex.withLock {
            val barrier = resumeReadyByPlayer[playerId]
            if (
                barrier != null &&
                barrier.session === session &&
                barrier.offerId == ready.offerId &&
                barrier.generation == ready.generation
            ) {
                barrier.ready = true
                barrier.signal.complete(Unit)
                true
            } else {
                val credential = credentialsByPlayer[playerId]
                credential?.offerId == ready.offerId &&
                    credential.generation == ready.generation &&
                    sessionsByPlayer[playerId] === session
            }
        }
        if (!matched) rejectSession(session, AdmissionRejection.InvalidCredential)
    }

    private suspend fun acknowledgeResumeCommit(
        playerId: PlayerId,
        acknowledgement: PeerMessage.ResumeCommitAck,
    ) {
        val obsolete = stateMutex.withLock {
            credentialsByPlayer[playerId]?.takeIf {
                it.offerId == acknowledgement.offerId &&
                    it.generation == acknowledgement.generation
            }?.let { credential ->
                credential.previousDigest.also {
                    credential.previousDigest = null
                    credential.previousGeneration = null
                }
            }
        }
        obsolete?.fill(0)
    }

    private sealed interface ResumePreparation {
        data class Rejected(val reason: AdmissionRejection) : ResumePreparation
        data class Ready(
            val transaction: AdmissionOfferTransaction,
            val matchedGeneration: Long,
            val matchedDigest: ByteArray,
        ) : ResumePreparation
    }

    override suspend fun approveAdmission(playerId: PlayerId): Result<Unit, NetError> {
        val pending = stateMutex.withLock { pendingByPlayer[playerId] }
            ?: return Result.Failure(NetError.NotConnected)
        return admit(playerId, pending.session, pending.displayName, pending.isRejoin)
    }

    override suspend fun rejectAdmission(playerId: PlayerId): Result<Unit, NetError> {
        val pending = stateMutex.withLock {
            if (playerId in admissionReservations) {
                AdmissionRejectionResult.InFlight
            } else {
                pendingByPlayer.remove(playerId)?.let(AdmissionRejectionResult::Ready)
                    ?: AdmissionRejectionResult.Missing
            }.also { publishPendingAdmissions() }
        }
        when (pending) {
            AdmissionRejectionResult.InFlight -> return Result.Failure(NetError.CommandInFlight)
            AdmissionRejectionResult.Missing -> return Result.Failure(NetError.NotConnected)
            is AdmissionRejectionResult.Ready -> Unit
        }
        rejectSession(pending.connection.session, AdmissionRejection.HostDeclined)
        return Result.Success(Unit)
    }

    override suspend fun closeAdmissions() {
        val pending = stateMutex.withLock {
            admissionsClosed = true
            pendingByPlayer.keys
                .filterNot(admissionReservations::contains)
                .mapNotNull(pendingByPlayer::remove)
                .also {
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
    ): Result<Unit, NetError> {
        val transaction = when (val prepared = stateMutex.withLock {
            val pending = pendingByPlayer[playerId]
            when {
                pending?.session !== session -> null
                playerId in admissionReservations -> AdmissionPreparation.InFlight
                session.state.value != ConnectionState.Connected -> {
                    pendingByPlayer.remove(playerId)
                    publishPendingAdmissions()
                    null
                }
                playerId !in membersByPlayer &&
                    membersByPlayer.size + admissionReservations.count { it !in membersByPlayer } >=
                    maxRemotePlayers -> {
                    pendingByPlayer.remove(playerId)
                    publishPendingAdmissions()
                    AdmissionPreparation.Rejected(
                        error = NetError.RoomFull,
                        reason = AdmissionRejection.RoomFull,
                    )
                }
                else -> {
                    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    val offer = ResumableCredentialOffer(
                        offerId = SecureIds.id128(),
                        playerId = playerId,
                        hostPeerId = kit.localPeerId.value,
                        hostFingerprint = checkNotNull(kit.localFingerprint).value,
                        secret = SecureIds.rejoinToken256(),
                        generation = P2pKitRoomTransport.INITIAL_CREDENTIAL_GENERATION,
                        issuedAtEpochMillis = now,
                        expiresAtEpochMillis = now + P2pKitRoomTransport.CREDENTIAL_MAX_AGE_MS,
                        gameId = gameProtocol?.gameId?.raw,
                        gameVersion = gameProtocol?.gameVersion,
                    )
                    val transaction = AdmissionOfferTransaction(
                        offer = offer,
                        confirmation = CompletableDeferred(),
                        kind = CredentialTransactionKind.Admission,
                    )
                    admissionReservations += playerId
                    pending.transaction = transaction
                    AdmissionPreparation.Ready(transaction)
                }
            }
        }) {
            null -> return Result.Failure(NetError.NotConnected)
            is AdmissionPreparation.Rejected -> {
                rejectSession(session, prepared.reason)
                return Result.Failure(prepared.error)
            }
            AdmissionPreparation.InFlight -> return Result.Failure(NetError.CommandInFlight)
            is AdmissionPreparation.Ready -> prepared.transaction
        }
        diagnostics.event(
            P2pDiagnosticEventName.ADMISSION_RESERVED,
            P2pDiagnosticRole.HOST,
        )

        try {
            sendRaw(session, HostMessage.AdmissionOffered(transaction.offer))
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                rollbackAdmission(playerId, session)
                runCatching { session.close() }
            }
            failure.rethrowIfCancellation()
            return Result.Failure(NetError.TransportFailure("admission offer failed"))
        }

        val confirmed = try {
            withTimeoutOrNull(P2pKitRoomTransport.ADMISSION_CONFIRM_TIMEOUT_MS) {
                transaction.confirmation.await()
                true
            } ?: false
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                rollbackAdmission(playerId, session)
                runCatching { session.close() }
            }
            failure.rethrowIfCancellation()
            return Result.Failure(NetError.TransportFailure("admission confirmation failed"))
        }
        if (!confirmed) {
            rollbackAdmission(playerId, session)
            runCatching { session.close() }
            return Result.Failure(NetError.Timeout)
        }

        val commit = stateMutex.withLock {
            val pending = pendingByPlayer[playerId]
            if (
                playerId !in admissionReservations ||
                pending?.session !== session ||
                pending.transaction !== transaction ||
                session.state.value != ConnectionState.Connected
            ) {
                null
            } else {
                admissionReservations.remove(playerId)
                pendingByPlayer.remove(playerId)
                publishPendingAdmissions()
                val old = sessionsByPlayer[playerId]?.takeIf { it !== session }
                sessionsByPlayer[playerId] = session
                membersByPlayer[playerId] = RoomMember(playerId, displayName, connected = true)
                previouslySeenPlayerIds += playerId
                rejoinDeadlineByPlayer.remove(playerId)
                val oldCredential = credentialsByPlayer.put(
                    playerId,
                    HostCredential(
                        digest = SecureHashes.sha256Utf8(transaction.offer.secret),
                        generation = transaction.offer.generation,
                        peerFingerprint = pending.peerFingerprint,
                        offerId = transaction.offer.offerId,
                        expiresAtEpochMillis = transaction.offer.expiresAtEpochMillis,
                    ),
                )
                val readyBarrier = AdmissionReadyBarrier(
                    session = session,
                    offerId = transaction.offer.offerId,
                    generation = transaction.offer.generation,
                )
                admissionReadyByPlayer[playerId] = readyBarrier
                publishMembers()
                AdmissionCommit(
                    previousSession = old,
                    previousCredential = oldCredential,
                    admissionReadyBarrier = readyBarrier,
                )
            }
        } ?: run {
            rollbackAdmission(playerId, session)
            runCatching { session.close() }
            return Result.Failure(NetError.NotConnected)
        }
        diagnostics.event(
            P2pDiagnosticEventName.ADMISSION_COMMITTED,
            P2pDiagnosticRole.HOST,
            P2pDiagnosticResult.SUCCESS,
        )
        commit.previousSession?.let { runCatching { it.close() } }
        commit.previousCredential?.wipe()
        try {
            sendRaw(
                session,
                HostMessage.AdmissionCommitted(
                    playerId = playerId,
                    offerId = transaction.offer.offerId,
                    generation = transaction.offer.generation,
                ),
            )
        } catch (failure: Throwable) {
            withContext(NonCancellable) { runCatching { session.close() } }
            failure.rethrowIfCancellation()
            // Confirmation proves that the peer durably owns this committed
            // capability. A lost commit frame is recovered through resume;
            // rolling back here would create a split-brain credential.
        }
        val expectedBarrier = checkNotNull(commit.admissionReadyBarrier)
        val ready = withTimeoutOrNull(P2pKitRoomTransport.ADMISSION_CONFIRM_TIMEOUT_MS) {
            expectedBarrier.signal.await()
            stateMutex.withLock {
                val barrier = admissionReadyByPlayer[playerId]
                if (
                    barrier === expectedBarrier &&
                    barrier.ready &&
                    barrier.session === session &&
                    sessionsByPlayer[playerId] === session &&
                    session.state.value == ConnectionState.Connected
                ) {
                    admissionReadyByPlayer.remove(playerId)
                    true
                } else {
                    false
                }
            }
        } ?: false
        if (!ready) {
            stateMutex.withLock {
                if (admissionReadyByPlayer[playerId] === expectedBarrier) {
                    admissionReadyByPlayer.remove(playerId)
                }
            }
            runCatching { session.close() }
            return Result.Failure(NetError.Timeout)
        }
        if (isRejoin) {
            _peerEvents.emit(PeerEvent.PeerReconnected(playerId, displayName))
        } else {
            _peerEvents.emit(PeerEvent.PeerJoined(playerId, displayName))
        }
        markActiveIfRestored()
        return Result.Success(Unit)
    }

    private suspend fun rollbackAdmission(playerId: PlayerId, session: P2pSession) {
        stateMutex.withLock {
            admissionReservations.remove(playerId)
            if (pendingByPlayer[playerId]?.session === session) {
                pendingByPlayer.remove(playerId)
                publishPendingAdmissions()
            }
        }
        diagnostics.event(
            P2pDiagnosticEventName.ADMISSION_ROLLED_BACK,
            P2pDiagnosticRole.HOST,
        )
    }

    private sealed interface AdmissionPreparation {
        data object InFlight : AdmissionPreparation
        data class Ready(val transaction: AdmissionOfferTransaction) : AdmissionPreparation
        data class Rejected(
            val error: NetError,
            val reason: AdmissionRejection,
        ) : AdmissionPreparation
    }

    private sealed interface AdmissionRequestResult {
        data class Accepted(
            val admission: PendingAdmission,
            val emitEvent: Boolean,
        ) : AdmissionRequestResult

        data class Rejected(val reason: AdmissionRejection) : AdmissionRequestResult
    }

    private sealed interface AdmissionRejectionResult {
        data object InFlight : AdmissionRejectionResult
        data object Missing : AdmissionRejectionResult
        data class Ready(val connection: PendingConnection) : AdmissionRejectionResult
    }

    private data class AdmissionCommit(
        val previousSession: P2pSession?,
        val previousCredential: HostCredential?,
        val readyBarrier: ResumeReadyBarrier? = null,
        val admissionReadyBarrier: AdmissionReadyBarrier? = null,
    )

    private fun pendingAdmissionLimit(): Int =
        (maxRemotePlayers + P2pTrafficLimits.SESSION_ADMISSION_HEADROOM)
            .coerceAtMost(P2pTrafficLimits.MAX_PENDING_ADMISSION_REQUESTS)

    private fun trackedSessionLimit(): Int =
        (maxRemotePlayers + P2pTrafficLimits.SESSION_ADMISSION_HEADROOM)
            .coerceAtMost(P2pTrafficLimits.MAX_TRACKED_SESSIONS)

    private suspend fun enforceTrafficDecision(
        decision: InboundTrafficDecision,
        session: P2pSession,
        reason: P2pDiagnosticReason,
    ): Boolean = when (decision) {
        InboundTrafficDecision.Accept -> true
        InboundTrafficDecision.Drop -> {
            diagnostics.event(
                P2pDiagnosticEventName.FRAME_DROPPED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.REJECTED,
                reason,
            )
            false
        }
        InboundTrafficDecision.Disconnect -> {
            diagnostics.event(
                P2pDiagnosticEventName.PEER_RATE_LIMITED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.REJECTED,
                reason,
            )
            try {
                session.close()
            } catch (failure: Throwable) {
                failure.rethrowIfCancellation()
            }
            false
        }
    }

    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private suspend fun rejectSession(
        session: P2pSession,
        reason: AdmissionRejection,
    ) {
        diagnostics.event(
            if (reason == AdmissionRejection.IncompatibleProtocol) {
                P2pDiagnosticEventName.PROTOCOL_REJECTED
            } else {
                P2pDiagnosticEventName.ADMISSION_REJECTED
            },
            P2pDiagnosticRole.HOST,
            P2pDiagnosticResult.REJECTED,
            reason.toDiagnosticReason(),
        )
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
        diagnostics.event(
            P2pDiagnosticEventName.LIFECYCLE_SUSPENDED,
            P2pDiagnosticRole.HOST,
        )
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
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_FAILED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.FAILURE,
                P2pDiagnosticReason.LIFECYCLE,
            )
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
        diagnostics.event(
            P2pDiagnosticEventName.LIFECYCLE_RESUME_STARTED,
            P2pDiagnosticRole.HOST,
        )
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
        if (expiryJob != null) {
            diagnostics.event(
                P2pDiagnosticEventName.LIFECYCLE_RESUMED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.SUCCESS,
            )
        }
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
        if (shouldLeave) {
            diagnostics.event(
                P2pDiagnosticEventName.LIFECYCLE_EXPIRED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.TIMEOUT,
                P2pDiagnosticReason.LIFECYCLE,
            )
            leave()
        }
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
                credentialsByPlayer.remove(playerId)?.wipe()
                rejoinDeadlineByPlayer.remove(playerId)
                admissionReadyByPlayer.remove(playerId)?.signal?.complete(Unit)
                resumeReadyByPlayer.remove(playerId)?.signal?.complete(Unit)
                publishMembers()
                true
            }
        }
        if (!removed) {
            return
        }
        diagnostics.event(
            P2pDiagnosticEventName.CONNECTION_CLOSED,
            P2pDiagnosticRole.HOST,
            reason = P2pDiagnosticReason.DISCONNECTED,
        )
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
        val result = runCatching {
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
        if (result is Result.Success) {
            when (message) {
                is HostMessage.CommandResult ->
                    diagnostics.recordCommandResult(message.status, P2pDiagnosticRole.HOST)
                is HostMessage.PlayerSnapshot -> diagnostics.event(
                    P2pDiagnosticEventName.SNAPSHOT_SENT,
                    P2pDiagnosticRole.HOST,
                    P2pDiagnosticResult.SUCCESS,
                )
                else -> Unit
            }
        }
        return result
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
            return
        }
        expiryJob?.cancel()
        diagnostics.event(
            P2pDiagnosticEventName.CLEANUP_STARTED,
            P2pDiagnosticRole.HOST,
            count = diagnosticCount(stateMutex.withLock { trackedSessions.size }),
        )
        // Stop advertising FIRST and give Bonjour a beat to actually push
        // the "service-removed" packet before the kit goes down. Skipping
        // this is the root cause of dead rooms lingering in remote join
        // lobbies for the entire Bonjour eviction window (5–30s on iOS).
        runCatching { kit.stopAdvertising() }.onFailure {
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_FAILED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.FAILURE,
                P2pDiagnosticReason.TRANSPORT,
            )
        }
        delay(P2pKitRoomTransport.BONJOUR_GOODBYE_FLUSH_MS)
        sessionSupervisor.cancelAndJoin()
        val toClose = stateMutex.withLock {
            val sessions = trackedSessions.toList()
            sessionsByPlayer.clear()
            pendingByPlayer.clear()
            trackedSessions.clear()
            admissionReservations.clear()
            membersByPlayer.clear()
            previouslySeenPlayerIds.clear()
            credentialsByPlayer.values.forEach(HostCredential::wipe)
            credentialsByPlayer.clear()
            rejoinDeadlineByPlayer.clear()
            admissionReadyByPlayer.values.forEach { it.signal.complete(Unit) }
            admissionReadyByPlayer.clear()
            resumeReadyByPlayer.values.forEach { it.signal.complete(Unit) }
            resumeReadyByPlayer.clear()
            publishMembers()
            publishPendingAdmissions()
            sessions
        }
        toClose.forEach { runCatching { it.close() } }
        // kit.stop() is terminal; guard it so a late/duplicate teardown can't
        // throw out of a disposal path. See PROBLEMS_PARLOR.md → p2p-016.
        runCatching { kit.stop() }.onFailure {
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_FAILED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.FAILURE,
                P2pDiagnosticReason.TRANSPORT,
            )
        }
        if (_lifecycle.value != RoomLifecycleState.Expired) {
            _lifecycle.value = RoomLifecycleState.Closed
        }
        incomingPeerMessages.close()
        onClosed()
        diagnostics.event(
            P2pDiagnosticEventName.CLEANUP_COMPLETED,
            P2pDiagnosticRole.HOST,
            P2pDiagnosticResult.SUCCESS,
        )
    }
}

internal data class ResumedPeerConnection(
    val session: P2pSession,
    val hostPeer: Peer,
    val credential: ResumableSessionCredential,
)

// ============================================================================ Peer room ==

internal class PeerP2pRoom(
    private val kit: P2pKit,
    session: P2pSession,
    hostPeer: Peer,
    private val roomCode: String,
    private val scope: CoroutineScope,
    private val codec: RoomMessageCodec,
    private val diagnostics: P2pDiagnostics = NoOpP2pDiagnostics,
    initialCredential: ResumableSessionCredential? = null,
    private val credentialStore: ResumableCredentialStore? = null,
    private val resumeConnector: (
        suspend (ResumableSessionCredential) -> Result<ResumedPeerConnection, NetError>
    )? = null,
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

    private val incomingHostMessages = Channel<RoomMessage>(
        capacity = P2pTrafficLimits.PEER_APPLICATION_QUEUE_CAPACITY,
    )
    override val incoming: Flow<RoomMessage> = incomingHostMessages.receiveAsFlow()

    private val hostPlayerId: PlayerId = PlayerId(hostPeer.id.value)
    private val activeCredential = MutableStateFlow(initialCredential)
    override val rejoinToken: String?
        get() = null

    // p2p-016 (peer side): leave() runs from both a "Leave" tap and onDispose,
    // and kit.stop() is terminal — guard so the duplicate call is a no-op.
    private var left = false
    private val lifecycleMutex = Mutex()
    private val sessionMutex = Mutex()
    private var lifecycleExpiryJob: Job? = null
    private var resumeJob: Job? = null
    private var activeSession: P2pSession = session
    private var collectorJob: Job = launchIncomingCollector(session)
    private var stateJob: Job = launchSessionStateCollector(session)

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
            diagnostics.event(
                P2pDiagnosticEventName.LIFECYCLE_SUSPENDED,
                P2pDiagnosticRole.PEER,
            )
            val interruptedResume = lifecycleMutex.withLock {
                resumeJob.also { resumeJob = null }
            }
            interruptedResume?.cancelAndJoin()
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
        diagnostics.event(
            P2pDiagnosticEventName.LIFECYCLE_RESUME_STARTED,
            P2pDiagnosticRole.PEER,
        )
        scheduleLifecycleExpiry(deadline, deadline - atEpochMillis)
        kit.notifyAppForegrounded()
        resumeAfterForeground(deadline)
    }

    private suspend fun resumeAfterForeground(deadline: Long) {
        val session = sessionMutex.withLock { activeSession }
        if (session.state.value == ConnectionState.Connected) {
            restoreActiveRoom(emitEvent = true)
            return
        }
        val connector = resumeConnector ?: return
        val credential = activeCredential.value ?: return
        val job = scope.launch(start = CoroutineStart.LAZY) {
                var candidate = credential
                while (
                    lifecycleMutex.withLock {
                        !left &&
                            (_lifecycle.value as? RoomLifecycleState.Resuming)
                                ?.resumeDeadlineEpochMillis == deadline
                    }
                ) {
                    when (val resumed = connector(candidate)) {
                        is Result.Success -> {
                            candidate = resumed.data.credential
                            activeCredential.value = candidate
                            if (replaceSession(resumed.data)) {
                                restoreActiveRoom(emitEvent = true)
                                return@launch
                            }
                        }
                        is Result.Failure -> {
                            if (
                                resumed.error == NetError.RejoinExpired ||
                                resumed.error == NetError.Unauthorized
                            ) {
                                expireLifecycle(deadline)
                                return@launch
                            }
                        }
                    }
                    delay(P2pKitRoomTransport.ADMISSION_RETRY_MS)
                }
        }
        val accepted = lifecycleMutex.withLock {
            if (left || resumeJob?.isActive == true) {
                false
            } else {
                resumeJob = job
                true
            }
        }
        if (!accepted) {
            job.cancel()
            return
        }
        job.invokeOnCompletion {
            scope.launch {
                lifecycleMutex.withLock {
                    if (resumeJob === job) resumeJob = null
                }
            }
        }
        job.start()
    }

    private suspend fun replaceSession(resumed: ResumedPeerConnection): Boolean {
        val old = sessionMutex.withLock {
            if (left) return false
            val previous = Triple(activeSession, collectorJob, stateJob)
            activeSession = resumed.session
            collectorJob = launchIncomingCollector(resumed.session)
            stateJob = launchSessionStateCollector(resumed.session)
            previous
        }
        old.second.cancelAndJoin()
        old.third.cancelAndJoin()
        runCatching { old.first.close() }
        return signalResumeReady(resumed)
    }

    internal suspend fun finishInitialResumeHandoff(
        resumed: ResumedPeerConnection,
    ): Boolean = signalResumeReady(resumed)

    internal suspend fun finishInitialAdmissionHandoff(
        credential: ResumableSessionCredential,
    ): Boolean {
        val session = sessionMutex.withLock { activeSession }
        try {
            session.send(
                P2pMessage.Binary(
                    codec.encode(
                        PeerMessage.AdmissionReady(
                            actor = selfPlayerId,
                            offerId = credential.offerId,
                            generation = credential.generation,
                        ),
                    ),
                ),
            )
        } catch (failure: Throwable) {
            failure.rethrowIfCancellation()
            runCatching { session.close() }
            return false
        }
        try {
            session.send(
                P2pMessage.Binary(
                    codec.encode(
                        PeerMessage.AdmissionCommitAck(
                            actor = selfPlayerId,
                            offerId = credential.offerId,
                            generation = credential.generation,
                        ),
                    ),
                ),
            )
        } catch (failure: Throwable) {
            failure.rethrowIfCancellation()
        }
        return true
    }

    internal suspend fun abandonFailedResume() {
        leave(sendNotice = false, clearCredential = false)
    }

    private suspend fun signalResumeReady(resumed: ResumedPeerConnection): Boolean {
        val credential = resumed.credential
        try {
            val ready = PeerMessage.ResumeReady(
                actor = selfPlayerId,
                offerId = credential.offerId,
                generation = credential.generation,
            )
            resumed.session.send(P2pMessage.Binary(codec.encode(ready)))
        } catch (failure: Throwable) {
            failure.rethrowIfCancellation()
            runCatching { resumed.session.close() }
            return false
        }
        try {
            val acknowledgement = PeerMessage.ResumeCommitAck(
                actor = selfPlayerId,
                offerId = credential.offerId,
                generation = credential.generation,
            )
            resumed.session.send(P2pMessage.Binary(codec.encode(acknowledgement)))
        } catch (failure: Throwable) {
            failure.rethrowIfCancellation()
            // Ready is the ordering barrier. A lost cleanup acknowledgement
            // leaves one prior generation valid until the next successful
            // rotation, but does not make this attached session unsafe.
        }
        return true
    }

    private suspend fun restoreActiveRoom(emitEvent: Boolean) {
        val restored = lifecycleMutex.withLock {
            if (left || _lifecycle.value == RoomLifecycleState.Expired) {
                false
            } else {
                lifecycleExpiryJob?.cancel()
                lifecycleExpiryJob = null
                _lifecycle.value = RoomLifecycleState.Active
                true
            }
        }
        if (restored) {
            diagnostics.event(
                P2pDiagnosticEventName.LIFECYCLE_RESUMED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.SUCCESS,
            )
            markHostConnected(true)
            _info.value = _info.value.copy(status = RoomInfo.Status.Joined)
            if (emitEvent) _peerEvents.emit(PeerEvent.HostRestored)
        }
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
        if (shouldLeave) {
            diagnostics.event(
                P2pDiagnosticEventName.LIFECYCLE_EXPIRED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.TIMEOUT,
                P2pDiagnosticReason.LIFECYCLE,
            )
            leave(sendNotice = false)
        }
    }

    private fun launchIncomingCollector(session: P2pSession): Job = scope.launch {
        val trafficGuard = InboundTrafficGuard(
            maxFrameBytes = P2pTrafficLimits.MAX_HOST_TO_PEER_FRAME_BYTES,
            nowMillis = nowMillis(),
        )
        session.incoming.collect { msg ->
            if (msg !is P2pMessage.Binary) {
                enforceTrafficDecision(
                    trafficGuard.malformedFrame(nowMillis()),
                    session,
                    P2pDiagnosticReason.MALFORMED,
                )
                return@collect
            }
            if (
                !enforceTrafficDecision(
                    trafficGuard.inspectFrame(msg.bytes.size, nowMillis()),
                    session,
                    P2pDiagnosticReason.RATE_LIMIT,
                )
            ) {
                return@collect
            }
            // p2p-002: a malformed host frame must not cancel this collector
            // (which would permanently sever the peer's inbound stream).
            val decoded = runCatching {
                codec.decode(msg.bytes)
            }.getOrElse {
                enforceTrafficDecision(
                    trafficGuard.malformedFrame(nowMillis()),
                    session,
                    P2pDiagnosticReason.MALFORMED,
                )
                return@collect
            }
            if (decoded !is HostMessage) {
                enforceTrafficDecision(
                    trafficGuard.malformedFrame(nowMillis()),
                    session,
                    P2pDiagnosticReason.WRONG_DIRECTION,
                )
                return@collect
            }
            when (decoded) {
                is HostMessage.AdmissionAccepted,
                is HostMessage.AdmissionOffered,
                is HostMessage.AdmissionPending,
                is HostMessage.AdmissionCommitted,
                is HostMessage.ResumeOffered,
                is HostMessage.ResumeCommitted,
                is HostMessage.AdmissionRejected -> Unit
                else -> {
                    when (decoded) {
                        is HostMessage.CommandResult ->
                            diagnostics.recordCommandResult(decoded.status, P2pDiagnosticRole.PEER)
                        is HostMessage.PlayerSnapshot -> diagnostics.event(
                            P2pDiagnosticEventName.SNAPSHOT_RECEIVED,
                            P2pDiagnosticRole.PEER,
                            P2pDiagnosticResult.SUCCESS,
                        )
                        else -> Unit
                    }
                    incomingHostMessages.send(decoded)
                }
            }
        }
    }

    private suspend fun enforceTrafficDecision(
        decision: InboundTrafficDecision,
        session: P2pSession,
        reason: P2pDiagnosticReason,
    ): Boolean = when (decision) {
        InboundTrafficDecision.Accept -> true
        InboundTrafficDecision.Drop -> {
            diagnostics.event(
                P2pDiagnosticEventName.FRAME_DROPPED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.REJECTED,
                reason,
            )
            false
        }
        InboundTrafficDecision.Disconnect -> {
            diagnostics.event(
                P2pDiagnosticEventName.PEER_RATE_LIMITED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.REJECTED,
                reason,
            )
            try {
                session.close()
            } catch (failure: Throwable) {
                failure.rethrowIfCancellation()
            }
            false
        }
    }

    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun launchSessionStateCollector(session: P2pSession): Job = scope.launch {
        // The initial state emission for an already-Connected session must
        // not be reported as HostRestored; gate on whether we've previously
        // entered a lost state.
        var hostLost = false
        session.state.collect { state ->
            when (state) {
                ConnectionState.Reconnecting,
                ConnectionState.Failed -> {
                    if (!hostLost) {
                        hostLost = true
                        markHostConnected(false)
                        _info.value = _info.value.copy(status = RoomInfo.Status.Lost)
                        diagnostics.event(
                            P2pDiagnosticEventName.CONNECTION_CLOSED,
                            P2pDiagnosticRole.PEER,
                            reason = P2pDiagnosticReason.DISCONNECTED,
                        )
                        _peerEvents.tryEmit(PeerEvent.HostLost)
                    }
                }
                ConnectionState.Connected -> {
                    if (hostLost) {
                        hostLost = false
                        restoreActiveRoom(emitEvent = false)
                        diagnostics.event(
                            P2pDiagnosticEventName.CONNECTION_SECURE,
                            P2pDiagnosticRole.PEER,
                            P2pDiagnosticResult.SUCCESS,
                        )
                        _peerEvents.tryEmit(PeerEvent.HostRestored)
                    }
                }
                ConnectionState.Closed -> {
                    if (!hostLost) {
                        hostLost = true
                        markHostConnected(false)
                        _info.value = _info.value.copy(status = RoomInfo.Status.Lost)
                        diagnostics.event(
                            P2pDiagnosticEventName.CONNECTION_CLOSED,
                            P2pDiagnosticRole.PEER,
                            reason = P2pDiagnosticReason.DISCONNECTED,
                        )
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

    /** A peer cannot author a [HostMessage]. */
    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> =
        Result.Failure(NetError.Unauthorized)

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
        val session = sessionMutex.withLock { activeSession }
        if (session.state.value != ConnectionState.Connected) {
            return Result.Failure(NetError.NotConnected)
        }
        val bytes = try {
            codec.encode(message)
        } catch (_: IllegalArgumentException) {
            return Result.Failure(NetError.PayloadTooLarge)
        }
        val result = runCatching {
            val payload = P2pMessage.Binary(bytes)
            session.send(payload)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = {
                it.rethrowIfCancellation()
                Result.Failure(NetError.TransportFailure(it.message ?: "send failed"))
            },
        )
        if (result is Result.Success && message is PeerMessage.ClientCommand) {
            diagnostics.event(
                P2pDiagnosticEventName.COMMAND_SENT,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.SUCCESS,
            )
        }
        return result
    }

    override suspend fun leave() = leave(sendNotice = true)

    private suspend fun leave(
        sendNotice: Boolean,
        clearCredential: Boolean = true,
    ) {
        val (shouldLeave, jobs) = lifecycleMutex.withLock {
            if (left) false to emptyList() else {
                left = true
                val toCancel = listOfNotNull(lifecycleExpiryJob, resumeJob)
                lifecycleExpiryJob = null
                resumeJob = null
                true to toCancel
            }
        }
        if (!shouldLeave) {
            return
        }
        jobs.forEach { job ->
            if (job != kotlinx.coroutines.currentCoroutineContext()[Job]) {
                job.cancel()
            }
        }
        val session = sessionMutex.withLock { activeSession }
        diagnostics.event(
            P2pDiagnosticEventName.CLEANUP_STARTED,
            P2pDiagnosticRole.PEER,
            count = P2pDiagnosticCountBucket.ONE,
        )
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
                // Tiny window for the bytes to actually flush across the
                // wire before close() yanks the socket out from under
                // them. See P2pKitRoomTransport.LEAVE_NOTICE_FLUSH_MS.
                delay(P2pKitRoomTransport.LEAVE_NOTICE_FLUSH_MS)
            }
        }
        collectorJob.cancelAndJoin()
        stateJob.cancelAndJoin()
        if (clearCredential) {
            credentialStore?.clear()
            activeCredential.value = null
        }
        runCatching { session.close() }.onFailure {
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                P2pDiagnosticReason.TRANSPORT,
            )
        }
        // kit.stop() is terminal — guard against a duplicate/late teardown.
        runCatching { kit.stop() }.onFailure {
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                P2pDiagnosticReason.TRANSPORT,
            )
        }
        if (_lifecycle.value != RoomLifecycleState.Expired) {
            _lifecycle.value = RoomLifecycleState.Closed
        }
        incomingHostMessages.close()
        onClosed()
        diagnostics.event(
            P2pDiagnosticEventName.CLEANUP_COMPLETED,
            P2pDiagnosticRole.PEER,
            P2pDiagnosticResult.SUCCESS,
        )
    }
}
