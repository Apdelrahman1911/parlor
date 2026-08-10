package com.parlor.games.mafia.ui.flow.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.action.MafiaActionCodec
import com.parlor.games.mafia.domain.authority.MafiaActionAuthority
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomMember
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
 * Mafia's single-writer, host-authoritative room bridge.
 *
 * Each accepted peer command is authenticated by transport identity,
 * actor-authorized, ordered and deduplicated before it reaches the reducer.
 * State replication is one atomic public + own-private envelope per revision.
 */
class MafiaHostRoomBridge(
    private val controller: PassAndPlaySessionController<MafiaState, MafiaAction, MafiaEvent>,
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
        gameId = MafiaIds.GameId,
        gameVersion = GAME_VERSION,
    )

    private val publicStateSerializer = MafiaState.serializer()
    private val privateSerializer = MafiaPrivate.serializer()
    private val remotePlayers = players.map(Player::id).toSet() - room.selfPlayerId
    private val lifecycleMutex = Mutex()
    private val graceJobs = mutableMapOf<PlayerId, Job>()
    private val rejoinJobs = mutableMapOf<PlayerId, Job>()
    private var terminated = false
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

    init {
        if (reconcileRoomTopology) {
            bridgeScope.launch { room.members.collect(::reconcileMembers) }
        } else {
            bridgeScope.launch { room.peerEvents.collect(::handlePeerEvent) }
        }
    }

    suspend fun announceStart(caseId: String, modeId: String): Result<Unit, com.parlor.networking.room.NetError> =
        when (
            val started = coordinator.startSession(
                caseId = caseId,
                modeId = modeId,
                players = players,
                sessionNonce = room.info.value.code.hashCode().toLong(),
                initialRetryMs = startRetryMs,
                maxRetryMs = startMaxRetryMs,
                deadlineMs = startDeadlineMs,
            )
        ) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(started.error)
        }

    /** Serializes host UI actions with remote commands and protocol revisions. */
    suspend fun submitHostAction(
        action: MafiaAction,
    ): Result<SubmissionReceipt, SubmitError> {
        if (!coordinator.awaitSessionStarted()) {
            return Result.Failure(SubmitError.SessionClosed)
        }
        var submission: Result<SubmissionReceipt, SubmitError>? = null
        val mutation = coordinator.applyHostMutation {
            if (controller.currentState().public.disconnectedPlayers.isNotEmpty()) {
                submission = Result.Failure(SubmitError.IllegalForPhase)
                false
            } else {
                controller.submit(action).also { submission = it }
                    .let { it is Result.Success && it.data.stateChanged }
            }
        }
        return when (mutation) {
            HostMutationResult.Closed -> Result.Failure(SubmitError.SessionClosed)
            HostMutationResult.NotStarted -> Result.Failure(SubmitError.SessionClosed)
            HostMutationResult.Suspended -> Result.Failure(SubmitError.SessionSuspended)
            HostMutationResult.Applied,
            HostMutationResult.Unchanged -> checkNotNull(submission)
        }
    }

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
     * Lifecycle recovery stays bridge-owned so gameplay commands remain
     * blocked while any seat is transiently disconnected.
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
        return retireAndContinue(playerId)
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
            MafiaActionCodec.decode(payload)
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            return CommandApplication.InvalidAction
        }
        val before = controller.currentState()
        // A transiently missing seat pauses Mafia at the orchestration
        // boundary. The domain remains topology-agnostic.
        if (before.public.disconnectedPlayers.isNotEmpty()) {
            return CommandApplication.InvalidAction
        }
        if (
            !MafiaActionAuthority.isAllowed(
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
        val state = controller.currentState()
        val publicState = MafiaProjectionPolicy.toPublic(state).state
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
            val public = controller.currentState().public
            if (playerId in public.droppedPlayers) return@forEach
            val markedDisconnected = playerId in public.disconnectedPlayers
            when {
                playerId !in connected && !markedDisconnected -> handlePeerLeft(playerId)
                playerId in connected && markedDisconnected -> handlePeerReconnected(playerId)
            }
        }
    }

    private suspend fun handlePeerLeft(playerId: PlayerId) {
        if (playerId !in remotePlayers) return
        if (lifecycleMutex.withLock { terminated }) return
        applyLifecycleAction(MafiaAction.MarkPlayerDisconnected(playerId))
        val interruptedRejoin = lifecycleMutex.withLock { rejoinJobs.remove(playerId) }
        interruptedRejoin?.cancel()
        if (controller.currentState().phase != MafiaPhase.PostGame) {
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
            retireAndContinue(playerId)
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
        val changed = applyLifecycleAction(MafiaAction.MarkPlayerReconnected(playerId))
        if (!changed) coordinator.publishState(incrementRevision = false)
    }

    private suspend fun applyLifecycleAction(action: MafiaAction): Boolean {
        if (!coordinator.awaitSessionStarted()) return false
        var submission: Result<SubmissionReceipt, SubmitError>? = null
        val mutation = coordinator.applyLifecycleMutation {
            controller.submit(action).also { submission = it }
                .let { it is Result.Success && it.data.stateChanged }
        }
        return mutation == HostMutationResult.Applied && submission is Result.Success
    }

    /** Atomically orders permanent seat revocation against reconnect recovery. */
    private suspend fun retireAndContinue(playerId: PlayerId): Boolean {
        if (!coordinator.awaitSessionStarted()) return false
        var retired = false
        var reducerInvariantFailed = false
        val mutation = coordinator.applyLifecycleMutation {
            if (playerId !in controller.currentState().public.disconnectedPlayers) {
                return@applyLifecycleMutation false
            }
            when (room.retireDisconnectedMember(playerId)) {
                is Result.Failure -> false
                is Result.Success -> {
                    retired = true
                    when (
                        val submission = controller.submit(
                            MafiaAction.ContinueWithoutPlayer(playerId),
                        )
                    ) {
                        is Result.Success -> submission.data.stateChanged.also { changed ->
                            if (!changed) reducerInvariantFailed = true
                        }
                        is Result.Failure -> {
                            reducerInvariantFailed = true
                            false
                        }
                    }
                }
            }
        }
        if (retired && (reducerInvariantFailed || mutation != HostMutationResult.Applied)) {
            terminate(SessionEndReason.Cancelled)
            return false
        }
        return mutation == HostMutationResult.Applied
    }

    companion object {
        // v2 removes two serialized actions that never represented a legal
        // state transition (AcknowledgePostGame and ReadmitPlayer).
        const val GAME_VERSION: Int = 2
        const val REJOIN_GRACE_MS: Long = 120_000L
        const val HEARTBEAT_INTERVAL_MS: Long = 10_000L
        const val START_RETRY_MS: Long = 250L
        const val START_MAX_RETRY_MS: Long = 2_000L
        const val START_DEADLINE_MS: Long = 20_000L
    }
}
