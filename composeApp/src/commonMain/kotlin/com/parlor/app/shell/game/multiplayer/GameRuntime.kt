package com.parlor.app.shell.game.multiplayer

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.security.SecureIds
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.RetainedMultiplayerRuntime
import com.parlor.session.multidevice.toHostStartGateState
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class HostedGameRuntime<S : GameState, A : GameAction, E : GameEvent>(
    val spec: MultiplayerGameSpec<S, A, E>,
    clock: Clock,
    players: List<Player>,
    seed: Long,
    caseId: CaseId,
    val room: LocalRoom,
    scope: CoroutineScope,
) : RetainedMultiplayerRuntime {
    override val runtimeKind = spec.kind("host")
    private val raw = PassAndPlaySessionController(
        definition = spec.definition,
        config = SessionConfig(SessionId(SecureIds.id128()), caseId, spec.modeId, players, seed),
        reducerContext = DefaultReducerContext(clock, RandomSource.seeded(seed)), scope = scope,
    )
    val bridge = HostedGameBridge(spec, raw, room, players, scope, reconcileRoomTopology = true)
    val session: SessionController<S, A, E> = PublishingGameController(raw, bridge)
    val actions = GameActionSubmission(scope, session::submit)
    private val _startGate = MutableStateFlow<HostStartGateState>(HostStartGateState.Starting)
    val startGate = _startGate.asStateFlow()

    init {
        scope.launch {
            _startGate.value = try {
                bridge.announceStart(caseId.raw, spec.modeId.raw).toHostStartGateState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // I/O/start boundary: never expose codec or private state in an error.
                HostStartGateState.Failed(NetError.TransportFailure("session start failed"))
            }
        }
    }

    override suspend fun terminate(reason: SessionEndReason) = bridge.terminate(reason)
    override suspend fun close() {
        bridge.close()
        raw.close()
    }
}

internal class PeerGameRuntime<S : GameState, A : GameAction, E : GameEvent>(
    val spec: MultiplayerGameSpec<S, A, E>,
    offer: HostMessage.SessionStarting,
    protocol: SessionProtocol,
    room: LocalRoom,
    scope: CoroutineScope,
) : RetainedMultiplayerRuntime {
    override val runtimeKind = spec.kind("peer")
    private val placeholder = spec.definition.createInitialState(
        SessionConfig(protocol.sessionId, CaseId(offer.caseId), spec.modeId, offer.players, 0L),
    )
    val bridge = PeerGameBridge(spec, room, room.selfPlayerId, placeholder, scope, protocol, acceptedStartOffer = offer)
    val session: SessionController<S, A, E> = bridge.controller
    val actions = GameActionSubmission(scope, session::submit, bridge.commandProgress)
    override suspend fun terminate(reason: SessionEndReason) = Unit
    override suspend fun close() {
        bridge.close()
        session.close()
    }
}

private class PublishingGameController<S : GameState, A : GameAction, E : GameEvent>(
    private val delegate: SessionController<S, A, E>,
    private val bridge: HostedGameBridge<S, A, E>,
) : SessionController<S, A, E> by delegate {
    override suspend fun submit(action: A): Result<SubmissionReceipt, SubmitError> = bridge.submitHostAction(action)
}
