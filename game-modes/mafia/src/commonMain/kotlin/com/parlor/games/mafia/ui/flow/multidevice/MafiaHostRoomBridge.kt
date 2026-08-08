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
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.SendTarget
import com.parlor.networking.security.SecureIds
import com.parlor.session.multidevice.CommandApplication
import com.parlor.session.multidevice.HostAuthoritativeSessionCoordinator
import com.parlor.session.multidevice.PlayerSnapshotPayload
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    sessionIdGenerator: () -> String = SecureIds::id128,
) {
    val protocol: SessionProtocol = SessionProtocol(
        sessionId = SessionId(sessionIdGenerator()),
        gameId = MafiaIds.GameId,
        gameVersion = GAME_VERSION,
    )

    private val publicStateSerializer = MafiaState.serializer()
    private val privateSerializer = MafiaPrivate.serializer()
    private val remotePlayers = players.map(Player::id).toSet() - room.selfPlayerId
    private val graceJobs = mutableMapOf<PlayerId, Job>()
    private var lastSessionStarting: HostMessage.SessionStarting? = null
    private var terminated = false

    private val coordinator = HostAuthoritativeSessionCoordinator(
        room = room,
        protocol = protocol,
        remotePlayers = remotePlayers,
        scope = scope,
        applyCommand = ::applyRemoteCommand,
        snapshotFor = ::snapshotFor,
        heartbeatIntervalMs = heartbeatIntervalMs,
    )

    private val peerEventsJob = scope.launch {
        room.peerEvents.collect(::handlePeerEvent)
    }

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

    suspend fun publishHostMutation() {
        coordinator.publishState()
    }

    suspend fun terminate(reason: SessionEndReason = SessionEndReason.HostLeft) {
        if (terminated) return
        terminated = true
        coordinator.end(reason)
    }

    /**
     * Applies the host's explicit decision before the grace timer expires.
     *
     * Lifecycle recovery stays bridge-owned so gameplay commands remain
     * blocked while any seat is transiently disconnected.
     */
    suspend fun continueWithout(playerId: PlayerId): Boolean {
        if (terminated || playerId !in remotePlayers) return false
        graceJobs.remove(playerId)?.cancel()
        return applyLifecycleAction(MafiaAction.ContinueWithoutPlayer(playerId))
    }

    fun close() {
        graceJobs.values.forEach(Job::cancel)
        graceJobs.clear()
        peerEventsJob.cancel()
        coordinator.close()
    }

    private suspend fun applyRemoteCommand(
        actor: PlayerId,
        payload: ByteArray,
    ): CommandApplication {
        val action = runCatching { MafiaActionCodec.decode(payload) }.getOrNull()
            ?: return CommandApplication.InvalidAction
        val before = controller.hostState.value.state
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
        return when (controller.submit(action)) {
            is Result.Failure -> CommandApplication.InvalidAction
            is Result.Success -> {
                if (controller.hostState.value.state == before) {
                    CommandApplication.InvalidAction
                } else {
                    CommandApplication.Applied
                }
            }
        }
    }

    private suspend fun snapshotFor(playerId: PlayerId): PlayerSnapshotPayload {
        val state = controller.hostState.value.state
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

    private suspend fun handlePeerLeft(playerId: PlayerId) {
        if (playerId !in remotePlayers) return
        applyLifecycleAction(MafiaAction.MarkPlayerDisconnected(playerId))
        if (
            controller.hostState.value.state.phase != MafiaPhase.PostGame &&
            graceJobs[playerId] == null
        ) {
            graceJobs[playerId] = scope.launch {
                delay(rejoinGraceMs)
                graceJobs.remove(playerId)
                if (playerId in controller.hostState.value.state.public.disconnectedPlayers) {
                    continueWithout(playerId)
                }
            }
        }
    }

    private suspend fun handlePeerReconnected(playerId: PlayerId) {
        if (playerId !in remotePlayers) return
        graceJobs.remove(playerId)?.cancel()
        lastSessionStarting?.let { room.send(SendTarget.Direct(playerId), it) }
        val changed = applyLifecycleAction(MafiaAction.MarkPlayerReconnected(playerId))
        if (!changed) coordinator.publishState(incrementRevision = false)
    }

    private suspend fun applyLifecycleAction(action: MafiaAction): Boolean {
        val before = controller.hostState.value.state
        val result = controller.submit(action)
        val changed = result is Result.Success && controller.hostState.value.state != before
        if (changed) coordinator.publishState()
        return changed
    }

    companion object {
        const val GAME_VERSION: Int = 1
        const val REJOIN_GRACE_MS: Long = 120_000L
        const val HEARTBEAT_INTERVAL_MS: Long = 10_000L
    }
}
