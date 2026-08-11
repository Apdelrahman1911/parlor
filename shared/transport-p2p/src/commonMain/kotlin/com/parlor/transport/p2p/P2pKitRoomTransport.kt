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
import com.parlor.networking.room.RoomInputPolicy
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

internal interface AppLifecycleAwareRoom {
    suspend fun appBackgrounded(atEpochMillis: Long)
    suspend fun appForegrounded(atEpochMillis: Long)
}

private const val REJOIN_SECRET_HEX_LENGTH: Int = 64

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
@Suppress("LargeClass") // Cohesive single-owner transport lifecycle; protocol branches are isolated and race-tested.
class P2pKitRoomTransport @Suppress("LongParameterList") private constructor(
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
    // production budget (30s). Production wiring uses the default.
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
                    }
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
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

    private suspend fun roomClosed(registrationId: String) {
        // Ownership teardown is terminal state, not a best-effort lifecycle
        // hint. It must never share the DROP_OLDEST signal queue above: a
        // foreground/background burst could otherwise evict this removal and
        // retain a closed room until another room happened to register.
        activeLifecycleMutex.withLock {
            if (activeLifecycleRoom?.registrationId == registrationId) {
                activeLifecycleRoom = null
            }
        }
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
        val roomDisplayName = RoomInputPolicy.normalizeDisplayName(config.roomDisplayName)
        if (!RoomInputPolicy.isValidDisplayName(roomDisplayName)) {
            return Result.Failure(NetError.InvalidInput)
        }
        _localNetworkAccess.value = LocalNetworkAccess.Attempting
        diagnostics.event(P2pDiagnosticEventName.SESSION_CREATE_STARTED, P2pDiagnosticRole.HOST)
        return try {
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
            var initializationComplete = false
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
                    roomDisplayName = roomDisplayName,
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
                initializationComplete = true
            } finally {
                if (!initializationComplete) withContext(NonCancellable) {
                    if (room == null) {
                        kit.stopAfterFailure(diagnostics)
                    } else {
                        attemptCleanup(
                            diagnostics,
                            P2pDiagnosticRole.HOST,
                            preserveCancellation = false,
                        ) { room.leave() }
                    }
                }
            }
            val hostedRoom = checkNotNull(room)
            registerLifecycleRoom(lifecycleRegistrationId, hostedRoom)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_SUCCEEDED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.SUCCESS,
            )
            Result.Success(hostedRoom)
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            recordLocalNetworkFailure(failure)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.FAILURE,
                failure.toDiagnosticReason(),
            )
            Result.Failure(NetError.TransportFailure(failure.message ?: "host failed"))
        }
    }

    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> {
        return join(JoinConfig(code = code, displayName = displayName))
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    override suspend fun join(config: JoinConfig): Result<LocalRoom, NetError> {
        if (config.rejoinToken != null) {
            return Result.Failure(NetError.Unauthorized)
        }
        val normalizedCode = config.code.trim().uppercase()
        val normalizedName = RoomInputPolicy.normalizeDisplayName(config.displayName)
        if (
            !RoomInputPolicy.isValidRoomCode(normalizedCode) ||
            !RoomInputPolicy.isValidDisplayName(normalizedName)
        ) {
            return Result.Failure(NetError.InvalidInput)
        }
        val effectiveConfig = config.copy(
            code = normalizedCode,
            displayName = normalizedName,
        )
        _localNetworkAccess.value = LocalNetworkAccess.Attempting
        diagnostics.event(P2pDiagnosticEventName.SESSION_CREATE_STARTED, P2pDiagnosticRole.PEER)
        val kit = try {
            kitFactory.createKit(appId = appId, deviceName = effectiveConfig.displayName)
        } catch (@Suppress("TooGenericExceptionCaught") t: Exception) {
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
            var initializationComplete = false
            try {
                kit.start()
                kit.startDiscovery()
                diagnostics.event(P2pDiagnosticEventName.DISCOVERY_STARTED, P2pDiagnosticRole.PEER)
                initializationComplete = true
            } finally {
                if (!initializationComplete) kit.stopAfterFailure(diagnostics)
            }
        } catch (@Suppress("TooGenericExceptionCaught") t: Exception) {
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
        var keepKitRunning = false
        return try {
            val startedAt = TimeSource.Monotonic.markNow()
            val scheduler = DiscoveryCandidateScheduler(
                totalDeadlineMs = joinTimeoutMs,
                perAttemptTimeoutMs = DIAL_AND_HANDSHAKE_TIMEOUT_MS,
            )
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
                val scheduledAttempt = scheduler.next(elapsedMs)
                if (scheduledAttempt == null) {
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
                val candidate = scheduledAttempt.candidate
                val hostPeer = visiblePeers.firstOrNull {
                    it.id.value == candidate.key && it.endpointVersion() == candidate.endpointVersion
                }
                if (hostPeer == null) {
                    scheduler.recordResult(
                        scheduledAttempt,
                        DiscoveryAttemptResult.TransientFailure,
                        elapsedMs,
                    )
                    continue
                }
                val remainingDialBudgetMs = scheduledAttempt.deadlineAtMs -
                    startedAt.elapsedNow().inWholeMilliseconds
                if (remainingDialBudgetMs <= 0L) {
                    scheduler.recordResult(
                        scheduledAttempt,
                        DiscoveryAttemptResult.TransientFailure,
                        startedAt.elapsedNow().inWholeMilliseconds,
                    )
                    continue
                }
                diagnostics.event(
                    P2pDiagnosticEventName.DISCOVERY_ATTEMPTED,
                    P2pDiagnosticRole.PEER,
                )
                val session = try {
                    withTimeoutOrNull(remainingDialBudgetMs) {
                        kit.connect(hostPeer)
                    }
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
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
                        scheduledAttempt,
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
                    attemptCleanup(diagnostics, P2pDiagnosticRole.PEER) { session.close() }
                    break
                }
                when (
                    val admission = awaitAdmission(
                        session = session,
                        hostPeer = hostPeer,
                        code = effectiveConfig.code,
                        displayName = effectiveConfig.displayName,
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
                            roomCode = effectiveConfig.code,
                            initialCredential = admission.credential,
                            credentialStore = credentialStore,
                            resumeConnector = { credential ->
                                resumeConnectionDetailed(kit, credential)
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
                        attemptCleanup(diagnostics, P2pDiagnosticRole.PEER) { session.close() }
                        when (admission.reason) {
                            AdmissionRejection.WrongCode -> scheduler.recordResult(
                                scheduledAttempt,
                                DiscoveryAttemptResult.WrongRoom,
                                startedAt.elapsedNow().inWholeMilliseconds,
                            )
                            AdmissionRejection.IncompatibleProtocol -> scheduler.recordResult(
                                scheduledAttempt,
                                DiscoveryAttemptResult.IncompatibleProtocol,
                                startedAt.elapsedNow().inWholeMilliseconds,
                            )
                            else -> result = Result.Failure(admission.reason.toNetError())
                        }
                    }
                    is AdmissionOutcome.TransientFailure -> {
                        attemptCleanup(diagnostics, P2pDiagnosticRole.PEER) { session.close() }
                        scheduler.recordResult(
                            scheduledAttempt,
                            DiscoveryAttemptResult.TransientFailure,
                            startedAt.elapsedNow().inWholeMilliseconds,
                        )
                    }
                    is AdmissionOutcome.Failed -> {
                        attemptCleanup(diagnostics, P2pDiagnosticRole.PEER) { session.close() }
                        result = Result.Failure(admission.error)
                    }
                }
            }
            if (result == null) {
                result = Result.Failure(scheduler.finalError().toNetError())
            }
            if (result is Result.Failure) {
                attemptCleanup(diagnostics, P2pDiagnosticRole.PEER) { kit.stopDiscovery() }
                if (_localNetworkAccess.value != LocalNetworkAccess.Operational) {
                    _localNetworkAccess.value = LocalNetworkAccess.FailureUnclassified
                }
            }
            checkNotNull(result).also { completed ->
                when (completed) {
                    is Result.Success -> {
                        keepKitRunning = true
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
        } catch (@Suppress("TooGenericExceptionCaught") t: Exception) {
            t.rethrowIfCancellation()
            recordLocalNetworkFailure(t)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                t.toDiagnosticReason(),
            )
            Result.Failure(NetError.TransportFailure(t.message ?: "join failed"))
        } finally {
            if (!keepKitRunning) kit.stopAfterFailure(diagnostics)
        }
    }

    override suspend fun resumableSession(): Result<ResumableSessionInfo?, NetError> =
        when (val loaded = credentialStore.loadResumeCandidate()) {
            is Result.Failure -> Result.Failure(NetError.SecureStorageUnavailable)
            is Result.Success -> {
                val credential = loaded.data ?: return Result.Success(null)
                if (credential.expiresAtEpochMillis <= nowMillis()) {
                    when (
                        invalidateStoredCredential(
                            credential,
                            P2pDiagnosticReason.LIFECYCLE,
                        )
                    ) {
                        is Result.Success -> Result.Success(null)
                        is Result.Failure -> Result.Failure(NetError.SecureStorageUnavailable)
                    }
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

    @Suppress("LongMethod") // One transactional resume path owns kit cleanup on every exit.
    override suspend fun resumeLastSession(): Result<LocalRoom, NetError> {
        val credential = when (val loaded = credentialStore.loadResumeCandidate()) {
            is Result.Failure -> return Result.Failure(NetError.SecureStorageUnavailable)
            is Result.Success -> loaded.data ?: return Result.Failure(NetError.NotConnected)
        }
        if (credential.expiresAtEpochMillis <= nowMillis()) {
            if (
                invalidateStoredCredential(
                    credential,
                    P2pDiagnosticReason.LIFECYCLE,
                ) is Result.Failure
            ) {
                return Result.Failure(NetError.SecureStorageUnavailable)
            }
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
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
            var initializationComplete = false
            try {
                kit.start()
                initializationComplete = true
            } finally {
                if (!initializationComplete) kit.stopAfterFailure(diagnostics)
            }
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
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
        var keepKitRunning = false
        return try {
            when (val resumed = resumeConnectionDetailed(kit, credential)) {
                is Result.Failure -> {
                    val resumeError = resumed.error.error
                    if (_localNetworkAccess.value != LocalNetworkAccess.Operational) {
                        _localNetworkAccess.value = LocalNetworkAccess.FailureUnclassified
                    }
                    if (resumed.error.invalidatesCredential) {
                        val reason = if (resumeError == NetError.Unauthorized) {
                            P2pDiagnosticReason.UNAUTHORIZED
                        } else {
                            P2pDiagnosticReason.LIFECYCLE
                        }
                        if (invalidateStoredCredential(credential, reason) is Result.Failure) {
                            return Result.Failure(NetError.SecureStorageUnavailable)
                        }
                    }
                    diagnostics.event(
                        if (resumeError == NetError.RejoinExpired) {
                            P2pDiagnosticEventName.LIFECYCLE_EXPIRED
                        } else {
                            P2pDiagnosticEventName.SESSION_CREATE_FAILED
                        },
                        P2pDiagnosticRole.PEER,
                        resumeError.toDiagnosticResult(),
                        resumeError.toDiagnosticReason(),
                    )
                    Result.Failure(resumeError)
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
                        resumeConnector = { next -> resumeConnectionDetailed(kit, next) },
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
                        keepKitRunning = true
                        Result.Success(room)
                    }
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            recordLocalNetworkFailure(failure)
            diagnostics.event(
                P2pDiagnosticEventName.SESSION_CREATE_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                failure.toDiagnosticReason(),
            )
            Result.Failure(NetError.TransportFailure(failure.message ?: "resume failed"))
        } finally {
            if (!keepKitRunning) kit.stopAfterFailure(diagnostics)
        }
    }

    /** Invalidates one loaded capability without allowing a stale caller to clear a replacement. */
    private suspend fun invalidateStoredCredential(
        credential: ResumableSessionCredential,
        reason: P2pDiagnosticReason,
    ): Result<CredentialInvalidationResult, NetError> {
        val invalidated = try {
            credentialStore.invalidateOwned(credential)
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            diagnostics.event(
                P2pDiagnosticEventName.CREDENTIAL_INVALIDATION_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                P2pDiagnosticReason.INTERNAL,
            )
            return Result.Failure(NetError.SecureStorageUnavailable)
        }
        return when (invalidated) {
            is Result.Success -> {
                diagnostics.event(
                    P2pDiagnosticEventName.CREDENTIAL_INVALIDATED,
                    P2pDiagnosticRole.PEER,
                    if (invalidated.data == CredentialInvalidationResult.Invalidated) {
                        P2pDiagnosticResult.SUCCESS
                    } else {
                        P2pDiagnosticResult.DUPLICATE
                    },
                    reason,
                )
                invalidated
            }
            is Result.Failure -> {
                diagnostics.event(
                    P2pDiagnosticEventName.CREDENTIAL_INVALIDATION_FAILED,
                    P2pDiagnosticRole.PEER,
                    P2pDiagnosticResult.FAILURE,
                    P2pDiagnosticReason.INTERNAL,
                )
                Result.Failure(NetError.SecureStorageUnavailable)
            }
        }
    }

    private sealed interface AdmissionOutcome {
        data class Accepted(val credential: ResumableSessionCredential) : AdmissionOutcome
        data class Rejected(val reason: AdmissionRejection) : AdmissionOutcome
        data class TransientFailure(val error: NetError) : AdmissionOutcome
        data class Failed(val error: NetError) : AdmissionOutcome
    }

    @Suppress(
        "LongMethod",
        // Intentional one-way compatibility: a protocol-0 acceptance receives
        // an explicit IncompatibleProtocol result instead of timing out.
        "DEPRECATION",
    )
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
                    val decoded = try {
                        codec.decode(frame.bytes)
                    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                        failure.rethrowIfCancellation()
                        return@collect
                    } as? HostMessage ?: return@collect
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
            val first = sendUntilResponse(
                session = session,
                responses = responses,
                message = PeerMessage.AdmissionRequest(
                    protocol = ProtocolVersion(),
                    actor = selfPlayerId,
                    roomCode = code,
                    displayName = displayName,
                ),
                timeoutMs = firstResponseTimeoutMs,
                accepts = { response ->
                    when (response) {
                        is HostMessage.AdmissionPending ->
                            response.playerId == selfPlayerId
                        is HostMessage.AdmissionOffered ->
                            response.offer.playerId == selfPlayerId
                        is HostMessage.AdmissionAccepted ->
                            response.playerId == selfPlayerId
                        // Rejections are terminal for this physical session:
                        // the host flushes the frame and immediately closes it.
                        is HostMessage.AdmissionRejected -> true
                        else -> false
                    }
                },
            ) ?: return@coroutineScope AdmissionOutcome.TransientFailure(NetError.Timeout)
            val decision = if (first is HostMessage.AdmissionPending) {
                if (first.playerId != selfPlayerId) {
                    return@coroutineScope AdmissionOutcome.Rejected(
                        AdmissionRejection.InvalidCredential,
                    )
                }
                withTimeoutOrNull(HOST_APPROVAL_TIMEOUT_MS) {
                    responses.receiveMatching { response ->
                        when (response) {
                            is HostMessage.AdmissionOffered ->
                                response.offer.playerId == selfPlayerId
                            is HostMessage.AdmissionAccepted ->
                                response.playerId == selfPlayerId
                            is HostMessage.AdmissionRejected -> true
                            // AdmissionPending is a duplicate of the current
                            // phase; AdmissionCommitted belongs to a later
                            // offer/generation and must not poison this wait.
                            else -> false
                        }
                    }
                } ?: return@coroutineScope AdmissionOutcome.Failed(NetError.Timeout)
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
                        accepts = { response ->
                            when (response) {
                                is HostMessage.AdmissionCommitted ->
                                    response.playerId == selfPlayerId &&
                                        response.offerId == credential.offerId &&
                                        response.generation == credential.generation
                                // The rejection has no correlation fields in the
                                // current wire schema, but is terminal and is
                                // followed by closure of this physical session.
                                is HostMessage.AdmissionRejected -> true
                                else -> false
                            }
                        },
                    ) ?: run {
                        credentialStore.discardPending(credential.offerId)
                        return@coroutineScope AdmissionOutcome.Failed(NetError.Timeout)
                    }
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
            withContext(NonCancellable) {
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.PEER,
                    preserveCancellation = false,
                ) { collector.cancelAndJoin() }
                responses.close()
            }
        }
    }

    private suspend fun sendUntilResponse(
        session: P2pSession,
        responses: Channel<HostMessage>,
        message: PeerMessage,
        timeoutMs: Long,
        accepts: (HostMessage) -> Boolean,
    ): HostMessage? = withTimeoutOrNull(timeoutMs) {
        var response: HostMessage? = null
        while (response == null) {
            sendRoomMessage(session, message)
            response = withTimeoutOrNull(ADMISSION_RETRY_MS) {
                responses.receiveMatching(accepts)
            }
        }
        response
    }

    private suspend inline fun Channel<HostMessage>.receiveMatching(
        crossinline accepts: (HostMessage) -> Boolean,
    ): HostMessage {
        while (true) {
            val response = receive()
            if (accepts(response)) return response
        }
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
    private suspend fun resumeConnectionDetailed(
        kit: P2pKit,
        credential: ResumableSessionCredential,
    ): Result<ResumedPeerConnection, ResumeConnectionFailure> {
        if (credential.expiresAtEpochMillis <= nowMillis()) {
            return Result.Failure(
                ResumeConnectionFailure(NetError.RejoinExpired, invalidatesCredential = true),
            )
        }
        val expectedFingerprint = try {
            PeerFingerprint(credential.hostFingerprint)
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            return Result.Failure(
                ResumeConnectionFailure(NetError.Unauthorized, invalidatesCredential = true),
            )
        }
        return try {
            kit.startDiscovery()
            diagnostics.event(P2pDiagnosticEventName.DISCOVERY_STARTED, P2pDiagnosticRole.PEER)
            withTimeoutOrNull(REJOIN_GRACE_MS) {
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
                    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
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
                        attemptCleanup(diagnostics, P2pDiagnosticRole.PEER) { session.close() }
                        return@withTimeoutOrNull Result.Failure(
                            ResumeConnectionFailure(NetError.Unauthorized),
                        )
                    }
                    diagnostics.event(
                        P2pDiagnosticEventName.CONNECTION_SECURE,
                        P2pDiagnosticRole.PEER,
                        P2pDiagnosticResult.SUCCESS,
                    )
                    when (val resumed = awaitResume(session, hostPeer, credential)) {
                        is Result.Success -> return@withTimeoutOrNull resumed
                        is Result.Failure -> {
                            attemptCleanup(diagnostics, P2pDiagnosticRole.PEER) { session.close() }
                            if (
                                resumed.error == NetError.Unauthorized ||
                                resumed.error == NetError.RejoinExpired ||
                                resumed.error == NetError.IncompatibleProtocol ||
                                resumed.error == NetError.AlreadyConnected
                            ) {
                                return@withTimeoutOrNull Result.Failure(
                                    ResumeConnectionFailure(
                                        error = resumed.error,
                                        invalidatesCredential =
                                            resumed.error == NetError.Unauthorized ||
                                                resumed.error == NetError.RejoinExpired,
                                    ),
                                )
                            }
                            delay(ADMISSION_RETRY_MS)
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Result.Failure(ResumeConnectionFailure(NetError.Timeout))
            } ?: Result.Failure(ResumeConnectionFailure(NetError.Timeout))
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            Result.Failure(
                ResumeConnectionFailure(
                    NetError.TransportFailure(failure.message ?: "resume failed"),
                ),
            )
        } finally {
            withContext(NonCancellable) {
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.PEER,
                    preserveCancellation = false,
                ) { kit.stopDiscovery() }
            }
        }
    }

    @Suppress("LongMethod") // Ordered credential rotation handshake with rollback at each boundary.
    private suspend fun awaitResume(
        session: P2pSession,
        hostPeer: Peer,
        credential: ResumableSessionCredential,
    ): Result<ResumedPeerConnection, NetError> = coroutineScope {
        val responses = Channel<HostMessage>(capacity = 8)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.incoming.filterIsInstance<P2pMessage.Binary>().collect { frame ->
                if (frame.bytes.size > MAX_ROOM_FRAME_BYTES) return@collect
                val decoded = try {
                    codec.decode(frame.bytes)
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    failure.rethrowIfCancellation()
                    return@collect
                } as? HostMessage ?: return@collect
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
                accepts = { response ->
                    when (response) {
                        is HostMessage.ResumeOffered ->
                            response.offer.playerId.raw == credential.playerId
                        is HostMessage.AdmissionRejected -> true
                        else -> false
                    }
                },
            ) ?: return@coroutineScope Result.Failure(NetError.Timeout)
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
            val committed = sendUntilResponse(
                session = session,
                responses = responses,
                message = PeerMessage.ResumeConfirmed(
                    actor = PlayerId(rotated.playerId),
                    offerId = rotated.offerId,
                    generation = rotated.generation,
                ),
                timeoutMs = ADMISSION_CONFIRM_TIMEOUT_MS,
                accepts = { response ->
                    when (response) {
                        is HostMessage.ResumeCommitted ->
                            response.playerId.raw == rotated.playerId &&
                                response.offerId == rotated.offerId &&
                                response.generation == rotated.generation
                        is HostMessage.AdmissionRejected -> true
                        else -> false
                    }
                },
            ) ?: run {
                credentialStore.discardPending(rotated.offerId)
                return@coroutineScope Result.Failure(NetError.Timeout)
            }
            when (committed) {
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
            withContext(NonCancellable) {
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.PEER,
                    preserveCancellation = false,
                ) { collector.cancelAndJoin() }
                responses.close()
                val pending = stagedOfferId
                if (pending != null && !committedCredential) {
                    // On an interrupted handshake retain the last committed
                    // generation and remove only this staged rotation.
                    attemptCleanup(
                        diagnostics,
                        P2pDiagnosticRole.PEER,
                        preserveCancellation = false,
                    ) { credentialStore.discardPending(pending) }
                }
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
        val playerMatches = playerId == selfPlayerId
        val authenticatedHostMatches = hostPeer.id == session.peer.id &&
            hostPeer.id.value == hostPeerId &&
            authenticatedFingerprint == hostFingerprint
        val offerShapeMatches = generation == INITIAL_CREDENTIAL_GENERATION && gameFieldsMatch
        if (!playerMatches || !authenticatedHostMatches || !offerShapeMatches) {
            return null
        }
        return try {
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
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            null
        }
    }

    private fun ResumableCredentialOffer.toRotatedCredentialOrNull(
        session: P2pSession,
        hostPeer: Peer,
        current: ResumableSessionCredential,
    ): ResumableSessionCredential? {
        val authenticatedFingerprint = session.peerIdentity.fingerprint?.value ?: return null
        val playerMatches = playerId.raw == current.playerId
        val authenticatedHostMatches = hostPeer.id == session.peer.id &&
            hostPeer.id.value == current.hostPeerId &&
            authenticatedFingerprint == current.hostFingerprint
        val offeredHostMatches = hostPeerId == current.hostPeerId &&
            hostFingerprint == current.hostFingerprint
        val rotationMatches = generation > current.generation &&
            gameId == current.gameId &&
            gameVersion == current.gameVersion
        if (!playerMatches || !authenticatedHostMatches || !offeredHostMatches || !rotationMatches) {
            return null
        }
        return try {
            current.copy(
                offerId = offerId,
                secret = secret,
                generation = generation,
                issuedAtEpochMillis = issuedAtEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
            ).requireValid()
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            null
        }
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

    private fun generateRoomCode(): String = SecureIds.randomCharacters(
        length = RoomInputPolicy.ROOM_CODE_LENGTH,
        alphabet = RoomInputPolicy.ROOM_CODE_ALPHABET,
    )

    companion object {
        const val P2P_ROOM_PREFIX = "parlor-room|"
        /** Discovery, dial, secure handshake and first admission-state response budget. */
        internal const val DEFAULT_JOIN_TIMEOUT_MS: Long = 30_000L
        internal const val DIAL_AND_HANDSHAKE_TIMEOUT_MS: Long = 5_000L
        internal const val FIRST_ADMISSION_RESPONSE_TIMEOUT_MS: Long = 5_000L
        /** Authenticated sessions must begin admission before consuming a host slot indefinitely. */
        internal const val FIRST_APPLICATION_MESSAGE_TIMEOUT_MS: Long = 5_000L
        /** Starts only after a valid request receives AdmissionPending. */
        internal const val HOST_APPROVAL_TIMEOUT_MS: Long = 60_000L
        internal const val DISCOVERY_REFRESH_POLL_MS: Long = 1_000L
        internal const val ADMISSION_RETRY_MS: Long = 400L
        internal const val ADMISSION_REJECTION_FLUSH_MS: Long = 100L
        internal const val REJOIN_GRACE_MS: Long = 120_000L
        internal const val ADMISSION_CONFIRM_TIMEOUT_MS: Long = 60_000L
        internal const val CREDENTIAL_MAX_AGE_MS: Long = 24L * 60L * 60L * 1_000L
        internal const val INITIAL_CREDENTIAL_GENERATION: Long = 1L
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
    NetError.InvalidInput -> P2pDiagnosticReason.UNAUTHORIZED
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_FAILED,
                result = P2pDiagnosticResult.FAILURE,
                reason = failure.toDiagnosticReason(),
            )
        }
    }
}

/**
 * Runs one best-effort cleanup step without ever converting a VM/runtime
 * [Error] into an ordinary transport outcome.
 *
 * Outside a [NonCancellable] owner cleanup, cancellation remains structural
 * and is rethrown. Terminal owner cleanup may set [preserveCancellation] to
 * false because it records the failure and must finish releasing the rest of
 * the room before returning to its already-cancelled caller.
 */
private suspend inline fun attemptCleanup(
    diagnostics: P2pDiagnostics,
    role: P2pDiagnosticRole,
    preserveCancellation: Boolean = true,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        if (preserveCancellation) failure.rethrowIfCancellation()
        diagnostics.event(
            P2pDiagnosticEventName.CLEANUP_FAILED,
            role,
            P2pDiagnosticResult.FAILURE,
            failure.toDiagnosticReason(),
        )
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

@Suppress("LargeClass") // Single mutex owner for host membership/admission/resume transaction state.
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
    private val onClosed: suspend () -> Unit = {},
    private val appResumeGraceMs: Long = P2pKitRoomTransport.APP_RESUME_GRACE_MS,
    private val firstApplicationMessageTimeoutMs: Long =
        P2pKitRoomTransport.FIRST_APPLICATION_MESSAGE_TIMEOUT_MS,
) : LocalRoom, AppLifecycleAwareRoom {

    init {
        require(firstApplicationMessageTimeoutMs > 0L) {
            "firstApplicationMessageTimeoutMs must be positive"
        }
    }

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
    private data class RetiredMemberResources(
        val sessions: Set<P2pSession>,
        val credential: HostCredential?,
    )
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
    // throws IllegalStateException. Guard so leave() is idempotent. Every access
    // is serialized by stateMutex, so no cross-platform @Volatile is needed.
    private var left = false

    init {
        sessionScope.launch {
            kit.incomingSessions.collect { session ->
                handleIncomingSession(session)
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod") // Security handshake validates and dispatches every allowed first frame.
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
        val admissionHandshakeStarted = CompletableDeferred<Unit>()
        val firstApplicationMessageDeadlineJob = sessionScope.launch {
            val started = withTimeoutOrNull(firstApplicationMessageTimeoutMs) {
                admissionHandshakeStarted.await()
                true
            } ?: false
            if (started) return@launch

            val stillWaiting = stateMutex.withLock {
                session in trackedSessions &&
                    sessionsByPlayer[playerId] !== session &&
                    pendingByPlayer[playerId]?.session !== session
            }
            if (!stillWaiting) return@launch

            diagnostics.event(
                P2pDiagnosticEventName.ADMISSION_REJECTED,
                P2pDiagnosticRole.HOST,
                P2pDiagnosticResult.TIMEOUT,
                P2pDiagnosticReason.RATE_LIMIT,
            )
            try {
                session.close()
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                failure.rethrowIfCancellation()
            }
        }

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
                val rawDecoded = try {
                    codec.decode(msg.bytes)
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    failure.rethrowIfCancellation()
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
                // The actor identity is the AUTHENTICATED session peer id,
                // never a peer-authored actor field.
                // Overwrite the body field so the authority gate downstream can
                // only ever see who actually owns this connection — a peer can no
                // longer forge another player's vote/action by lying in the body.
                val decoded = when (rawDecoded) {
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
                    is PeerMessage.SessionStartReady -> rawDecoded.copy(actor = playerId)
                    is PeerMessage.SessionStartCommitAck -> rawDecoded.copy(actor = playerId)
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
                        is PeerMessage.AdmissionRequest -> {
                            admissionHandshakeStarted.complete(Unit)
                            handleAdmissionRequest(
                                playerId,
                                displayName,
                                peerFingerprint,
                                session,
                                decoded,
                            )
                        }
                        is PeerMessage.AdmissionConfirmed -> handleCredentialConfirmation(
                            playerId,
                            session,
                            decoded.offerId,
                            decoded.generation,
                            CredentialTransactionKind.Admission,
                        )
                        is PeerMessage.ResumeRequested -> {
                            admissionHandshakeStarted.complete(Unit)
                            handleResumeRequest(
                                playerId,
                                displayName,
                                peerFingerprint,
                                session,
                                decoded,
                            )
                        }
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
                        else -> rejectSession(session, AdmissionRejection.InvalidRequest)
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
            session.state.first { state ->
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
                        admissionHandshakeStarted.complete(Unit)
                        firstApplicationMessageDeadlineJob.cancel()
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
                // StateFlow is terminal-by-value rather than terminal-by-
                // completion. Returning true here releases this per-session
                // collector after its one terminal cleanup transaction; a
                // long-lived host must not retain one suspended job for every
                // session that has ever connected.
                state == ConnectionState.Closed || state == ConnectionState.Failed
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
            !RoomInputPolicy.isValidDisplayName(displayName) ||
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
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    failure.rethrowIfCancellation()
                    attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
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

    @Suppress("LongMethod", "CyclomaticComplexMethod") // Authenticated resume preparation is one fail-closed transaction.
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
        val protocolMatches = request.protocol.isCompatibleWith(ProtocolVersion())
        val roomMatches = request.roomCode == roomCode
        val displayNameMatches = RoomInputPolicy.isValidDisplayName(displayName) &&
            request.displayName.trim() == displayName
        val secretShapeMatches = request.secret.length == REJOIN_SECRET_HEX_LENGTH &&
            request.secret.none { it !in '0'..'9' && it !in 'a'..'f' }
        if (!protocolMatches || !roomMatches || !displayNameMatches || !secretShapeMatches) {
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
                        val previousGeneration = credential.previousGeneration
                        val previousDigest = credential.previousDigest
                        val matchedGeneration = when {
                            request.generation == credential.generation &&
                                SecureHashes.constantTimeEquals(
                                    providedDigest,
                                    credential.digest,
                                ) -> credential.generation
                            previousGeneration != null &&
                                previousDigest != null &&
                                request.generation == previousGeneration &&
                                SecureHashes.constantTimeEquals(
                                    providedDigest,
                                    previousDigest,
                                ) -> previousGeneration
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

    @Suppress("LongMethod") // Resume commit and rollback must remain one transaction owner.
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
                attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
                return
            }
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            withContext(NonCancellable) {
                rollbackAdmission(playerId, session)
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.HOST,
                    preserveCancellation = false,
                ) { session.close() }
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
            attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
            return
        }
        prepared.matchedDigest.fill(0)
        commit.previousSession?.let { previous ->
            attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { previous.close() }
        }
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            withContext(NonCancellable) {
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.HOST,
                    preserveCancellation = false,
                ) { session.close() }
            }
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
            attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
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

    override suspend fun closeAdmissions(): Result<List<RoomMember>, NetError> {
        data class FrozenRoster(
            val members: List<RoomMember>,
            val pending: List<PendingConnection>,
            val disconnectedSessions: List<P2pSession>,
            val discardedCredentials: List<HostCredential>,
        )

        val frozen = stateMutex.withLock {
            // An offered/committed credential is a transaction, not a lobby
            // seat. Starting through that transaction would either omit a
            // peer that was approved or include one that cannot yet receive
            // the first snapshot. Keep admissions open and make the caller
            // retry after the deterministic ready/rollback result.
            if (
                admissionReservations.isNotEmpty() ||
                admissionReadyByPlayer.isNotEmpty() ||
                resumeReadyByPlayer.isNotEmpty()
            ) {
                return@withLock null
            }

            admissionsClosed = true
            val pending = pendingByPlayer.values.toList()
            pendingByPlayer.clear()

            // A disconnected lobby member is not part of the frozen game
            // roster. Invalidate its capability atomically so it cannot rejoin
            // after gameplay starts as a seat the game never created.
            val disconnectedIds = membersByPlayer
                .filterValues { !it.connected }
                .keys
                .toList()
            val disconnectedSessions = disconnectedIds.mapNotNull(sessionsByPlayer::remove)
            val discardedCredentials = disconnectedIds.mapNotNull(credentialsByPlayer::remove)
            disconnectedIds.forEach { playerId ->
                membersByPlayer.remove(playerId)
                rejoinDeadlineByPlayer.remove(playerId)
            }

            publishPendingAdmissions()
            publishMembers()
            FrozenRoster(
                members = membersByPlayer.values.filter(RoomMember::connected),
                pending = pending,
                disconnectedSessions = disconnectedSessions,
                discardedCredentials = discardedCredentials,
            )
        } ?: return Result.Failure(NetError.CommandInFlight)

        frozen.discardedCredentials.forEach(HostCredential::wipe)
        frozen.pending.forEach { pending ->
            rejectSession(pending.session, AdmissionRejection.SessionStarted)
        }
        frozen.disconnectedSessions.forEach { session ->
            attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
        }
        return Result.Success(frozen.members)
    }

    /**
     * Revokes a frozen game seat as one host-owned transaction. Removing every
     * map entry under [stateMutex] makes a concurrent resume/admission commit
     * fail its identity checks; socket cleanup happens only after that
     * authoritative revocation is visible.
     */
    override suspend fun retireDisconnectedMember(
        playerId: PlayerId,
    ): Result<Unit, NetError> {
        if (playerId == hostPlayerId) return Result.Failure(NetError.Unauthorized)

        var failure: NetError? = null
        val retired = stateMutex.withLock {
            when {
                left -> {
                    failure = NetError.NotConnected
                    null
                }
                !admissionsClosed -> {
                    // Seat retirement is a gameplay operation. Lobby removal
                    // continues to use rejectAdmission/explicit peer Leave.
                    failure = NetError.InvalidInput
                    null
                }
                playerId !in previouslySeenPlayerIds -> {
                    failure = NetError.NotConnected
                    null
                }
                else -> {
                    val sessions = linkedSetOf<P2pSession>()
                    sessionsByPlayer.remove(playerId)?.let(sessions::add)
                    pendingByPlayer.remove(playerId)?.let { pending ->
                        sessions += pending.session
                        pending.transaction?.confirmation?.complete(Unit)
                    }
                    admissionReadyByPlayer.remove(playerId)?.let { barrier ->
                        sessions += barrier.session
                        barrier.signal.complete(Unit)
                    }
                    resumeReadyByPlayer.remove(playerId)?.let { barrier ->
                        sessions += barrier.session
                        barrier.signal.complete(Unit)
                    }
                    // Include a just-accepted physical session that has not yet
                    // reached one of the indexed transaction maps.
                    trackedSessions
                        .filterTo(sessions) { session ->
                            PlayerId(session.peer.id.value) == playerId
                        }
                    trackedSessions.removeAll(sessions)
                    admissionReservations.remove(playerId)
                    membersByPlayer.remove(playerId)
                    rejoinDeadlineByPlayer.remove(playerId)
                    val credential = credentialsByPlayer.remove(playerId)
                    publishMembers()
                    publishPendingAdmissions()
                    RetiredMemberResources(sessions, credential)
                }
            }
        }
        failure?.let { return Result.Failure(it) }

        // Once the mutex transition has committed, cancellation must not leave
        // a revoked credential resident in memory or an adopted socket alive.
        val resources = checkNotNull(retired)
        resources.credential?.wipe()
        withContext(NonCancellable) {
            resources.sessions.forEach { session ->
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.HOST,
                    preserveCancellation = false,
                ) { session.close() }
            }
        }
        return Result.Success(Unit)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod") // Admission reservation, commit, and ready barrier are one transaction.
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
                admissionsClosed -> {
                    pendingByPlayer.remove(playerId)
                    publishPendingAdmissions()
                    AdmissionPreparation.Rejected(
                        error = NetError.SessionStarted,
                        reason = AdmissionRejection.SessionStarted,
                    )
                }
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            withContext(NonCancellable) {
                rollbackAdmission(playerId, session)
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.HOST,
                    preserveCancellation = false,
                ) { session.close() }
            }
            failure.rethrowIfCancellation()
            return Result.Failure(NetError.TransportFailure("admission offer failed"))
        }

        val confirmed = try {
            withTimeoutOrNull(P2pKitRoomTransport.ADMISSION_CONFIRM_TIMEOUT_MS) {
                transaction.confirmation.await()
                true
            } ?: false
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            withContext(NonCancellable) {
                rollbackAdmission(playerId, session)
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.HOST,
                    preserveCancellation = false,
                ) { session.close() }
            }
            failure.rethrowIfCancellation()
            return Result.Failure(NetError.TransportFailure("admission confirmation failed"))
        }
        if (!confirmed) {
            rollbackAdmission(playerId, session)
            attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
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
            attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
            return Result.Failure(NetError.NotConnected)
        }
        diagnostics.event(
            P2pDiagnosticEventName.ADMISSION_COMMITTED,
            P2pDiagnosticRole.HOST,
            P2pDiagnosticResult.SUCCESS,
        )
        commit.previousSession?.let { previous ->
            attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { previous.close() }
        }
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            withContext(NonCancellable) {
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.HOST,
                    preserveCancellation = false,
                ) { session.close() }
            }
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
            attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
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
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
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
        attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) {
            sendRaw(session, HostMessage.AdmissionRejected(reason))
        }
        delay(P2pKitRoomTransport.ADMISSION_REJECTION_FLUSH_MS)
        attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
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
        attemptCleanup(diagnostics, P2pDiagnosticRole.HOST) { session.close() }
    }

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        val bytes = try {
            codec.encode(message)
        } catch (_: IllegalArgumentException) {
            return Result.Failure(NetError.PayloadTooLarge)
        }
        val payload = P2pMessage.Binary(bytes)
        val result = try {
            when (target) {
                SendTarget.Broadcast -> broadcast(payload)
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            Result.Failure(NetError.TransportFailure(failure.message ?: "send failed"))
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

    private suspend fun broadcast(payload: P2pMessage.Binary): Result<Unit, NetError> {
        // Snapshot under the lock to avoid racing admission/removal, then send
        // off-lock so one slow peer cannot block membership state transitions.
        val targets = stateMutex.withLock { sessionsByPlayer.values.toList() }
        var delivered = 0
        var firstFailure: Exception? = null
        targets.forEach { session ->
            if (session.state.value == ConnectionState.Connected) {
                try {
                    session.send(payload)
                    delivered++
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    failure.rethrowIfCancellation()
                    if (firstFailure == null) firstFailure = failure
                }
            }
        }
        // A broadcast that reached zero connected peers is a delivery failure;
        // callers must not treat an undelivered snapshot as synchronized.
        return when {
            firstFailure != null -> Result.Failure(
                NetError.TransportFailure(firstFailure.message ?: "send failed"),
            )
            delivered == 0 -> Result.Failure(NetError.NotConnected)
            else -> Result.Success(Unit)
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
            return
        }
        // The first caller has set the terminal `left` guard, so it must finish
        // cleanup even if the owning UI/lifecycle coroutine is cancelled.
        withContext(NonCancellable) {
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
            attemptCleanup(
                diagnostics,
                P2pDiagnosticRole.HOST,
                preserveCancellation = false,
            ) { kit.stopAdvertising() }
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
            toClose.forEach { session ->
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.HOST,
                    preserveCancellation = false,
                ) { session.close() }
            }
            // kit.stop() is terminal; guard it so a late/duplicate teardown can't
            // throw out of a disposal path. See PROBLEMS_PARLOR.md → p2p-016.
            attemptCleanup(
                diagnostics,
                P2pDiagnosticRole.HOST,
                preserveCancellation = false,
            ) { kit.stop() }
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
}

internal data class ResumedPeerConnection(
    val session: P2pSession,
    val hostPeer: Peer,
    val credential: ResumableSessionCredential,
)

internal data class ResumeConnectionFailure(
    val error: NetError,
    /** True only for local expiry/corruption or an authenticated host rejection. */
    val invalidatesCredential: Boolean = false,
)

private enum class SessionReplacementOutcome {
    Ready,
    AdoptedHandoffFailed,
    NotAdopted,
}

private enum class CredentialInvalidationScope {
    ExactGeneration,
    LogicalMembership,
}

internal enum class ResumeAdoptionOutcome {
    Ready,
    Retry,
    Terminal,
}

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
        suspend (ResumableSessionCredential) ->
            Result<ResumedPeerConnection, ResumeConnectionFailure>
    )? = null,
    private val onClosed: suspend () -> Unit = {},
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
    /** An authenticated host terminal frame permanently disables logical resume. */
    private var terminalByHost = false
    private var activeSession: P2pSession = session
    private var collectorJob: Job = launchIncomingCollector(session, initialCredential)
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
                            !terminalByHost &&
                            (_lifecycle.value as? RoomLifecycleState.Resuming)
                                ?.resumeDeadlineEpochMillis == deadline
                    }
                ) {
                    when (val resumed = connector(candidate)) {
                        is Result.Success -> {
                            candidate = resumed.data.credential
                            when (adoptResumedConnection(resumed.data)) {
                                ResumeAdoptionOutcome.Ready,
                                ResumeAdoptionOutcome.Terminal -> return@launch
                                ResumeAdoptionOutcome.Retry -> Unit
                            }
                        }
                        is Result.Failure -> {
                            // Only local expiry/corruption or a rejection received
                            // through the pinned authenticated host session is
                            // terminal. A defensive post-connect identity mismatch
                            // is Unauthorized too, but must retain the capability
                            // and retry until the lifecycle deadline.
                            if (resumed.error.invalidatesCredential) {
                                expireLifecycle(deadline)
                                return@launch
                            }
                        }
                    }
                    delay(P2pKitRoomTransport.ADMISSION_RETRY_MS)
                }
        }
        val accepted = lifecycleMutex.withLock {
            if (left || terminalByHost || resumeJob?.isActive == true) {
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

    /**
     * Transfers a successful connector result into this room or closes it when
     * terminal/leave ownership won first. This is the single ownership boundary
     * for every physical resume session returned by [resumeConnector].
     */
    internal suspend fun adoptResumedConnection(
        resumed: ResumedPeerConnection,
    ): ResumeAdoptionOutcome {
        var adopted = false
        return try {
            when (replaceSession(resumed)) {
                SessionReplacementOutcome.Ready -> {
                    adopted = true
                    restoreActiveRoom(emitEvent = true)
                    ResumeAdoptionOutcome.Ready
                }
                SessionReplacementOutcome.AdoptedHandoffFailed -> {
                    adopted = true
                    ResumeAdoptionOutcome.Retry
                }
                SessionReplacementOutcome.NotAdopted -> ResumeAdoptionOutcome.Terminal
            }
        } finally {
            if (!adopted) {
                withContext(NonCancellable) {
                    closeSessionUnlessActive(resumed.session)
                }
            }
        }
    }

    private suspend fun replaceSession(
        resumed: ResumedPeerConnection,
    ): SessionReplacementOutcome {
        val old = lifecycleMutex.withLock {
            if (left || terminalByHost) return SessionReplacementOutcome.NotAdopted
            sessionMutex.withLock {
                val previous = Triple(activeSession, collectorJob, stateJob)
                activeSession = resumed.session
                activeCredential.value = resumed.credential
                collectorJob = launchIncomingCollector(
                    resumed.session,
                    resumed.credential,
                )
                stateJob = launchSessionStateCollector(resumed.session)
                previous
            }
        }
        old.second.cancelAndJoin()
        old.third.cancelAndJoin()
        if (old.first !== resumed.session) closeSessionSafely(old.first)
        return if (signalResumeReady(resumed)) {
            SessionReplacementOutcome.Ready
        } else {
            SessionReplacementOutcome.AdoptedHandoffFailed
        }
    }

    /** Closes a connector result only when it was never installed as the active session. */
    private suspend fun closeSessionUnlessActive(session: P2pSession) {
        val isActive = lifecycleMutex.withLock {
            sessionMutex.withLock { activeSession === session }
        }
        if (!isActive) closeSessionSafely(session)
    }

    private suspend fun closeSessionSafely(session: P2pSession) {
        try {
            session.close()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            diagnostics.event(
                P2pDiagnosticEventName.CLEANUP_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                P2pDiagnosticReason.TRANSPORT,
            )
        }
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
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.PEER,
                    preserveCancellation = false,
                ) { session.close() }
            }
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            closeSessionSafely(session)
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
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
        }
        return true
    }

    internal suspend fun abandonFailedResume() {
        closeForRetry()
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
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                try {
                    resumed.session.close()
                } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                    // The original handoff cancellation is rethrown below;
                    // this records that its non-cancellable close also failed.
                    diagnostics.event(
                        P2pDiagnosticEventName.CLEANUP_FAILED,
                        P2pDiagnosticRole.PEER,
                        P2pDiagnosticResult.FAILURE,
                        P2pDiagnosticReason.TRANSPORT,
                    )
                }
            }
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            closeSessionSafely(resumed.session)
            return false
        }
        try {
            val acknowledgement = PeerMessage.ResumeCommitAck(
                actor = selfPlayerId,
                offerId = credential.offerId,
                generation = credential.generation,
            )
            resumed.session.send(P2pMessage.Binary(codec.encode(acknowledgement)))
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            // Ready is the ordering barrier. A lost cleanup acknowledgement
            // leaves one prior generation valid until the next successful
            // rotation, but does not make this attached session unsafe.
        }
        return true
    }

    private suspend fun restoreActiveRoom(emitEvent: Boolean) {
        val restored = lifecycleMutex.withLock {
            if (left || terminalByHost || _lifecycle.value == RoomLifecycleState.Expired) {
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
            leave(
                sendNotice = false,
                invalidationReason = P2pDiagnosticReason.LIFECYCLE,
                invalidationScope = CredentialInvalidationScope.LogicalMembership,
            )
        }
    }

    private fun launchIncomingCollector(
        session: P2pSession,
        credentialBinding: ResumableSessionCredential?,
    ): Job = scope.launch {
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
            val decoded = try {
                codec.decode(msg.bytes)
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                failure.rethrowIfCancellation()
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
            if (decoded is HostMessage.SessionEnded) {
                if (!acceptAuthenticatedTerminal(session, credentialBinding)) {
                    return@collect
                }
            }
            @Suppress("DEPRECATION")
            if (decoded is HostMessage.AdmissionAccepted) {
                enforceTrafficDecision(
                    trafficGuard.malformedFrame(nowMillis()),
                    session,
                    P2pDiagnosticReason.INCOMPATIBLE_PROTOCOL,
                )
                return@collect
            }
            when (decoded) {
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

    /**
     * Accepts a terminal frame only from the currently owned physical session.
     * The collector's credential binding is invalidated before the frame reaches
     * UI/game cleanup, so process/UI teardown is not required for correctness.
     */
    private suspend fun acceptAuthenticatedTerminal(
        session: P2pSession,
        credentialBinding: ResumableSessionCredential?,
    ): Boolean {
        var resumeToCancel: Job? = null
        val accepted = lifecycleMutex.withLock {
            if (left || terminalByHost) {
                false
            } else {
                sessionMutex.withLock {
                    if (activeSession !== session) {
                        false
                    } else {
                        terminalByHost = true
                        resumeToCancel = resumeJob
                        resumeJob = null
                        true
                    }
                }
            }
        }
        if (!accepted) return false

        resumeToCancel?.cancel()
        invalidateCredentialForRoom(
            credentialBinding,
            P2pDiagnosticReason.SESSION_ENDED,
            CredentialInvalidationScope.LogicalMembership,
        )
        return true
    }

    /**
     * Invalidates the ownership scope selected by the lifecycle transaction.
     * Authenticated terminal/final Leave and lifecycle expiry revoke the
     * logical membership, including a just-committed rotation. Only stale
     * non-terminal cleanup retains exact-generation behavior.
     */
    private suspend fun invalidateCredentialForRoom(
        credentialBinding: ResumableSessionCredential?,
        reason: P2pDiagnosticReason,
        scope: CredentialInvalidationScope,
    ): Result<Unit, NetError> {
        credentialBinding ?: return Result.Success(Unit)
        fun ownsCredential(candidate: ResumableSessionCredential?): Boolean = when (scope) {
            CredentialInvalidationScope.ExactGeneration ->
                candidate?.ownsSameGenerationAs(credentialBinding) == true
            CredentialInvalidationScope.LogicalMembership ->
                candidate?.ownsSameMembershipAs(credentialBinding) == true
        }
        val store = credentialStore
        if (store == null) {
            if (ownsCredential(activeCredential.value)) {
                activeCredential.value = null
            }
            return Result.Success(Unit)
        }
        val invalidated = try {
            when (scope) {
                CredentialInvalidationScope.ExactGeneration ->
                    store.invalidateOwned(credentialBinding)
                CredentialInvalidationScope.LogicalMembership ->
                    store.invalidateMembershipOwned(credentialBinding)
            }
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            diagnostics.event(
                P2pDiagnosticEventName.CREDENTIAL_INVALIDATION_FAILED,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.FAILURE,
                P2pDiagnosticReason.INTERNAL,
            )
            return Result.Failure(NetError.SecureStorageUnavailable)
        }
        return when (invalidated) {
            is Result.Success -> {
                if (ownsCredential(activeCredential.value)) {
                    activeCredential.value = null
                }
                diagnostics.event(
                    P2pDiagnosticEventName.CREDENTIAL_INVALIDATED,
                    P2pDiagnosticRole.PEER,
                    if (invalidated.data == CredentialInvalidationResult.Invalidated) {
                        P2pDiagnosticResult.SUCCESS
                    } else {
                        P2pDiagnosticResult.DUPLICATE
                    },
                    reason,
                )
                Result.Success(Unit)
            }
            is Result.Failure -> {
                diagnostics.event(
                    P2pDiagnosticEventName.CREDENTIAL_INVALIDATION_FAILED,
                    P2pDiagnosticRole.PEER,
                    P2pDiagnosticResult.FAILURE,
                    P2pDiagnosticReason.INTERNAL,
                )
                Result.Failure(NetError.SecureStorageUnavailable)
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
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
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
                ConnectionState.Reconnecting -> {
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
                ConnectionState.Failed,
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
                    beginForegroundResume()
                }
                ConnectionState.Idle,
                ConnectionState.Connecting,
                ConnectionState.Handshaking,
                ConnectionState.Closing -> Unit
            }
        }
    }

    /** Starts credential-based logical resume after a terminal foreground drop. */
    private suspend fun beginForegroundResume() {
        val now = nowMillis()
        val deadline = lifecycleMutex.withLock {
            if (left || terminalByHost) return@withLock null
            when (val current = _lifecycle.value) {
                RoomLifecycleState.Active -> (now + appResumeGraceMs).also { resumeDeadline ->
                    _lifecycle.value = RoomLifecycleState.Resuming(resumeDeadline)
                }
                is RoomLifecycleState.Resuming -> current.resumeDeadlineEpochMillis
                is RoomLifecycleState.Suspended,
                RoomLifecycleState.Expired,
                RoomLifecycleState.Closed -> null
            }
        } ?: return
        scheduleLifecycleExpiry(deadline, (deadline - now).coerceAtLeast(0L))
        diagnostics.event(
            P2pDiagnosticEventName.LIFECYCLE_RESUME_STARTED,
            P2pDiagnosticRole.PEER,
        )
        resumeAfterForeground(deadline)
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
        val session = lifecycleMutex.withLock {
            if (left || terminalByHost) return Result.Failure(NetError.NotConnected)
            sessionMutex.withLock { activeSession }
        }
        if (session.state.value != ConnectionState.Connected) {
            return Result.Failure(NetError.NotConnected)
        }
        val bytes = try {
            codec.encode(message)
        } catch (_: IllegalArgumentException) {
            return Result.Failure(NetError.PayloadTooLarge)
        }
        if (bytes.size > P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES) {
            return Result.Failure(NetError.PayloadTooLarge)
        }
        val result = try {
            val payload = P2pMessage.Binary(bytes)
            session.send(payload)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            failure.rethrowIfCancellation()
            Result.Failure(NetError.TransportFailure(failure.message ?: "send failed"))
        }
        if (result is Result.Success && message is PeerMessage.ClientCommand) {
            diagnostics.event(
                P2pDiagnosticEventName.COMMAND_SENT,
                P2pDiagnosticRole.PEER,
                P2pDiagnosticResult.SUCCESS,
            )
        }
        return result
    }

    override suspend fun leave() = leave(
        sendNotice = true,
        invalidationScope = CredentialInvalidationScope.LogicalMembership,
    )

    override suspend fun finalLeave(): Result<Unit, NetError> {
        val credentialBinding = activeCredential.value
        // Close transport resources first but retain the credential binding so
        // a failed secure deletion can be retried after this room is closed.
        leave(sendNotice = true, invalidateCredential = false)
        return invalidateCredentialForRoom(
            credentialBinding = credentialBinding,
            reason = P2pDiagnosticReason.LIFECYCLE,
            scope = CredentialInvalidationScope.LogicalMembership,
        )
    }

    /**
     * A failed game-start transaction is not an explicit membership Leave.
     * Close the socket/kit without a LeaveNotice (the host interprets that as
     * permanent revocation) and preserve the secure credential for the next
     * process-level resume attempt.
     */
    override suspend fun closeForRetry(): Result<Unit, NetError> {
        leave(sendNotice = false, invalidateCredential = false)
        return Result.Success(Unit)
    }

    /**
     * Revokes the stable membership retained by [closeForRetry]. This remains
     * callable after physical cleanup made [leave] idempotent, and membership
     * ownership intentionally spans a credential rotation committed by a
     * concurrent resume transaction.
     */
    override suspend fun discardRejoinCapability(): Result<Unit, NetError> =
        invalidateCredentialForRoom(
            credentialBinding = activeCredential.value,
            reason = P2pDiagnosticReason.LIFECYCLE,
            scope = CredentialInvalidationScope.LogicalMembership,
        )

    private suspend fun leave(
        sendNotice: Boolean,
        invalidateCredential: Boolean = true,
        invalidationReason: P2pDiagnosticReason = P2pDiagnosticReason.NONE,
        invalidationScope: CredentialInvalidationScope =
            CredentialInvalidationScope.ExactGeneration,
    ) {
        val (shouldLeave, jobs, credentialAtLeave) = lifecycleMutex.withLock {
            if (left) {
                Triple(false, emptyList(), null)
            } else {
                left = true
                val toCancel = listOfNotNull(lifecycleExpiryJob, resumeJob)
                lifecycleExpiryJob = null
                resumeJob = null
                Triple(true, toCancel, activeCredential.value)
            }
        }
        if (!shouldLeave) {
            return
        }
        // Capture the owning job before replacing the context with
        // NonCancellable. Inside that context currentCoroutineContext()[Job]
        // is the non-cancellable cleanup job, not the resume/expiry job that
        // invoked leave(); joining the latter from itself deadlocks cleanup.
        val leavingJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        withContext(NonCancellable) {
            jobs.forEach { job ->
                if (job !== leavingJob) {
                    job.cancelAndJoin()
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
                attemptCleanup(
                    diagnostics,
                    P2pDiagnosticRole.PEER,
                    preserveCancellation = false,
                ) {
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
            var invalidationCancellation: CancellationException? = null
            if (invalidateCredential) {
                try {
                    invalidateCredentialForRoom(
                        credentialAtLeave,
                        invalidationReason,
                        invalidationScope,
                    )
                } catch (cancelled: CancellationException) {
                    // Finish terminal resource cleanup, then preserve structured
                    // cancellation for the caller instead of translating it.
                    invalidationCancellation = cancelled
                }
            }
            attemptCleanup(
                diagnostics,
                P2pDiagnosticRole.PEER,
                preserveCancellation = false,
            ) { session.close() }
            // kit.stop() is terminal — guard against a duplicate/late teardown.
            attemptCleanup(
                diagnostics,
                P2pDiagnosticRole.PEER,
                preserveCancellation = false,
            ) { kit.stop() }
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
            invalidationCancellation?.let { throw it }
        }
    }
}
