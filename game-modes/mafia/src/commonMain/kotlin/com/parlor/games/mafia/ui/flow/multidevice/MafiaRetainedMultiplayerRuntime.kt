package com.parlor.games.mafia.ui.flow.multidevice

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.party.MafiaReadinessGate
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.RetainedMultiplayerRuntime
import com.parlor.session.multidevice.beginExit
import com.parlor.session.multidevice.toHostStartGateState
import com.parlor.session.party.PartyAwareSession
import com.parlor.session.passandplay.PassAndPlaySessionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val MAFIA_HOST_RUNTIME_KIND = "mafia/host/v1"
internal const val MAFIA_PEER_RUNTIME_KIND = "mafia/peer/v1"

/** Canonical Mafia host reducer and room bridge retained across UI roots. */
internal class MafiaHostRuntime(
    definition: MafiaDefinition,
    clock: Clock,
    players: List<Player>,
    val seed: Long,
    val room: LocalRoom,
    val scope: CoroutineScope,
) : RetainedMultiplayerRuntime {
    override val runtimeKind: String = MAFIA_HOST_RUNTIME_KIND

    private val rawSession = PassAndPlaySessionController(
        definition = definition,
        config = SessionConfig(
            sessionId = SessionId("mafia-mp-host-${seed.toString(16)}"),
            caseId = CaseId("default"),
            modeId = MafiaIds.ClassicModeId,
            players = players,
            randomSeed = seed,
        ),
        reducerContext = DefaultReducerContext(
            clock = clock,
            random = RandomSource.seeded(seed),
        ),
        scope = scope,
    )
    val playMode = PlayMode.MultiDevice(selfPlayerId = room.selfPlayerId, isHost = true)
    private val partySession: SessionController<MafiaState, MafiaAction, MafiaEvent> =
        PartyAwareSession(rawSession, playMode, MafiaReadinessGate)
    val bridge = MafiaHostRoomBridge(
        controller = rawSession,
        room = room,
        players = players,
        scope = scope,
        reconcileRoomTopology = true,
        requireStartHandshake = true,
    )
    val session: SessionController<MafiaState, MafiaAction, MafiaEvent> =
        PublishingMafiaSessionController(partySession, bridge)

    private val _startGate = MutableStateFlow<HostStartGateState>(HostStartGateState.Starting)
    val startGate: StateFlow<HostStartGateState> = _startGate.asStateFlow()

    init {
        scope.launch {
            val result = try {
                bridge.announceStart(
                    caseId = "default",
                    modeId = MafiaIds.ClassicModeId.raw,
                ).toHostStartGateState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                HostStartGateState.Failed(NetError.TransportFailure("session start failed"))
            }
            if (_startGate.value != HostStartGateState.Exiting) {
                _startGate.value = result
            }
        }
    }

    fun beginExit() {
        _startGate.value = _startGate.value.beginExit()
    }

    override suspend fun terminate(reason: SessionEndReason) {
        bridge.terminate(reason)
    }

    override suspend fun close() {
        bridge.close()
    }
}

/** Passive Mafia peer mirror retained after the acknowledged start commit. */
internal class MafiaPeerRuntime(
    definition: MafiaDefinition,
    players: List<Player>,
    selfPlayerId: PlayerId,
    seed: Long,
    room: LocalRoom,
    protocol: SessionProtocol,
    val scope: CoroutineScope,
) : RetainedMultiplayerRuntime {
    override val runtimeKind: String = MAFIA_PEER_RUNTIME_KIND

    private val initialState = definition.createInitialState(
        SessionConfig(
            sessionId = SessionId("mafia-mp-peer-${seed.toString(16)}"),
            caseId = CaseId("default"),
            modeId = MafiaIds.ClassicModeId,
            players = players,
            randomSeed = 0L,
        ),
    )
    val bridge = MafiaPeerRoomBridge(
        room = room,
        selfPlayerId = selfPlayerId,
        initialPublic = initialState,
        scope = scope,
        protocol = protocol,
    )
    val playMode = PlayMode.MultiDevice(selfPlayerId = selfPlayerId, isHost = false)
    val session: SessionController<MafiaState, MafiaAction, MafiaEvent> =
        PartyAwareSession(bridge.controller, playMode, MafiaReadinessGate)

    override suspend fun terminate(reason: SessionEndReason) = Unit

    override suspend fun close() {
        bridge.close()
    }
}

private class PublishingMafiaSessionController(
    private val delegate: SessionController<MafiaState, MafiaAction, MafiaEvent>,
    private val bridge: MafiaHostRoomBridge,
) : SessionController<MafiaState, MafiaAction, MafiaEvent> by delegate {
    override suspend fun submit(
        action: MafiaAction,
    ): Result<SubmissionReceipt, SubmitError> = bridge.submitHostAction(action)
}
