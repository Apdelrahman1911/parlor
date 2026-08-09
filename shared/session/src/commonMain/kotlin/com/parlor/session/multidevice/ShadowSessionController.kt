package com.parlor.session.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.GameState
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.ViewerContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 7 stub multi-device "shadow" controller. Sits on a peer device and
 * forwards local action submissions to a host-side controller via a supplied
 * sender callback. State arrives via [updatePublic] / [updatePrivate].
 *
 * This is NOT a production controller. Phase 7 only uses it to prove the
 * `SessionController` contract is sound across topologies — i.e., that a peer
 * can drive the same reducer without code changes.
 */
class ShadowSessionController<S : GameState, A : GameAction, E : GameEvent>(
    private val selfPlayerId: PlayerId,
    private val sendActionToHost: suspend (A) -> Result<SubmissionReceipt, SubmitError>,
    initialPublic: PublicProjection<S>,
    initialPrivate: PrivateProjection<S>,
) : SessionController<S, A, E> {

    private val _public = MutableStateFlow(initialPublic)
    private val _private = MutableStateFlow(initialPrivate)
    private val _events = MutableSharedFlow<E>(extraBufferCapacity = 64)
    private val _activeViewer = MutableStateFlow<ViewerContext>(ViewerContext.Player(selfPlayerId))

    override val publicState: StateFlow<PublicProjection<S>> = _public.asStateFlow()
    override val hostState: StateFlow<HostProjection<S>>? = null   // peer side — host bucket never arrives
    override val canonicalState: StateFlow<S>? = null
    override val events: SharedFlow<E> = _events.asSharedFlow()
    override val activeViewer: StateFlow<ViewerContext> = _activeViewer.asStateFlow()

    override fun privateStateFor(playerId: PlayerId): StateFlow<PrivateProjection<S>> {
        require(playerId == selfPlayerId) {
            "Shadow controller can only expose its own player's private state."
        }
        return _private.asStateFlow()
    }

    override suspend fun submit(action: A): Result<SubmissionReceipt, SubmitError> =
        sendActionToHost(action)

    override suspend fun setActiveViewer(viewer: ViewerContext) {
        // Peers' viewer is fixed to the owning player. Accept Public for cover screens.
        when (viewer) {
            is ViewerContext.Player -> require(viewer.id == selfPlayerId)
            ViewerContext.Public, ViewerContext.Host -> Unit
        }
        _activeViewer.value = viewer
    }

    override suspend fun pause() {}
    override suspend fun resume() {}
    override suspend fun close() {}

    // -- Driven by the multi-device session driver (Phase 7 shape test) --
    suspend fun updatePublic(p: PublicProjection<S>) { _public.value = p }
    suspend fun updatePrivate(p: PrivateProjection<S>) { _private.value = p }
    suspend fun emitEvent(e: E) { _events.emit(e) }
}
