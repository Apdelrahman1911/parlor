package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.event.LastLightEvent
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.RetainedMultiplayerRuntime
import com.parlor.session.multidevice.beginExit
import com.parlor.session.multidevice.toHostStartGateState
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val LAST_LIGHT_HOST_RUNTIME_KIND = "last-light/host/v1"
internal const val LAST_LIGHT_PEER_RUNTIME_KIND = "last-light/peer/v1"
internal const val LAST_LIGHT_MIN_PLAYERS = 2
internal const val LAST_LIGHT_MAX_PLAYERS = 6

/** The only canonical reducer in a LAN match; all UI receives a seated projection. */
internal class LastLightHostRuntime(
    definition: LastLightDefinition,
    clock: Clock,
    players: List<Player>,
    seed: Long,
    val room: LocalRoom,
    val scope: CoroutineScope,
) : RetainedMultiplayerRuntime {
    override val runtimeKind = LAST_LIGHT_HOST_RUNTIME_KIND

    private val rawSession = PassAndPlaySessionController(
        definition = definition,
        config = SessionConfig(
            sessionId = SessionId("last-light-host-${seed.toString(16)}"),
            caseId = LastLightIds.CaseId,
            modeId = LastLightIds.StandardModeId,
            players = players,
            randomSeed = seed,
        ),
        reducerContext = DefaultReducerContext(clock, RandomSource.seeded(seed)),
        scope = scope,
    )
    val bridge = LastLightHostRoomBridge(
        controller = rawSession,
        room = room,
        players = players,
        scope = scope,
        reconcileRoomTopology = true,
    )
    val session: SessionController<LastLightState, LastLightAction, LastLightEvent> =
        PublishingLastLightSessionController(rawSession, bridge)
    val actions = LastLightActionSubmission(scope, session::submit)

    private val _startGate = MutableStateFlow<HostStartGateState>(HostStartGateState.Starting)
    val startGate = _startGate.asStateFlow()

    init {
        scope.launch {
            val result = try {
                bridge.announceStart(
                    caseId = LastLightIds.CaseId.raw,
                    modeId = LastLightIds.StandardModeId.raw,
                ).toHostStartGateState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                HostStartGateState.Failed(NetError.TransportFailure("session start failed"))
            }
            if (_startGate.value != HostStartGateState.Exiting) _startGate.value = result
        }
    }

    fun beginExit() {
        _startGate.value = _startGate.value.beginExit()
    }

    override suspend fun terminate(reason: SessionEndReason) = bridge.terminate(reason)

    override suspend fun close() {
        bridge.close()
        rawSession.close()
    }
}

/** A passive peer; its seed-zero placeholder is concealed until a validated snapshot arrives. */
internal class LastLightPeerRuntime(
    definition: LastLightDefinition,
    players: List<Player>,
    selfPlayerId: PlayerId,
    room: LocalRoom,
    protocol: SessionProtocol,
    acceptedStartOffer: HostMessage.SessionStarting,
    val scope: CoroutineScope,
) : RetainedMultiplayerRuntime {
    override val runtimeKind = LAST_LIGHT_PEER_RUNTIME_KIND

    private val initialState = definition.createInitialState(
        SessionConfig(
            sessionId = protocol.sessionId,
            caseId = LastLightIds.CaseId,
            modeId = LastLightIds.StandardModeId,
            players = players,
            randomSeed = 0L,
        ),
    )
    val bridge = LastLightPeerRoomBridge(
        room = room,
        selfPlayerId = selfPlayerId,
        initialPublic = initialState,
        scope = scope,
        protocol = protocol,
        acceptedStartOffer = acceptedStartOffer,
    )
    val session: SessionController<LastLightState, LastLightAction, LastLightEvent> = bridge.controller
    val actions = LastLightActionSubmission(scope, session::submit, bridge.commandProgress)

    override suspend fun terminate(reason: SessionEndReason) = Unit

    override suspend fun close() {
        bridge.close()
        session.close()
    }
}

private class PublishingLastLightSessionController(
    private val delegate: SessionController<LastLightState, LastLightAction, LastLightEvent>,
    private val bridge: LastLightHostRoomBridge,
) : SessionController<LastLightState, LastLightAction, LastLightEvent> by delegate {
    override suspend fun submit(action: LastLightAction): Result<SubmissionReceipt, SubmitError> =
        bridge.submitHostAction(action)
}
