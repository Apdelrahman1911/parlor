package com.parlor.games.mafia.ui.flow.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.action.MafiaActionCodec
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.session.multidevice.ShadowSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Peer-side bridge. Holds a [ShadowSessionController] that the UI renders
 * against, and wires it to the room transport. Mirrors the Whodunit pattern
 * one-for-one — only the state/action/event types and codec change.
 *
 * The peer **never reduces** game state. The host is the canonical reducer;
 * every action the peer submits is sent over the wire, and the peer's shadow
 * is updated only when the host's snapshot arrives.
 *
 * ## Privacy invariant (structural)
 *
 * The host bridge ships each peer only their own [MafiaPrivate] slice via
 * `SendTarget.Direct(playerId)`. A peer's inbox accepts only
 * `PrivateStateForPlayer(target = self)`. Every other slice (other roles,
 * other players' Mafia coordination, other players' detective results)
 * never reaches this device — UI hiding is unnecessary because the data
 * was never sent.
 *
 * ## Connection lifecycle
 *
 * [connectionEvents] surfaces:
 *  - `HostLost` / `HostRestored` — snapshot-silence watchdog (8s default).
 *  - `SelfOffline` / `SelfOnline` — synthesised when `sendToHost` reports
 *    [NetError.NotConnected], cleared on the next successful send.
 *
 * ## Offline action queue
 *
 * Single-slot, phase-stamped. On disconnect the failed action is held; on
 * reconnect it replays only if (a) the phase still matches and (b) the
 * peer is not in `public.droppedPlayers`. The authority gate enforces the
 * same dropped check on the host side as belt-and-suspenders.
 */
