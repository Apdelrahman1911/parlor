package com.parlor.session

import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.GameState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The I/O boundary above the engine. Same contract for local and multi-device
 * sessions: only how state is *distributed* and how actions are
 * *submitted* differs.
 *
 * The reducer is identical across topologies; switching topologies is a
 * `SessionController` implementation choice, not a rewrite.
 */
interface SessionController<S : GameState, A : GameAction, E : GameEvent> {
    val publicState: StateFlow<PublicProjection<S>>
    val hostState: StateFlow<HostProjection<S>>?
    /**
     * The directly committed reducer state on an authoritative controller.
     *
     * Unlike projection flows, this flow is updated in the reducer commit
     * critical section and can therefore drive persistence and follow-up
     * validation without observing an asynchronously mapped stale value.
     * Peer/shadow controllers must expose `null`; this state includes every
     * private bucket and must never be serialized onto the peer protocol.
     */
    val canonicalState: StateFlow<S>?
    val events: SharedFlow<E>
    val activeViewer: StateFlow<ViewerContext>

    fun privateStateFor(playerId: PlayerId): StateFlow<PrivateProjection<S>>

    /**
     * Applies [action] and reports whether the canonical state actually changed.
     *
     * Callers that replicate or persist state must use this receipt instead of
     * sampling one of the projection flows: those flows are asynchronous views
     * and are not a commit acknowledgement.
     */
    suspend fun submit(action: A): Result<SubmissionReceipt, SubmitError>
    suspend fun setActiveViewer(viewer: ViewerContext)
    suspend fun close()
}

/** Commit receipt returned by [SessionController.submit]. */
data class SubmissionReceipt(
    val stateChanged: Boolean,
    /** True on a peer after transport accepted the command but before host acknowledgement. */
    val awaitingAuthority: Boolean = false,
)
