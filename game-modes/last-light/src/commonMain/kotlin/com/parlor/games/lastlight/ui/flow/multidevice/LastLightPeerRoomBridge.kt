package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.protocol.LastLightActionCodec
import com.parlor.games.lastlight.domain.event.LastLightEvent
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.protocol.LastLightPeerSnapshotValidator
import com.parlor.games.lastlight.protocol.LastLightProjectionCodec
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.session.multidevice.PeerAuthoritativeSessionCoordinator
import com.parlor.session.multidevice.PeerConnectionTracker
import com.parlor.session.multidevice.PlayerSnapshotPayload
import com.parlor.session.multidevice.ShadowSessionController
import com.parlor.session.SubmissionReceipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Passive, version-bound peer mirror for a host-authoritative LastLight game. */
class LastLightPeerRoomBridge(
    private val room: LocalRoom,
    val selfPlayerId: PlayerId,
    initialPublic: LastLightState,
    private val scope: CoroutineScope,
    val protocol: SessionProtocol,
    private val hostLostTimeoutMs: Long = HOST_REJOIN_GRACE_MS,
    private val acceptedStartOffer: HostMessage.SessionStarting? = null,
) {
    private val closeMutex = Mutex()
    private var closed = false
    private val _hostDisconnected = MutableSharedFlow<Unit>(replay = 1)
    val hostDisconnected: SharedFlow<Unit> = _hostDisconnected.asSharedFlow()
    private val _terminalReason = MutableStateFlow<SessionEndReason?>(null)
    val terminalReason = _terminalReason.asStateFlow()
    private val _terminalError = MutableStateFlow<NetError?>(null)
    val terminalError = _terminalError.asStateFlow()
    private val recoveryCounter = LastLightRecoveryEpoch()
    val recoveryEpoch = recoveryCounter.value
    private var lastInstalledRevision = -1L

    private val connectionTracker = PeerConnectionTracker(
        scope = scope,
        hostLostTimeoutMs = hostLostTimeoutMs,
        roomInfo = room.info,
        onHostLossExpired = { markTerminated(SessionEndReason.RejoinExpired) },
    )
    val connectionState = connectionTracker.state
    val connectionEvents: SharedFlow<PeerEvent> = connectionTracker.events

    private val safeInitialPublic = LastLightProjectionPolicy.toPublic(initialPublic).state
    private val expectedPlayers = safeInitialPublic.players

    val controller: ShadowSessionController<LastLightState, LastLightAction, LastLightEvent> =
        ShadowSessionController(
            selfPlayerId = selfPlayerId,
            sendActionToHost = ::sendActionToHost,
            // The peer has not authenticated a state snapshot yet. Treat the
            // placeholder as public-only even if a future caller accidentally
            // passes a canonical host state here.
            initialPublic = PublicProjection(safeInitialPublic),
            initialPrivate = PrivateProjection(safeInitialPublic, selfPlayerId),
        )

    private val coordinator = PeerAuthoritativeSessionCoordinator(
        room = room,
        protocol = protocol,
        selfPlayerId = selfPlayerId,
        scope = scope,
        onSnapshot = ::installSnapshot,
        onSessionEnded = { markTerminated(it.reason) },
        onProtocolViolation = { markTerminated(SessionEndReason.IncompatibleVersion) },
        onSessionEndCommitFailure = { _terminalError.value = it },
        acceptedStartId = protocol.startId,
        acceptedStartOffer = acceptedStartOffer,
    )

    private val connectionJob = scope.launch {
        room.peerEvents.collect(::handleConnectionEvent)
    }
    private val connectionInfoJob = scope.launch {
        room.info.map { it.status }.distinctUntilChanged().collect { status ->
            if (status == RoomInfo.Status.Lost) recoveryCounter.advance()
        }
    }

    val commandProgress = coordinator.commandProgress
    val hasAuthoritativeSnapshot = coordinator.hasAuthoritativeSnapshot
    val initialSnapshotError = coordinator.initialSnapshotError

    suspend fun acknowledgeCommandOutcome(commandId: String) {
        coordinator.acknowledgeCommandOutcome(commandId)
    }

    private suspend fun markTerminated(reason: SessionEndReason) {
        _terminalReason.value = reason
        _hostDisconnected.emit(Unit)
    }

    suspend fun close() = closeMutex.withLock {
        if (closed) return@withLock
        closed = true
        connectionTracker.close()
        connectionJob.cancelAndJoin()
        connectionInfoJob.cancelAndJoin()
        coordinator.close()
    }

    private suspend fun installSnapshot(
        payload: PlayerSnapshotPayload,
        revision: Long,
    ): Boolean {
        val decoded = try {
            val publicState = LastLightProjectionCodec.decodePublic(payload.publicPayload)
            val ownPrivate = LastLightProjectionCodec.decodePrivate(payload.privatePayload)
            Triple(
                publicState,
                ownPrivate,
                publicState.copy(
                    privatePerPlayer = mapOf(selfPlayerId to ownPrivate),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            return false
        }
        val (publicState, ownPrivate, playerState) = decoded
        if (publicState.players != expectedPlayers) return false
        // The public half of an atomic snapshot is a trust boundary. Reject a
        // canonical/host projection instead of installing host-only secrets
        // and relying on UI code not to read them.
        if (LastLightProjectionPolicy.toPublic(publicState).state != publicState) return false
        if (!LastLightPeerSnapshotValidator.isValid(publicState, ownPrivate, selfPlayerId)) return false
        recordSnapshotRecovery(publicState, revision)
        controller.installPlayerSnapshot(
            publicProjection = PublicProjection(publicState),
            playerProjection = PrivateProjection(playerState, selfPlayerId),
        )
        return true
    }

    private fun recordSnapshotRecovery(publicState: LastLightState, revision: Long) {
        // Another survivor's pause may end before Compose observes it. The
        // bounded host outbox may also replace that pause with a later revision.
        if (
            publicState.public.disconnectedPlayers.isNotEmpty() ||
            (lastInstalledRevision >= 0L && revision - lastInstalledRevision > 1L)
        ) {
            recoveryCounter.advance()
        }
        lastInstalledRevision = revision
    }

    private suspend fun sendActionToHost(
        action: LastLightAction,
    ): Result<SubmissionReceipt, SubmitError> {
        if (closed || _terminalReason.value != null || _terminalError.value != null) {
            return Result.Failure(SubmitError.SessionClosed)
        }
        if (connectionState.value.hostLost || connectionState.value.selfOffline) {
            return Result.Failure(SubmitError.SessionSuspended)
        }
        return when (val sent = coordinator.submit(LastLightActionCodec.encode(action))) {
            is Result.Success -> {
                connectionTracker.markSelfOnline()
                Result.Success(
                    SubmissionReceipt(
                        stateChanged = false,
                        awaitingAuthority = true,
                    ),
                )
            }
            is Result.Failure -> {
                if (sent.error == NetError.NotConnected) handleConnectionEvent(PeerEvent.SelfOffline)
                when (sent.error) {
                    NetError.CommandInFlight -> Result.Failure(SubmitError.CommandPending)
                    NetError.SessionSuspended -> Result.Failure(SubmitError.SessionSuspended)
                    else -> Result.Failure(SubmitError.SessionClosed)
                }
            }
        }
    }

    private suspend fun handleConnectionEvent(event: PeerEvent) {
        if (event == PeerEvent.HostLost || event == PeerEvent.SelfOffline) recoveryCounter.advance()
        connectionTracker.handle(event)
    }

    companion object {
        const val HOST_REJOIN_GRACE_MS: Long = 120_000L
    }
}
