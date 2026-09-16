package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.protocol.LastLightActionCodec
import com.parlor.games.lastlight.domain.authority.LastLightActionAuthority
import com.parlor.games.lastlight.domain.event.LastLightEvent
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.protocol.LastLightProjectionCodec
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LastLight's single-writer, host-authoritative room bridge.
 *
 * Each accepted peer command is authenticated by transport identity,
 * actor-authorized, ordered and deduplicated before it reaches the reducer.
 * State replication is one atomic public + own-private envelope per revision.
 */
class LastLightHostRoomBridge(
    private val controller: PassAndPlaySessionController<LastLightState, LastLightAction, LastLightEvent>,
    private val room: LocalRoom,
    private val players: List<Player>,
    private val scope: CoroutineScope,
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
        gameId = LastLightIds.GameId,
        gameVersion = GAME_VERSION,
    )

    private val remotePlayers = players.map(Player::id).toSet() - room.selfPlayerId
    private val lifecycleMutex = Mutex()
    /** Serializes topology callbacks with grace expiry and completed rejoins. */
    private val recoveryTransitionMutex = Mutex()
    private val closeMutex = Mutex()
    private val graceJobs = mutableMapOf<PlayerId, Job>()
    private val rejoinJobs = mutableMapOf<PlayerId, Job>()
    /** Transport-offline seats, including eliminated seated participants. */
    private val offlinePlayers = mutableSetOf<PlayerId>()
    private var terminated = false
    private val _terminalReason = MutableStateFlow<SessionEndReason?>(null)
    val terminalReason = _terminalReason.asStateFlow()
    private val recoveryCounter = LastLightRecoveryEpoch()
    val recoveryEpoch = recoveryCounter.value
    private var closed = false
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
            // Membership state can conflate offline -> online before its
            // collector runs. Keep the raw interruption for privacy without
            // giving a second observer ownership of domain topology changes.
            bridgeScope.launch(start = CoroutineStart.UNDISPATCHED) {
                room.peerEvents.collect(::recordRecoveryEvent)
            }
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

    /** Applies only the host seat's actions; lifecycle changes remain bridge-owned. */
    suspend fun submitHostAction(
        action: LastLightAction,
    ): Result<SubmissionReceipt, SubmitError> {
        if (!coordinator.awaitSessionStarted()) {
            return Result.Failure(SubmitError.SessionClosed)
        }
        var submission: Result<SubmissionReceipt, SubmitError>? = null
        val mutation = coordinator.applyHostMutation {
            val before = controller.currentState()
            when {
                isClosedOrPaused(before) -> {
                    submission = Result.Failure(SubmitError.IllegalForPhase)
                    false
                }
                !isPlayerActionAllowed(action, room.selfPlayerId, before) -> {
                    submission = Result.Failure(SubmitError.IllegalForPhase)
                    false
                }
                else -> controller.submit(action).also { submission = it }
                    .let { it is Result.Success && it.data.stateChanged }
            }
        }
        return when (mutation) {
            HostMutationResult.Closed,
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
                    offlinePlayers.clear()
                }
            }
        } ?: return
        jobsToCancel.forEach { it.cancelAndJoin() }
        coordinator.end(reason)
        _terminalReason.value = reason
    }

    suspend fun close() = closeMutex.withLock {
        if (closed) return@withLock
        closed = true
        val jobsToCancel = lifecycleMutex.withLock {
            terminated = true
            (graceJobs.values + rejoinJobs.values).also {
                graceJobs.clear()
                rejoinJobs.clear()
                offlinePlayers.clear()
            }
        }
        jobsToCancel.forEach { it.cancelAndJoin() }
        coordinator.close()
        bridgeJob.cancelAndJoin()
    }

    private suspend fun applyRemoteCommand(
        actor: PlayerId,
        payload: ByteArray,
    ): CommandApplication {
        val action = try {
            LastLightActionCodec.decode(payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            return CommandApplication.InvalidAction
        }
        val before = controller.currentState()
        if (isClosedOrPaused(before)) return CommandApplication.InvalidAction
        if (!isPlayerActionAllowed(action, actor, before)) return CommandApplication.Unauthorized
        return when (val result = controller.submit(action)) {
            is Result.Failure -> CommandApplication.InvalidAction
            is Result.Success -> if (result.data.stateChanged) {
                CommandApplication.Applied
            } else {
                CommandApplication.InvalidAction
            }
        }
    }

    private suspend fun isClosedOrPaused(state: LastLightState): Boolean =
        lifecycleMutex.withLock {
            terminated || state.public.disconnectedPlayers.isNotEmpty() ||
                state.public.roster.any { !it.eliminated && PlayerId(it.id) in offlinePlayers }
        }

    private fun isPlayerActionAllowed(
        action: LastLightAction,
        actor: PlayerId,
        state: LastLightState,
    ): Boolean {
        if (actor !in players.map(Player::id)) return false
        val submittedByPlayer = when (action) {
            is LastLightAction.PlayCards,
            is LastLightAction.Challenge,
            LastLightAction.NextRound -> true
            else -> false
        }
        return submittedByPlayer && LastLightActionAuthority.isAllowed(
            action = action,
            senderId = actor,
            hostId = room.selfPlayerId,
            state = state,
        )
    }

    private suspend fun snapshotFor(playerId: PlayerId): PlayerSnapshotPayload {
        val state = controller.currentState()
        return PlayerSnapshotPayload(
            publicPayload = LastLightProjectionCodec.encodePublic(state),
            privatePayload = LastLightProjectionCodec.encodePrivate(
                LastLightProjectionPolicy.toPlayer(state, playerId).state.privatePerPlayer.getValue(playerId),
            ),
        )
    }

    private suspend fun recordRecoveryEvent(event: PeerEvent) {
        if (event !is PeerEvent.PeerLeft || event.playerId !in remotePlayers) return
        lifecycleMutex.withLock {
            if (!terminated) recoveryCounter.advance()
        }
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
            val transportOffline = lifecycleMutex.withLock { playerId in offlinePlayers }
            when {
                playerId !in connected && !transportOffline -> handlePeerLeft(playerId)
                playerId in connected && transportOffline -> handlePeerReconnected(playerId)
            }
        }
    }

    private suspend fun handlePeerLeft(playerId: PlayerId) = recoveryTransitionMutex.withLock {
        if (playerId !in remotePlayers) return@withLock
        var interruptedRejoin: Job? = null
        val accepted = lifecycleMutex.withLock {
            if (terminated) {
                false
            } else {
                if (offlinePlayers.add(playerId)) recoveryCounter.advance()
                interruptedRejoin = rejoinJobs.remove(playerId)
                true
            }
        }
        if (!accepted) return@withLock
        interruptedRejoin?.cancel()

        applyLifecycleAction(LastLightAction.MarkPlayerDisconnected(playerId))
        val state = controller.currentState()
        if (
            playerId in state.public.disconnectedPlayers &&
            state.phase != GamePhase.FINISHED
        ) {
            scheduleGraceExpiry(playerId)
        }
    }

    private suspend fun handlePeerReconnected(playerId: PlayerId) =
        recoveryTransitionMutex.withLock {
            if (playerId !in remotePlayers) return@withLock
            val mayRejoin = lifecycleMutex.withLock {
                !terminated &&
                    playerId in offlinePlayers &&
                    rejoinJobs[playerId] == null
            }
            if (mayRejoin) scheduleRejoin(playerId)
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

    private suspend fun expireGrace(playerId: PlayerId, graceJob: Job) =
        recoveryTransitionMutex.withLock {
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
            if (!ownsDeadline) return@withLock
            rejoinToCancel?.cancel()
            if (playerId in controller.currentState().public.disconnectedPlayers) {
                // Losing a surviving seat cannot remove its secret hand or turn a
                // pending claim into another game. Publish an explicit terminal
                // domain state, then close the authenticated session.
                applyLifecycleAction(LastLightAction.EndGame)
                terminate(SessionEndReason.Cancelled)
            }
        }

    private suspend fun scheduleRejoin(playerId: PlayerId) {
        lateinit var rejoinJob: Job
        rejoinJob = bridgeScope.launch(start = CoroutineStart.LAZY) {
            try {
                while (ownsRejoinAttempt(playerId, rejoinJob)) {
                    val result = coordinator.resendStart(
                        playerId = playerId,
                        initialRetryMs = startRetryMs,
                        maxRetryMs = startMaxRetryMs,
                        readyDeadlineMs = startDeadlineMs,
                        commitAckDeadlineMs = startDeadlineMs,
                    )
                    if (result is Result.Success) {
                        completeRejoin(playerId, rejoinJob)
                        return@launch
                    }
                    delay(startMaxRetryMs)
                }
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

    private suspend fun ownsRejoinAttempt(playerId: PlayerId, job: Job): Boolean =
        lifecycleMutex.withLock {
            !terminated &&
                playerId in offlinePlayers &&
                rejoinJobs[playerId] === job
        }

    private suspend fun completeRejoin(playerId: PlayerId, rejoinJob: Job) =
        recoveryTransitionMutex.withLock {
            val ownsAttempt = lifecycleMutex.withLock {
                !terminated &&
                    rejoinJobs[playerId] === rejoinJob &&
                    playerId in offlinePlayers
            }
            if (!ownsAttempt) return@withLock

            val wasGameplayDisconnected =
                playerId in controller.currentState().public.disconnectedPlayers
            val changed = if (wasGameplayDisconnected) {
                applyLifecycleAction(LastLightAction.MarkPlayerReconnected(playerId))
            } else {
                false
            }
            if (
                wasGameplayDisconnected &&
                playerId in controller.currentState().public.disconnectedPlayers
            ) {
                return@withLock
            }

            var graceToCancel: Job? = null
            val completed = lifecycleMutex.withLock {
                if (
                    terminated ||
                    rejoinJobs[playerId] !== rejoinJob ||
                    playerId !in offlinePlayers
                ) {
                    false
                } else {
                    offlinePlayers.remove(playerId)
                    graceToCancel = graceJobs.remove(playerId)
                    true
                }
            }
            if (!completed) return@withLock
            graceToCancel?.cancel()
            if (!changed) coordinator.publishState(incrementRevision = false)
        }

    private suspend fun applyLifecycleAction(action: LastLightAction): Boolean {
        if (!coordinator.awaitSessionStarted()) return false
        var submission: Result<SubmissionReceipt, SubmitError>? = null
        val mutation = coordinator.applyLifecycleMutation {
            controller.submit(action).also { submission = it }
                .let { it is Result.Success && it.data.stateChanged }
        }
        return mutation == HostMutationResult.Applied && submission is Result.Success
    }

    companion object {
        const val GAME_VERSION: Int = 1
        const val REJOIN_GRACE_MS: Long = 120_000L
        const val HEARTBEAT_INTERVAL_MS: Long = 10_000L
        const val START_RETRY_MS: Long = 250L
        const val START_MAX_RETRY_MS: Long = 2_000L
        const val START_DEADLINE_MS: Long = 20_000L
    }
}
