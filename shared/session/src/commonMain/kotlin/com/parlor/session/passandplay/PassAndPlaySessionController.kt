package com.parlor.session.passandplay

import kotlin.concurrent.Volatile
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.event.GameEvent
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.PublicProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.GameState
import com.parlor.session.SessionController
import com.parlor.session.ViewerContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pass-and-play implementation: full state lives on this device. The UI advances
 * the active viewer through the ceremony; the chosen projection is what gets
 * rendered. The reducer never sees the topology — it sees actions in and
 * state out.
 *
 * Phase 4 wires the Whodunit definition into this controller. Phase 6.2 adds
 * `restoredState` so a snapshot-driven resume can boot the controller at the
 * persisted state instead of `definition.createInitialState(config)`. Phase 7
 * adds a shape test that proves the same engine code can drive a multi-device
 * flow with no changes here.
 *
 * @param restoredState When non-null, the controller starts with this state
 * (used by Phase 6.2 resume). When null, the controller calls
 * `definition.createInitialState(config)` as before — the prior contract.
 */
class PassAndPlaySessionController<S : GameState, A : GameAction, E : GameEvent>(
    private val definition: GameDefinition<S, A, E>,
    private val config: SessionConfig,
    private val reducerContext: ReducerContext,
    private val scope: CoroutineScope,
    restoredState: S? = null,
) : SessionController<S, A, E> {

    private val mutex = Mutex()
    private val state: MutableStateFlow<S> =
        MutableStateFlow(restoredState ?: definition.createInitialState(config))
    private val reducer = definition.reducer()
    private val policy: ProjectionPolicy<S> = definition.projectionPolicy()

    private val _activeViewer = MutableStateFlow<ViewerContext>(ViewerContext.Public)
    override val activeViewer: StateFlow<ViewerContext> = _activeViewer.asStateFlow()

    override val publicState: StateFlow<PublicProjection<S>> =
        state.map { policy.toPublic(it) }
            .stateIn(scope, SharingStarted.Eagerly, policy.toPublic(state.value))

    override val hostState: StateFlow<HostProjection<S>> =
        state.map { policy.toHost(it) }
            .stateIn(scope, SharingStarted.Eagerly, policy.toHost(state.value))

    private val _events = MutableSharedFlow<E>(extraBufferCapacity = 64)
    override val events: SharedFlow<E> = _events.asSharedFlow()

    private val privateFlows: MutableMap<PlayerId, StateFlow<PrivateProjection<S>>> = mutableMapOf()

    override fun privateStateFor(playerId: PlayerId): StateFlow<PrivateProjection<S>> =
        privateFlows.getOrPut(playerId) {
            state.map { policy.toPlayer(it, playerId) }
                .stateIn(scope, SharingStarted.Eagerly, policy.toPlayer(state.value, playerId))
        }

    @Volatile private var closed: Boolean = false
    @Volatile private var paused: Boolean = false
    private var resumeBlocker: Job? = null

    override suspend fun submit(action: A): Result<Unit, SubmitError> = mutex.withLock {
        if (closed) return Result.Failure(SubmitError.SessionClosed)
        val current = state.value
        val reduction = reducer.reduce(current, action, reducerContext)
        state.value = reduction.newState
        reduction.events.forEach { _events.emit(it) }
        Result.Success(Unit)
    }

    override suspend fun setActiveViewer(viewer: ViewerContext) {
        _activeViewer.value = viewer
    }

    override suspend fun pause() { paused = true }
    override suspend fun resume() { paused = false }

    override suspend fun close() {
        closed = true
        resumeBlocker?.cancel()
    }
}
