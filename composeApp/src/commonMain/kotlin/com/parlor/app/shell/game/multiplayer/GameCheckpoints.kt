package com.parlor.app.shell.game.multiplayer

import com.parlor.core.ids.CaseId
import com.parlor.core.result.Result
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.networking.room.NetError
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.RetainedMultiplayerCheckpoint
import com.parlor.session.multidevice.RetainedSessionOperation
import com.parlor.session.multidevice.ValidatedSessionStart
import com.parlor.session.multidevice.asNetError
import com.parlor.session.multidevice.awaitAuthoritativeSessionStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class GameHostSetupState(
    val caseId: CaseId,
    val starting: Boolean = false,
    val frozen: Boolean = false,
    val error: NetError? = null,
)

/** Public settings survive UI recreation and freeze with the session-owned start attempt. */
internal class GameHostSetupCheckpoint(
    override val checkpointKind: String,
    defaultCaseId: CaseId,
) : RetainedMultiplayerCheckpoint {
    private val _state = MutableStateFlow(GameHostSetupState(defaultCaseId))
    val state = _state.asStateFlow()

    fun select(caseId: CaseId) {
        _state.update { if (it.starting || it.frozen) it else it.copy(caseId = caseId, error = null) }
    }

    fun freeze(session: ProcessMultiplayerSession) {
        val current = _state.value
        if (current.starting || current.frozen || !_state.compareAndSet(current, current.copy(starting = true, error = null))) return
        // Completion belongs to the physical room, not the screen that pressed Start.
        session.scope.launch {
            when (val result = session.freezeAdmissions()) {
                is Result.Success -> _state.update { it.copy(starting = false, frozen = true) }
                is Result.Failure -> _state.update { it.copy(starting = false, error = result.error) }
            }
        }
    }
}

internal sealed interface GameStartState {
    data object Waiting : GameStartState
    data class Started(val value: ValidatedSessionStart) : GameStartState
    data class Failed(val error: NetError) : GameStartState
}

/** The accepted start cannot be lost when a screen stops collecting midway through the barrier. */
internal class GamePeerStartCheckpoint(override val checkpointKind: String) : RetainedMultiplayerCheckpoint {
    private val operation = RetainedSessionOperation<GameStartState>(GameStartState.Waiting)
    val state = operation.state

    suspend fun <S : GameState, A : GameAction, E : GameEvent> start(
        spec: MultiplayerGameSpec<S, A, E>, session: ProcessMultiplayerSession, owner: ProcessMultiplayerSessionOwner,
    ) = operation.start(
        session.scope,
        onUnexpectedFailure = { GameStartState.Failed(NetError.TransportFailure("session start failed")) },
    ) {
        when (val result = awaitAuthoritativeSessionStart(session.room, spec.definition.id, spec.version) { offer, _ ->
            spec.acceptsStart(offer, session.room)
        }) {
            is Result.Success -> GameStartState.Started(result.data)
            is Result.Failure -> {
                val error = result.error.asNetError()
                owner.preparePeerRetry(session, error)
                GameStartState.Failed(error)
            }
        }
    }
}
