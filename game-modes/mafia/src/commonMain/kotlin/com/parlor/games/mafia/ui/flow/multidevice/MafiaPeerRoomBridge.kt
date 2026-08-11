package com.parlor.games.mafia.ui.flow.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.action.MafiaActionCodec
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy
import com.parlor.games.mafia.domain.state.MafiaPrivate
import com.parlor.games.mafia.domain.state.MafiaPeerSnapshotValidator
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.session.multidevice.PeerAuthoritativeSessionCoordinator
import com.parlor.session.multidevice.PeerConnectionTracker
import com.parlor.session.multidevice.PlayerSnapshotPayload
import com.parlor.session.multidevice.ShadowSessionController
import com.parlor.session.SubmissionReceipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Passive, version-bound peer mirror for a host-authoritative Mafia game. */
class MafiaPeerRoomBridge(
    private val room: LocalRoom,
    val selfPlayerId: PlayerId,
    initialPublic: MafiaState,
    private val scope: CoroutineScope,
    val protocol: SessionProtocol,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    },
    private val hostLostTimeoutMs: Long = HOST_REJOIN_GRACE_MS,
) {
    private val closeMutex = Mutex()
    private var closed = false
    private val _hostDisconnected = MutableSharedFlow<Unit>(replay = 1)
    val hostDisconnected: SharedFlow<Unit> = _hostDisconnected.asSharedFlow()

    private val connectionTracker = PeerConnectionTracker(
        scope = scope,
        hostLostTimeoutMs = hostLostTimeoutMs,
        roomInfo = room.info,
        onHostLossExpired = { _hostDisconnected.emit(Unit) },
    )
    val connectionState = connectionTracker.state
    val connectionEvents: SharedFlow<PeerEvent> = connectionTracker.events

    private val publicSerializer = MafiaState.serializer()
    private val privateSerializer = MafiaPrivate.serializer()
    private val safeInitialPublic = MafiaProjectionPolicy.toPublic(initialPublic).state
    private val expectedPlayers = safeInitialPublic.players

    val controller: ShadowSessionController<MafiaState, MafiaAction, MafiaEvent> =
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
        onSessionEnded = { _hostDisconnected.emit(Unit) },
        onProtocolViolation = { _hostDisconnected.emit(Unit) },
        acceptedStartId = protocol.startId,
    )

    private val connectionJob = scope.launch {
        room.peerEvents.collect(::handleConnectionEvent)
    }

    val commandProgress = coordinator.commandProgress
    val hasAuthoritativeSnapshot = coordinator.hasAuthoritativeSnapshot
    val initialSnapshotError = coordinator.initialSnapshotError

    suspend fun acknowledgeCommandOutcome(commandId: String) {
        coordinator.acknowledgeCommandOutcome(commandId)
    }

    suspend fun close() = closeMutex.withLock {
        if (closed) return@withLock
        closed = true
        connectionTracker.close()
        connectionJob.cancelAndJoin()
        coordinator.close()
    }

    private suspend fun installSnapshot(
        payload: PlayerSnapshotPayload,
        @Suppress("UNUSED_PARAMETER") revision: Long,
    ): Boolean {
        val decoded = try {
            val publicState = json.decodeFromString(
                publicSerializer,
                payload.publicPayload.decodeToString(throwOnInvalidSequence = true),
            )
            val ownPrivate = if (payload.privatePayload.isEmpty()) {
                null
            } else {
                json.decodeFromString(
                    privateSerializer,
                    payload.privatePayload.decodeToString(throwOnInvalidSequence = true),
                )
            }
            Triple(
                publicState,
                ownPrivate,
                publicState.copy(
                    privatePerPlayer = ownPrivate?.let { mapOf(selfPlayerId to it) } ?: emptyMap(),
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
        if (MafiaProjectionPolicy.toPublic(publicState).state != publicState) return false
        if (!MafiaPeerSnapshotValidator.isValid(publicState, ownPrivate, selfPlayerId)) return false
        controller.installPlayerSnapshot(
            publicProjection = PublicProjection(publicState),
            playerProjection = PrivateProjection(playerState, selfPlayerId),
        )
        return true
    }

    private suspend fun sendActionToHost(
        action: MafiaAction,
    ): Result<SubmissionReceipt, SubmitError> {
        return when (val sent = coordinator.submit(MafiaActionCodec.encode(action))) {
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
                if (sent.error == NetError.NotConnected) connectionTracker.markSelfOffline()
                when (sent.error) {
                    NetError.CommandInFlight -> Result.Failure(SubmitError.CommandPending)
                    NetError.SessionSuspended -> Result.Failure(SubmitError.SessionSuspended)
                    else -> Result.Failure(SubmitError.SessionClosed)
                }
            }
        }
    }

    private suspend fun handleConnectionEvent(event: PeerEvent) = connectionTracker.handle(event)

    companion object {
        const val HOST_REJOIN_GRACE_MS: Long = 120_000L
    }
}
