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
 * The I/O boundary above the engine. Same contract for pass-and-play and
 * (future) multi-device: only how state is *distributed* and how actions are
 * *submitted* differs.
 *
 * The reducer is identical across topologies; switching topologies is a
 * `SessionController` implementation choice, not a rewrite.
 */
interface SessionController<S : GameState, A : GameAction, E : GameEvent> {
    val publicState: StateFlow<PublicProjection<S>>
    val hostState: StateFlow<HostProjection<S>>?
    val events: SharedFlow<E>
    val activeViewer: StateFlow<ViewerContext>

    fun privateStateFor(playerId: PlayerId): StateFlow<PrivateProjection<S>>

    suspend fun submit(action: A): Result<Unit, SubmitError>
    suspend fun setActiveViewer(viewer: ViewerContext)
    suspend fun pause()
    suspend fun resume()
    suspend fun close()
}
