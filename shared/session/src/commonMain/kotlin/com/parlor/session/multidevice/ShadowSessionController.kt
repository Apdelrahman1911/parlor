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
 * Peer-side session controller. Local actions are forwarded to the
 * authoritative host and viewer-filtered snapshots are installed by the game
 * transport adapter.
 *
 * [publicState] and [privateStateFor] remain separate `StateFlow`s because
 * they serve different consumers; Kotlin flows cannot provide a transaction
 * across two collectors. A player UI that needs public and own-private data
 * from one authoritative revision must therefore render exclusively from the
 * complete player projection returned by [privateStateFor]. The public flow is
 * for consumers that need public data only.
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
            ViewerContext.Public -> Unit
            ViewerContext.Host -> throw IllegalArgumentException(
                "A peer controller cannot assume the host viewer context.",
            )
        }
        _activeViewer.value = viewer
    }

    override suspend fun pause() {}
    override suspend fun resume() {}
    override suspend fun close() {}

    /**
     * Installs both projections decoded from one authenticated host snapshot.
     * Ownership is checked before either flow changes, so an adapter cannot
     * partially install a snapshot addressed to another player.
     *
     * This method deliberately does not promise cross-flow atomic observation;
     * player renderers must consume [privateStateFor] as their single complete
     * projection for a revision.
     */
    fun installPlayerSnapshot(
        publicProjection: PublicProjection<S>,
        playerProjection: PrivateProjection<S>,
    ) {
        require(playerProjection.playerId == selfPlayerId) {
            "Shadow controller cannot install another player's private projection."
        }
        _private.value = playerProjection
        _public.value = publicProjection
    }

    suspend fun emitEvent(e: E) { _events.emit(e) }
}
