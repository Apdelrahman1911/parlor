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
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.session.multidevice.ShadowSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
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
) {
    private val _hostDisconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires once when the host signals end-of-session or the room reports a drop. */
    val hostDisconnected: SharedFlow<Unit> = _hostDisconnected.asSharedFlow()

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

    init {
        startInbox()
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
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

    // ============================================================ Outbox ==

    private suspend fun sendActionToHost(action: WhodunitAction): Result<Unit, SubmitError> {
        val bytes = WhodunitActionCodec.encode(action)
        val sendResult = room.sendToHost(PeerMessage.ActionSubmit(bytes))
        return when (sendResult) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(SubmitError.SessionClosed)
        }
    }
}