class MafiaPeerRoomBridge(
    private val room: LocalRoom,
    val selfPlayerId: PlayerId,
    initialPublic: MafiaState,
    private val scope: CoroutineScope,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    },
    private val hostLostTimeoutMs: Long = DEFAULT_HOST_LOST_TIMEOUT_MS,
) {
    private val _hostDisconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hostDisconnected: SharedFlow<Unit> = _hostDisconnected.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<PeerEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val connectionEvents: SharedFlow<PeerEvent> = _connectionEvents.asSharedFlow()

    private val publicSerializer = MafiaState.serializer()
    private val privateSerializer = MafiaPrivate.serializer()

    val controller: ShadowSessionController<MafiaState, MafiaAction, MafiaEvent> =
        ShadowSessionController(
            selfPlayerId = selfPlayerId,
            sendActionToHost = ::sendActionToHost,
            initialPublic = PublicProjection(initialPublic),
            initialPrivate = PrivateProjection(initialPublic, selfPlayerId),
        )

    private val jobs: MutableList<Job> = mutableListOf()

    private var hostLostWatchdog: Job? = null
    private var hostLost: Boolean = false
    private var selfOffline: Boolean = false

    private data class QueuedAction(
        val action: MafiaAction,
        val phase: MafiaPhase,
    )
    private var queuedAction: QueuedAction? = null

    init {
        startInbox()
        startConnectionEventForwarder()
        resetHostLostWatchdog()
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        hostLostWatchdog?.cancel()
        hostLostWatchdog = null
    }

    // ============================================================ Inbox ==

    private fun startInbox() {
        jobs += scope.launch {
            room.incoming.collect { msg ->
                when (msg) {
                    is HostMessage.PublicStateSnapshot -> handlePublic(msg)
                    is HostMessage.PrivateStateForPlayer -> handlePrivate(msg)
                    HostMessage.EndSession -> _hostDisconnected.tryEmit(Unit)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun handlePublic(msg: HostMessage.PublicStateSnapshot) {
        val decoded = runCatching {
            json.decodeFromString(publicSerializer, msg.payload.decodeToString())
        }.getOrNull() ?: return
        // Preserve any private slice the peer was already holding so the
        // role card / coordination snapshot doesn't blank when a public-only
        // snapshot arrives between two private deliveries.
        val merged = decoded.copy(
            privatePerPlayer = decoded.privatePerPlayer +
                (selfPrivateOrNull()?.let { mapOf(selfPlayerId to it) } ?: emptyMap()),
        )
        controller.updatePublic(PublicProjection(merged))
        controller.updatePrivate(PrivateProjection(merged, selfPlayerId))
        onSnapshotReceived()
    }

    private suspend fun handlePrivate(msg: HostMessage.PrivateStateForPlayer) {
        if (msg.target != selfPlayerId) return
        val slice = runCatching {
            json.decodeFromString(privateSerializer, msg.payload.decodeToString())
        }.getOrNull() ?: return
        val current = controller.publicState.value.state
        val merged = current.copy(
            privatePerPlayer = current.privatePerPlayer + (selfPlayerId to slice),
        )
        controller.updatePublic(PublicProjection(merged))
        controller.updatePrivate(PrivateProjection(merged, selfPlayerId))
    }

    private fun selfPrivateOrNull(): MafiaPrivate? =
        controller.publicState.value.state.privatePerPlayer[selfPlayerId]

    // ============================================================ HostLost watchdog ==

    private fun resetHostLostWatchdog() {
        hostLostWatchdog?.cancel()
        hostLostWatchdog = scope.launch {
            delay(hostLostTimeoutMs)
            if (!hostLost) {
                hostLost = true
                _connectionEvents.tryEmit(PeerEvent.HostLost)
            }
        }
    }

    private fun onSnapshotReceived() {
        if (hostLost) {
            hostLost = false
            _connectionEvents.tryEmit(PeerEvent.HostRestored)
        }
        resetHostLostWatchdog()
    }

    private fun startConnectionEventForwarder() {
        jobs += scope.launch {
            room.peerEvents.collect { event ->
                when (event) {
                    PeerEvent.HostLost -> {
                        if (!hostLost) {
                            hostLost = true
                            _connectionEvents.tryEmit(event)
                        }
                    }
                    PeerEvent.HostRestored -> {
                        if (hostLost) {
                            hostLost = false
                            _connectionEvents.tryEmit(event)
                        }
                    }
                    PeerEvent.SelfOffline,
                    PeerEvent.SelfOnline,
                    is PeerEvent.PeerLeft,
                    is PeerEvent.PeerReconnected,
                    is PeerEvent.PeerJoined -> Unit
                }
            }
        }
    }

    // ============================================================ Outbox + queue ==

    private suspend fun sendActionToHost(action: MafiaAction): Result<Unit, SubmitError> {
        val bytes = MafiaActionCodec.encode(action)
        val sendResult = room.sendToHost(
            PeerMessage.ActionSubmit(sender = selfPlayerId, payload = bytes),
        )
        return when (sendResult) {
            is Result.Success -> {
                onSendSucceeded()
                Result.Success(Unit)
            }
            is Result.Failure -> {
                onSendFailed(sendResult.error, action)
                Result.Failure(SubmitError.SessionClosed)
            }
        }
    }

    private fun onSendSucceeded() {
        if (selfOffline) {
            selfOffline = false
            _connectionEvents.tryEmit(PeerEvent.SelfOnline)
            replayQueuedAction()
        }
    }

    private fun onSendFailed(error: NetError, action: MafiaAction) {
        if (error == NetError.NotConnected) {
            if (!selfOffline) {
                selfOffline = true
                _connectionEvents.tryEmit(PeerEvent.SelfOffline)
            }
            queuedAction = QueuedAction(
                action = action,
                phase = controller.publicState.value.state.phase,
            )
        }
    }

    private fun replayQueuedAction() {
        val queued = queuedAction ?: return
        queuedAction = null
        val state = controller.publicState.value.state
        if (selfPlayerId in state.public.droppedPlayers) return
        if (state.phase != queued.phase) return
        scope.launch { controller.submit(queued.action) }
    }

    /** Test hook: read current queue contents. */
    internal fun queuedActionForTest(): MafiaAction? = queuedAction?.action

    private companion object {
        const val DEFAULT_HOST_LOST_TIMEOUT_MS: Long = 8_000L
    }
}
