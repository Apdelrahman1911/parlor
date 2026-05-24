package com.parlor.games.whodunit.ui.flow.multiplayer

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitState
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
 * against, and wires it to the room transport:
 *
 *  - Inbound `HostMessage.PublicStateSnapshot` → decode the public
 *    `WhodunitState` and push it into the shadow's `publicState`.
 *  - Inbound `HostMessage.PrivateStateForPlayer(target=self)` → decode the
 *    peer's own `WhodunitPrivate`, splice it into the shadow's state so the
 *    UI can render the dossier when the reveal phase asks for it.
 *  - `controller.submit(action)` → encode via [WhodunitActionCodec], send
 *    as `PeerMessage.ActionSubmit` to the host.
 *
 * The peer **never reduces** game state locally. The host is canonical;
 * peers are passive mirrors with input.
 *
 * ## Wave 9H-5 connection lifecycle
 *
 * The bridge surfaces [connectionEvents] for UI to render the offline
 * banner / reconnecting overlay:
 *
 *  - **HostLost / HostRestored** — fallback detection via snapshot silence
 *    (`hostLostTimeoutMs`, default 8s). Also forwarded from
 *    `room.peerEvents` when the underlying transport emits them.
 *  - **SelfOffline / SelfOnline** — synthesised when `sendToHost` returns
 *    [NetError.NotConnected], cleared on the next successful send.
 *
 * ## Offline action queue
 *
 * When `sendToHost` reports the device offline, the action that failed is
 * stamped with the *current* phase and held in a single-slot queue. On
 * reconnect (next successful send or [PeerEvent.SelfOnline] from the
 * transport), the queue replays if the phase still matches. Three
 * sanity guards converge to "no double submit":
 *
 *  1. Single slot — a new offline action replaces any previous queue
 *     entry, so the user always sees only their *latest* intent.
 *  2. Phase stamp — if the canonical state moved to a different phase
 *     while the device was offline, the queued action is dropped.
 *  3. Dropped-player check — if the peer's own `PlayerId` is now in
 *     `public.droppedPlayers`, the queued action is dropped regardless
 *     of phase (the peer reconnected as a spectator).
 *
 * The authority gate + reducer enforce the same dropped-player rule, so
 * even if the queue mistakenly let an action through it would still be
 * rejected at the host. Belt + suspenders.
 */
class WhodunitPeerRoomBridge(
    private val room: LocalRoom,
    val selfPlayerId: PlayerId,
    initialPublic: WhodunitState,
    private val scope: CoroutineScope,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    },
    /**
     * Snapshot-silence threshold before the bridge synthesises
     * [PeerEvent.HostLost]. Real LAN sessions tune this against measured
     * snapshot cadence; tests inject smaller values.
     */
    private val hostLostTimeoutMs: Long = DEFAULT_HOST_LOST_TIMEOUT_MS,
) {
    private val _hostDisconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires once when the host signals end-of-session or the room reports a drop. */
    val hostDisconnected: SharedFlow<Unit> = _hostDisconnected.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<PeerEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    /**
     * Stream of connection-lifecycle events the UI consumes for the
     * reconnecting overlay, offline banner, and "queued action" toasts.
     * Merges transport-sourced events with bridge-synthesised ones.
     */
    val connectionEvents: SharedFlow<PeerEvent> = _connectionEvents.asSharedFlow()

    private val publicSerializer = WhodunitState.serializer()
    private val privateSerializer = WhodunitPrivate.serializer()

    val controller: ShadowSessionController<WhodunitState, WhodunitAction, WhodunitEvent> =
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
        val action: WhodunitAction,
        val phase: WhodunitPhase,
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
        // Preserve the slice the peer was holding so the dossier doesn't blank
        // if a public snapshot arrives between two private deliveries.
        val merged = decoded.copy(
            privatePerPlayer = controller.publicState.value.state.privatePerPlayer +
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

    private fun selfPrivateOrNull(): WhodunitPrivate? =
        controller.publicState.value.state.privatePerPlayer[selfPlayerId]

    // ============================================================ HostLost watchdog ==

    /**
     * Restart the snapshot-silence watchdog. Every public snapshot calls
     * this — if [hostLostTimeoutMs] elapses without another snapshot, we
     * emit [PeerEvent.HostLost]. The next snapshot after a [HostLost]
     * emits [PeerEvent.HostRestored].
     */
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
                    is PeerEvent.PeerJoined -> Unit  // host-side / handled by sendActionToHost path
                }
            }
        }
    }

    // ============================================================ Outbox + queue ==

    private suspend fun sendActionToHost(action: WhodunitAction): Result<Unit, SubmitError> {
        val bytes = WhodunitActionCodec.encode(action)
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

    private fun onSendFailed(error: NetError, action: WhodunitAction) {
        if (error == NetError.NotConnected) {
            if (!selfOffline) {
                selfOffline = true
                _connectionEvents.tryEmit(PeerEvent.SelfOffline)
            }
            // Single-slot queue: latest user intent replaces any prior queued
            // action. The phase stamp is the action's phase *at queue time*,
            // not at replay time — that's the whole point of the gate.
            queuedAction = QueuedAction(
                action = action,
                phase = controller.publicState.value.state.phase,
            )
        }
    }

    /**
     * Try to submit the queued action. Drop it if the phase has moved on
     * or if this peer has been dropped while offline.
     */
    private fun replayQueuedAction() {
        val queued = queuedAction ?: return
        queuedAction = null
        val state = controller.publicState.value.state
        if (selfPlayerId in state.public.droppedPlayers) return
        if (state.phase != queued.phase) return
        scope.launch { controller.submit(queued.action) }
    }

    /** Test hook: read current queue depth. */
    internal fun queuedActionForTest(): WhodunitAction? = queuedAction?.action

    private companion object {
        const val DEFAULT_HOST_LOST_TIMEOUT_MS: Long = 8_000L
    }
}
