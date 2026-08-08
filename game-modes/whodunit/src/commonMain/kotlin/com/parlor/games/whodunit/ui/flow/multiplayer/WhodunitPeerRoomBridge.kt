package com.parlor.games.whodunit.ui.flow.multiplayer

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.session.multidevice.PeerAuthoritativeSessionCoordinator
import com.parlor.session.multidevice.PlayerSnapshotPayload
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
 * Peer-side passive mirror for a host-authoritative Whodunit session.
 *
 * Commands are sequenced and retained for safe retry by
 * [PeerAuthoritativeSessionCoordinator]. The peer never runs the reducer.
 * Only a valid, monotonic atomic public + own-private snapshot can update the
 * shadow controller.
 */
class WhodunitPeerRoomBridge(
    private val room: LocalRoom,
    val selfPlayerId: PlayerId,
    initialPublic: WhodunitState,
    private val scope: CoroutineScope,
    val protocol: SessionProtocol,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    },
    private val hostLostTimeoutMs: Long = HOST_REJOIN_GRACE_MS,
) {
    private val _hostDisconnected = MutableSharedFlow<Unit>(replay = 1)
    val hostDisconnected: SharedFlow<Unit> = _hostDisconnected.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<PeerEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
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

    private var hostLost = false
    private var selfOffline = false
    private var hostLossJob: Job? = null

    private val coordinator = PeerAuthoritativeSessionCoordinator(
        room = room,
        protocol = protocol,
        selfPlayerId = selfPlayerId,
        scope = scope,
        onSnapshot = ::installSnapshot,
        onSessionEnded = { _hostDisconnected.emit(Unit) },
        onProtocolViolation = { _hostDisconnected.emit(Unit) },
    )

    private val connectionJob = scope.launch {
        room.peerEvents.collect(::handleConnectionEvent)
    }

    fun close() {
        hostLossJob?.cancel()
        hostLossJob = null
        connectionJob.cancel()
        coordinator.close()
    }

    private suspend fun installSnapshot(
        payload: PlayerSnapshotPayload,
        @Suppress("UNUSED_PARAMETER") revision: Long,
    ) {
        val merged = runCatching {
            val publicState = json.decodeFromString(
                publicSerializer,
                payload.publicPayload.decodeToString(),
            )
            val ownPrivate = if (payload.privatePayload.isEmpty()) {
                null
            } else {
                json.decodeFromString(
                    privateSerializer,
                    payload.privatePayload.decodeToString(),
                )
            }
            publicState.copy(
                privatePerPlayer = ownPrivate?.let { mapOf(selfPlayerId to it) } ?: emptyMap(),
            )
        }.getOrElse {
            _hostDisconnected.emit(Unit)
            return
        }
        // Both projections derive from the same decoded envelope. Public state
        // is installed last because it is the UI's render-driving StateFlow.
        controller.updatePrivate(PrivateProjection(merged, selfPlayerId))
        controller.updatePublic(PublicProjection(merged))
    }

    private suspend fun sendActionToHost(action: WhodunitAction): Result<Unit, SubmitError> {
        return when (val sent = coordinator.submit(WhodunitActionCodec.encode(action))) {
            is Result.Success -> {
                markSelfOnline()
                Result.Success(Unit)
            }
            is Result.Failure -> {
                if (sent.error == NetError.NotConnected) markSelfOffline()
                if (sent.error == NetError.CommandInFlight) {
                    Result.Failure(SubmitError.CommandPending)
                } else {
                    Result.Failure(SubmitError.SessionClosed)
                }
            }
        }
    }

    private fun markSelfOffline() {
        if (!selfOffline) {
            selfOffline = true
            _connectionEvents.tryEmit(PeerEvent.SelfOffline)
        }
    }

    private fun markSelfOnline() {
        if (selfOffline) {
            selfOffline = false
            _connectionEvents.tryEmit(PeerEvent.SelfOnline)
        }
    }

    private fun handleConnectionEvent(event: PeerEvent) {
        when (event) {
            PeerEvent.HostLost -> {
                if (!hostLost) {
                    hostLost = true
                    _connectionEvents.tryEmit(PeerEvent.HostLost)
                }
                hostLossJob?.cancel()
                hostLossJob = scope.launch {
                    delay(hostLostTimeoutMs)
                    if (hostLost) _hostDisconnected.emit(Unit)
                }
            }
            PeerEvent.HostRestored -> {
                hostLossJob?.cancel()
                hostLossJob = null
                if (hostLost) {
                    hostLost = false
                    _connectionEvents.tryEmit(PeerEvent.HostRestored)
                }
                markSelfOnline()
            }
            PeerEvent.SelfOffline -> markSelfOffline()
            PeerEvent.SelfOnline -> markSelfOnline()
            is PeerEvent.AdmissionRequested,
            is PeerEvent.PeerLeft,
            is PeerEvent.PeerReconnected,
            is PeerEvent.PeerJoined -> Unit
        }
    }

    /** Compatibility test hook: command retry now lives in the coordinator. */
    internal fun queuedActionForTest(): WhodunitAction? = null

    companion object {
        const val HOST_REJOIN_GRACE_MS: Long = 120_000L
    }
}
