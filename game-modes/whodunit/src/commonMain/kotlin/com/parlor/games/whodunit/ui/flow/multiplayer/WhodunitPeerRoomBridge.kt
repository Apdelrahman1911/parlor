package com.parlor.games.whodunit.ui.flow.multiplayer

import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
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
    private val case: ValidatedCase<WhodunitCase>,
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
        onHostLossExpired = { _hostDisconnected.emit(Unit) },
    )
    val connectionState = connectionTracker.state
    val connectionEvents: SharedFlow<PeerEvent> = connectionTracker.events

    private val publicSerializer = WhodunitState.serializer()
    private val privateSerializer = WhodunitPrivate.serializer()
    private val safeInitialPublic = WhodunitProjectionPolicy.toPublic(initialPublic).state
    private val expectedCaseId = safeInitialPublic.public.caseId
    private val expectedModeId = safeInitialPublic.public.modeId
    private val expectedPlayers = safeInitialPublic.players

    val controller: ShadowSessionController<WhodunitState, WhodunitAction, WhodunitEvent> =
        ShadowSessionController(
            selfPlayerId = selfPlayerId,
            sendActionToHost = ::sendActionToHost,
            // No state is authoritative until the first validated host
            // snapshot. Never let a canonical state supplied by a mistaken
            // caller become peer-visible during that waiting window.
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
        if (
            publicState.public.caseId != expectedCaseId ||
            publicState.public.modeId != expectedModeId ||
            publicState.players != expectedPlayers
        ) {
            return false
        }
        // A valid public payload is already a fixed point of the projection
        // policy. Anything else contains a private/host-only field (or an
        // unredacted in-progress vote) and must fail closed.
        if (WhodunitProjectionPolicy.toPublic(publicState).state != publicState) return false
        if (
            !WhodunitStateValidator.isValidPeerProjectionForCase(
                publicState = publicState,
                ownPrivate = ownPrivate,
                selfPlayerId = selfPlayerId,
                case = case,
            )
        ) {
            return false
        }
        // Keep the public bucket structurally public. The UI may combine its
        // own private projection locally, but no private slice is relabelled as
        // public where a future logger or rebroadcast path could consume it.
        controller.installPlayerSnapshot(
            publicProjection = PublicProjection(publicState),
            playerProjection = PrivateProjection(playerState, selfPlayerId),
        )
        return true
    }

    private suspend fun sendActionToHost(
        action: WhodunitAction,
    ): Result<SubmissionReceipt, SubmitError> {
        return when (val sent = coordinator.submit(WhodunitActionCodec.encode(action))) {
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
