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
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.SendTarget
import com.parlor.networking.security.SecureIds
import com.parlor.session.multidevice.CommandApplication
import com.parlor.session.multidevice.HostAuthoritativeSessionCoordinator
import com.parlor.session.multidevice.HostMutationResult
import com.parlor.session.multidevice.PlayerSnapshotPayload
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.engine.session.SubmitError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    sessionIdGenerator: () -> String = SecureIds::id128,
    reconcileRoomTopology: Boolean = false,
) {
    val protocol: SessionProtocol = SessionProtocol(
        sessionId = SessionId(sessionIdGenerator()),
        gameId = WhodunitIds.GameId,
        gameVersion = GAME_VERSION,
    )

    private val publicStateSerializer = WhodunitState.serializer()
    private val privateSerializer = WhodunitPrivate.serializer()
    private val remotePlayers = players.map(Player::id).toSet() - room.selfPlayerId
    private val graceJobs = mutableMapOf<PlayerId, Job>()
    private var lastSessionStarting: HostMessage.SessionStarting? = null
    private var terminated = false
    private var pausedByAppLifecycle = false

    private val coordinator = HostAuthoritativeSessionCoordinator(
        room = room,
        protocol = protocol,
        remotePlayers = remotePlayers,
        scope = scope,
        applyCommand = ::applyRemoteCommand,
        snapshotFor = ::snapshotFor,
        heartbeatIntervalMs = heartbeatIntervalMs,
    )

    private val peerEventsJob = if (reconcileRoomTopology) {
        // Production room membership is a replaying StateFlow. Using it as the
        // topology authority closes the freeze-roster -> bridge-subscription
        // race: the first collection always reconciles the current connection
        // state, even if PeerLeft happened before this bridge existed.
        scope.launch { room.members.collect(::reconcileMembers) }
    } else {
        // Deterministic bridge fixtures expose only synthetic PeerEvents.
        scope.launch { room.peerEvents.collect(::handlePeerEvent) }
    }

    /**
     * Freezes the canonical game clock for the whole transport interruption.
     * A lifecycle-owned pause is resumed only after every retained peer has
     * restored admission and the room is Active again. A pause chosen by a
     * player before backgrounding remains a player-owned pause and is never
     * lifted automatically.
     */
    private val roomLifecycleJob = scope.launch {
        room.lifecycle.collect { lifecycle ->
            when (lifecycle) {
                RoomLifecycleState.Active -> {
                    if (pausedByAppLifecycle && applyLifecycleAction(WhodunitAction.Resume)) {
                        pausedByAppLifecycle = false
                    }
                }
                is RoomLifecycleState.Suspended,
                is RoomLifecycleState.Resuming -> {
                    if (!controller.currentState().public.paused) {
                        pausedByAppLifecycle = applyLifecycleAction(WhodunitAction.Pause)
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
    suspend fun announceStart(caseId: String, modeId: String) {
        val starting = HostMessage.SessionStarting(
            caseId = caseId,
            modeId = modeId,
            players = players,
            sessionNonce = room.info.value.code.hashCode().toLong(),
            header = SessionEnvelopeHeader(
                protocol = protocol.protocol,
                sessionId = protocol.sessionId,
                gameId = protocol.gameId,
                gameVersion = protocol.gameVersion,
                messageId = SecureIds.id128(),
                sequence = 0L,
            ),
        )
        lastSessionStarting = starting
        room.send(SendTarget.Broadcast, starting)
        coordinator.publishState(incrementRevision = false)
    }

    /**
     * Serializes a host-originated action with remote commands and publishes
     * exactly one revision when the reducer commits a change.
     */
    suspend fun submitHostAction(
        action: WhodunitAction,
    ): Result<SubmissionReceipt, SubmitError> {
        var submission: Result<SubmissionReceipt, SubmitError>? = null
        val mutation = coordinator.applyHostMutation {
            controller.submit(action).also { submission = it }
                .let { it is Result.Success && it.data.stateChanged }
        }
        return when (mutation) {
            HostMutationResult.Closed -> Result.Failure(SubmitError.SessionClosed)
            HostMutationResult.Applied,
            HostMutationResult.Unchanged -> checkNotNull(submission)
        }
    }

    /** Delivers a terminal envelope before the caller navigates away. */
    suspend fun terminate(reason: SessionEndReason = SessionEndReason.HostLeft) {
        if (terminated) return
        terminated = true
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
        if (terminated || playerId !in remotePlayers) return false
        graceJobs.remove(playerId)?.cancel()
        return applyLifecycleAction(WhodunitAction.ContinueWithoutPlayer(playerId))
    }

    fun close() {
        graceJobs.values.forEach(Job::cancel)
        graceJobs.clear()
        peerEventsJob.cancel()
        roomLifecycleJob.cancel()
        coordinator.close()
    }

    private suspend fun applyRemoteCommand(
        actor: PlayerId,
        payload: ByteArray,
    ): CommandApplication {
        val action = runCatching { WhodunitActionCodec.decode(payload) }.getOrNull()
            ?: return CommandApplication.InvalidAction
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
        applyLifecycleAction(WhodunitAction.MarkPlayerDisconnected(playerId))
        if (
            controller.currentState().phase != WhodunitPhase.PostGame &&
            graceJobs[playerId] == null
        ) {
            graceJobs[playerId] = scope.launch {
                delay(rejoinGraceMs)
                graceJobs.remove(playerId)
                if (playerId in controller.currentState().public.disconnectedPlayers) {
                    continueWithout(playerId)
                }
            }
        }
    }

    private suspend fun handlePeerReconnected(playerId: PlayerId) {
        if (playerId !in remotePlayers) return
        graceJobs.remove(playerId)?.cancel()
        // A fresh rejoin flow needs the start envelope before its first atomic
        // snapshot. An already-running bridge consumes and ignores this
        // idempotent legacy transition message.
        lastSessionStarting?.let { room.send(SendTarget.Direct(playerId), it) }
        val changed = applyLifecycleAction(WhodunitAction.MarkPlayerReconnected(playerId))
        if (!changed) coordinator.publishState(incrementRevision = false)
    }

    private suspend fun applyLifecycleAction(action: WhodunitAction): Boolean {
        var submission: Result<SubmissionReceipt, SubmitError>? = null
        val mutation = coordinator.applyHostMutation {
            controller.submit(action).also { submission = it }
                .let { it is Result.Success && it.data.stateChanged }
        }
        return mutation == HostMutationResult.Applied && submission is Result.Success
    }

    companion object {
        const val GAME_VERSION: Int = 1
        const val REJOIN_GRACE_MS: Long = 120_000L
        const val HEARTBEAT_INTERVAL_MS: Long = 10_000L
    }
}
