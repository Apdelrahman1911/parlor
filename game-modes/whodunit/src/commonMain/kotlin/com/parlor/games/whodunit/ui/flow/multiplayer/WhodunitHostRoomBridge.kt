package com.parlor.games.whodunit.ui.flow.multiplayer

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.games.whodunit.domain.authority.WhodunitActionAuthority
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.security.SecureIds
import com.parlor.session.multidevice.CommandApplication
import com.parlor.session.multidevice.HostAuthoritativeSessionCoordinator
import com.parlor.session.multidevice.HostMutationResult
import com.parlor.session.multidevice.PlayerSnapshotPayload
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.engine.session.SubmitError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Owns Whodunit's host-authoritative wire protocol.
 *
 * The reducer on the host is the sole state mutator. Peer commands are
 * authenticated by the transport, actor-authorized here, ordered/deduplicated
 * by [HostAuthoritativeSessionCoordinator], and answered with a single atomic
 * public + own-private snapshot at one revision.
 */
class WhodunitHostRoomBridge(
    private val controller: PassAndPlaySessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    private val room: LocalRoom,
    private val players: List<Player>,
    private val scope: CoroutineScope,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    },
    private val rejoinGraceMs: Long = REJOIN_GRACE_MS,
    heartbeatIntervalMs: Long = HEARTBEAT_INTERVAL_MS,
    private val startRetryMs: Long = START_RETRY_MS,
    private val startMaxRetryMs: Long = START_MAX_RETRY_MS,
    private val startDeadlineMs: Long = START_DEADLINE_MS,
    sessionIdGenerator: () -> String = SecureIds::id128,
    reconcileRoomTopology: Boolean = false,
    requireStartHandshake: Boolean = true,
) {
    val protocol: SessionProtocol = SessionProtocol(
        sessionId = SessionId(sessionIdGenerator()),
        gameId = WhodunitIds.GameId,
        gameVersion = GAME_VERSION,
    )

    private val publicStateSerializer = WhodunitState.serializer()
    private val privateSerializer = WhodunitPrivate.serializer()
    private val remotePlayers = players.map(Player::id).toSet() - room.selfPlayerId
    private val lifecycleMutex = Mutex()
    private val graceJobs = mutableMapOf<PlayerId, Job>()
    private val rejoinJobs = mutableMapOf<PlayerId, Job>()
    private var terminated = false
    private var pausedByAppLifecycle = false
    private val bridgeJob = SupervisorJob(scope.coroutineContext[Job])
    private val bridgeScope = CoroutineScope(scope.coroutineContext + bridgeJob)

    private val coordinator = HostAuthoritativeSessionCoordinator(
        room = room,
        protocol = protocol,
        remotePlayers = remotePlayers,
        scope = bridgeScope,
        applyCommand = ::applyRemoteCommand,
        snapshotFor = ::snapshotFor,
        heartbeatIntervalMs = heartbeatIntervalMs,
        requireStartHandshake = requireStartHandshake,
    )

    private val peerEventsJob = if (reconcileRoomTopology) {
        // Production room membership is a replaying StateFlow. Using it as the
        // topology authority closes the freeze-roster -> bridge-subscription
        // race: the first collection always reconciles the current connection
        // state, even if PeerLeft happened before this bridge existed.
        bridgeScope.launch { room.members.collect(::reconcileMembers) }
    } else {
        // Deterministic bridge fixtures expose only synthetic PeerEvents.
        bridgeScope.launch { room.peerEvents.collect(::handlePeerEvent) }
    }

    /**
     * Freezes the canonical game clock for the whole transport interruption.
     * A lifecycle-owned pause is resumed only after every retained peer has
     * restored admission and the room is Active again. A pause chosen by a
     * player before backgrounding remains a player-owned pause and is never
     * lifted automatically.
     */
    private val roomLifecycleJob = bridgeScope.launch {
        room.lifecycle.collect { lifecycle ->
            when (lifecycle) {
                RoomLifecycleState.Active -> {
                    resumeLifecyclePauseIfPossible()
                }
                is RoomLifecycleState.Suspended,
                is RoomLifecycleState.Resuming -> {
                    if (!controller.currentState().public.paused) {
                        val paused = applyLifecycleAction(WhodunitAction.Pause)
                        if (paused) {
                            lifecycleMutex.withLock {
                                if (!terminated) pausedByAppLifecycle = true
                            }
                        }
                    }
                }
                RoomLifecycleState.Expired,
                RoomLifecycleState.Closed -> Unit
            }
        }
    }

    /**
     * Announces the immutable protocol tuple before publishing revision zero.
     * The nonce is public and never contains the reducer's role-assignment seed.
     */
    suspend fun announceStart(
        caseId: String,
        modeId: String,
        caseVersion: String,
        caseDigest: String,
    ): Result<Unit, com.parlor.networking.room.NetError> =
        when (
            val started = coordinator.startSession(
                caseId = caseId,
                modeId = modeId,
                players = players,
                sessionNonce = room.info.value.code.hashCode().toLong(),
                caseVersion = caseVersion,
                caseDigest = caseDigest,
                initialRetryMs = startRetryMs,
                maxRetryMs = startMaxRetryMs,
                deadlineMs = startDeadlineMs,
            )
        ) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(started.error)
        }

    /**
     * Serializes a host-originated action with remote commands and publishes
     * exactly one revision when the reducer commits a change.
     */
    suspend fun submitHostAction(
        action: WhodunitAction,
    ): Result<SubmissionReceipt, SubmitError> {
        if (!coordinator.awaitSessionStarted()) {
            return Result.Failure(SubmitError.SessionClosed)
        }
        var submission: Result<SubmissionReceipt, SubmitError>? = null
        val mutation = coordinator.applyHostMutation {
            controller.submit(action).also { submission = it }
                .let { it is Result.Success && it.data.stateChanged }
        }
        return when (mutation) {
            HostMutationResult.Closed -> Result.Failure(SubmitError.SessionClosed)
            HostMutationResult.NotStarted -> Result.Failure(SubmitError.SessionClosed)
            HostMutationResult.Applied,
            HostMutationResult.Unchanged -> checkNotNull(submission)
        }
    }

    /** Delivers a terminal envelope before the caller navigates away. */
    suspend fun terminate(reason: SessionEndReason = SessionEndReason.HostLeft) {
        val jobsToCancel = lifecycleMutex.withLock {
            if (terminated) {
                null
            } else {
                terminated = true
                (graceJobs.values + rejoinJobs.values).also {
                    graceJobs.clear()
                    rejoinJobs.clear()
                }
            }
        } ?: return
        jobsToCancel.forEach(Job::cancel)
        coordinator.end(reason)
    }

    /**
     * Applies the host's explicit decision before the grace timer expires.
     *
     * Keeping this transition on the bridge prevents gameplay controllers from
     * opening a bypass around the orchestration pause and guarantees that the
     * pending expiry job cannot race a confirmed host action.
     */
    suspend fun continueWithout(playerId: PlayerId): Boolean {
        if (playerId !in remotePlayers) return false
        val jobsToCancel = lifecycleMutex.withLock {
            if (terminated) null else listOfNotNull(
                graceJobs.remove(playerId),
                rejoinJobs.remove(playerId),
            )
        } ?: return false
        jobsToCancel.forEach(Job::cancel)
        val changed = applyLifecycleAction(WhodunitAction.ContinueWithoutPlayer(playerId))
        if (changed) resumeLifecyclePauseIfPossible()
        return changed
    }

    fun close() {
        bridgeJob.cancel()
        coordinator.close()
    }

    private suspend fun applyRemoteCommand(
        actor: PlayerId,
        payload: ByteArray,
    ): CommandApplication {
        val action = try {
            WhodunitActionCodec.decode(payload)
        } catch (_: Exception) {
            return CommandApplication.InvalidAction
        }
        val before = controller.currentState()
        if (before.public.paused) return CommandApplication.InvalidAction
        if (
            !WhodunitActionAuthority.isAllowed(
                action = action,
                senderId = actor,
                hostId = room.selfPlayerId,
                droppedPlayers = before.public.droppedPlayers,
            )
        ) {
            return CommandApplication.Unauthorized
        }
        return when (val result = controller.submit(action)) {
            is Result.Failure -> CommandApplication.InvalidAction
            is Result.Success -> if (result.data.stateChanged) {
                CommandApplication.Applied
            } else {
                CommandApplication.InvalidAction
            }
        }
    }

    private suspend fun snapshotFor(playerId: PlayerId): PlayerSnapshotPayload {
        // Read the canonical state exactly once so public and private bytes can
        // never describe different reducer revisions.
        val state = controller.currentState()
        val publicState = WhodunitProjectionPolicy.toPublic(state).state
        val publicPayload = json
            .encodeToString(publicStateSerializer, publicState)
            .encodeToByteArray()
        val privatePayload = state.privatePerPlayer[playerId]?.let { slice ->
            json.encodeToString(privateSerializer, slice).encodeToByteArray()
        } ?: ByteArray(0)
        return PlayerSnapshotPayload(publicPayload, privatePayload)
    }

    private suspend fun handlePeerEvent(event: PeerEvent) {
        when (event) {
            is PeerEvent.PeerLeft -> handlePeerLeft(event.playerId)
            is PeerEvent.PeerReconnected -> handlePeerReconnected(event.playerId)
            is PeerEvent.AdmissionRequested,
            is PeerEvent.PeerJoined,
            PeerEvent.HostLost,
            PeerEvent.HostRestored,
            PeerEvent.SelfOffline,
            PeerEvent.SelfOnline -> Unit
        }
    }

    private suspend fun reconcileMembers(members: List<RoomMember>) {
        val connected = members.asSequence()
            .filter(RoomMember::connected)
            .map(RoomMember::playerId)
            .toSet()
        remotePlayers.forEach { playerId ->
            val markedDisconnected =
                playerId in controller.currentState().public.disconnectedPlayers
            when {
                playerId !in connected && !markedDisconnected -> handlePeerLeft(playerId)
                playerId in connected && markedDisconnected -> handlePeerReconnected(playerId)
            }
        }
    }

    private suspend fun handlePeerLeft(playerId: PlayerId) {
        if (playerId !in remotePlayers) return
        if (lifecycleMutex.withLock { terminated }) return
        applyLifecycleAction(WhodunitAction.MarkPlayerDisconnected(playerId))
        val interruptedRejoin = lifecycleMutex.withLock { rejoinJobs.remove(playerId) }
        interruptedRejoin?.cancel()
        if (controller.currentState().phase != WhodunitPhase.PostGame) {
            scheduleGraceExpiry(playerId)
        }
    }

    private suspend fun handlePeerReconnected(playerId: PlayerId) {
        if (
            playerId !in remotePlayers ||
            playerId !in controller.currentState().public.disconnectedPlayers
        ) {
            return
        }
        scheduleRejoin(playerId)
    }

    private suspend fun scheduleGraceExpiry(playerId: PlayerId) {
        lateinit var graceJob: Job
        graceJob = bridgeScope.launch(start = CoroutineStart.LAZY) {
            delay(rejoinGraceMs)
            expireGrace(playerId, graceJob)
        }
        val installed = lifecycleMutex.withLock {
            if (terminated || graceJobs[playerId] != null) {
                false
            } else {
                graceJobs[playerId] = graceJob
                true
            }
        }
        if (installed) graceJob.start() else graceJob.cancel()
    }

    private suspend fun expireGrace(playerId: PlayerId, graceJob: Job) {
        var rejoinToCancel: Job? = null
        val ownsDeadline = lifecycleMutex.withLock {
            if (terminated || graceJobs[playerId] !== graceJob) {
                false
            } else {
                graceJobs.remove(playerId)
                rejoinToCancel = rejoinJobs.remove(playerId)
                true
            }
        }
        if (!ownsDeadline) return
        rejoinToCancel?.cancel()
        if (playerId in controller.currentState().public.disconnectedPlayers) {
            val changed = applyLifecycleAction(WhodunitAction.ContinueWithoutPlayer(playerId))
            if (changed) resumeLifecyclePauseIfPossible()
        }
    }

    private suspend fun scheduleRejoin(playerId: PlayerId) {
        lateinit var rejoinJob: Job
        rejoinJob = bridgeScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = coordinator.resendStart(
                    playerId = playerId,
                    initialRetryMs = startRetryMs,
                    maxRetryMs = startMaxRetryMs,
                    readyDeadlineMs = startDeadlineMs,
                    commitAckDeadlineMs = startDeadlineMs,
                )
                if (result is Result.Success) completeRejoin(playerId, rejoinJob)
            } finally {
                lifecycleMutex.withLock {
                    if (rejoinJobs[playerId] === rejoinJob) rejoinJobs.remove(playerId)
                }
            }
        }
        val installed = lifecycleMutex.withLock {
            if (terminated || rejoinJobs[playerId] != null) {
                false
            } else {
                rejoinJobs[playerId] = rejoinJob
                true
            }
        }
        if (installed) rejoinJob.start() else rejoinJob.cancel()
    }

    private suspend fun completeRejoin(playerId: PlayerId, rejoinJob: Job) {
        var graceToCancel: Job? = null
        val ownsSeat = lifecycleMutex.withLock {
            if (terminated || rejoinJobs[playerId] !== rejoinJob) {
                false
            } else {
                graceToCancel = graceJobs.remove(playerId)
                graceToCancel != null
            }
        }
        if (!ownsSeat) return
        graceToCancel?.cancel()
        val changed = applyLifecycleAction(WhodunitAction.MarkPlayerReconnected(playerId))
        if (!changed) coordinator.publishState(incrementRevision = false)
        resumeLifecyclePauseIfPossible()
    }

    /**
     * Active can be emitted before a disconnected seat completes the start
     * replay barrier. Re-evaluate the lifecycle-owned pause after every roster
     * restoration/drop as well as on Active; otherwise StateFlow will not emit
     * a second identical Active value and the game can remain paused forever.
     */
    private suspend fun resumeLifecyclePauseIfPossible() {
        val shouldResume = lifecycleMutex.withLock {
            !terminated && pausedByAppLifecycle
        } && room.lifecycle.value == RoomLifecycleState.Active
        if (!shouldResume) return
        if (applyLifecycleAction(WhodunitAction.Resume)) {
            lifecycleMutex.withLock { pausedByAppLifecycle = false }
        }
    }

    private suspend fun applyLifecycleAction(action: WhodunitAction): Boolean {
        if (!coordinator.awaitSessionStarted()) return false
        var submission: Result<SubmissionReceipt, SubmitError>? = null
        val mutation = coordinator.applyHostMutation {
            controller.submit(action).also { submission = it }
                .let { it is Result.Success && it.data.stateChanged }
        }
        return mutation == HostMutationResult.Applied && submission is Result.Success
    }

    companion object {
        /** v2 adds authoritative, roster-filtered killer deflection targets. */
        const val GAME_VERSION: Int = 2
        const val REJOIN_GRACE_MS: Long = 120_000L
        const val HEARTBEAT_INTERVAL_MS: Long = 10_000L
        const val START_RETRY_MS: Long = 250L
        const val START_MAX_RETRY_MS: Long = 2_000L
        const val START_DEADLINE_MS: Long = 20_000L
    }
}
