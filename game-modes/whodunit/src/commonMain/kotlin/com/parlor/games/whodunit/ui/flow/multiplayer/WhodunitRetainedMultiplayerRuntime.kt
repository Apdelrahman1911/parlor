package com.parlor.games.whodunit.ui.flow.multiplayer

import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.contentIdentity
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.party.WhodunitReadinessGate
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitState
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

internal const val WHODUNIT_HOST_RUNTIME_KIND = "whodunit/host/v1"
internal const val WHODUNIT_PEER_RUNTIME_KIND = "whodunit/peer/v1"

/** Canonical Whodunit host engine and bridge retained by the process owner. */
internal class WhodunitHostRuntime(
    definition: WhodunitDefinition,
    clock: Clock,
    val case: ValidatedCase<WhodunitCase>,
    val modeId: ModeId,
    val players: List<Player>,
    val seed: Long,
    val room: LocalRoom,
    val scope: CoroutineScope,
) : RetainedMultiplayerRuntime {
    override val runtimeKind: String = WHODUNIT_HOST_RUNTIME_KIND

    val playMode = PlayMode.MultiDevice(selfPlayerId = room.selfPlayerId, isHost = true)
    private val rawSession = PassAndPlaySessionController(
        definition = definition,
        config = SessionConfig(
            sessionId = SessionId("mp-host-${seed.toString(16)}"),
            caseId = CaseId(case.envelope.caseId),
            modeId = modeId,
            players = players,
            randomSeed = seed,
        ),
        reducerContext = WhodunitReducerContext(
            clock = clock,
            random = RandomSource.seeded(seed),
            case = case,
        ),
        scope = scope,
    )
    private val partySession: SessionController<WhodunitState, WhodunitAction, WhodunitEvent> =
        PartyAwareSession(rawSession, playMode, WhodunitReadinessGate)
    val bridge = WhodunitHostRoomBridge(
        controller = rawSession,
        room = room,
        players = players,
        scope = scope,
        reconcileRoomTopology = true,
        requireStartHandshake = true,
    )
    val session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent> =
        PublishingWhodunitSessionController(partySession, bridge)

    private val _startGate = MutableStateFlow<HostStartGateState>(HostStartGateState.Starting)
    val startGate: StateFlow<HostStartGateState> = _startGate.asStateFlow()

    init {
        scope.launch {
            val result = try {
                val contentIdentity = case.envelope.contentIdentity()
                when (
                    val announced = bridge.announceStart(
                        caseId = case.envelope.caseId,
                        modeId = modeId.raw,
                        caseVersion = contentIdentity.version,
                        caseDigest = contentIdentity.digest,
                    )
                ) {
                    is Result.Failure -> announced.toHostStartGateState()
                    is Result.Success -> when (session.submit(WhodunitAction.AssignRoles(seed))) {
                        is Result.Success -> HostStartGateState.Started
                        is Result.Failure -> HostStartGateState.Failed(
                            NetError.TransportFailure("game initialization failed"),
                        )
                    }
                }
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

    override fun close() {
        bridge.close()
    }
}

/** Passive Whodunit peer mirror retained after the start transaction commits. */
internal class WhodunitPeerRuntime(
    definition: WhodunitDefinition,
    val case: ValidatedCase<WhodunitCase>,
    val modeId: ModeId,
    val players: List<Player>,
    selfPlayerId: PlayerId,
    seed: Long,
    room: LocalRoom,
    protocol: SessionProtocol,
    scope: CoroutineScope,
) : RetainedMultiplayerRuntime {
    override val runtimeKind: String = WHODUNIT_PEER_RUNTIME_KIND

    private val initialState = definition.createInitialState(
        SessionConfig(
            sessionId = SessionId("mp-peer-${seed.toString(16)}"),
            caseId = CaseId(case.envelope.caseId),
            modeId = modeId,
            players = players,
            // The public start nonce never controls peer-side gameplay RNG.
            randomSeed = 0L,
        ),
    )
    val bridge = WhodunitPeerRoomBridge(
        room = room,
        selfPlayerId = selfPlayerId,
        initialPublic = initialState,
        case = case,
        scope = scope,
        protocol = protocol,
    )
    val playMode = PlayMode.MultiDevice(selfPlayerId = selfPlayerId, isHost = false)
    val session: SessionController<WhodunitState, WhodunitAction, WhodunitEvent> =
        PartyAwareSession(bridge.controller, playMode, WhodunitReadinessGate)

    override suspend fun terminate(reason: SessionEndReason) = Unit

    override fun close() {
        bridge.close()
    }
}

/** Routes host UI mutations through the single authoritative coordinator. */
private class PublishingWhodunitSessionController(
    private val delegate: SessionController<WhodunitState, WhodunitAction, WhodunitEvent>,
    private val bridge: WhodunitHostRoomBridge,
) : SessionController<WhodunitState, WhodunitAction, WhodunitEvent> by delegate {
    override suspend fun submit(
        action: WhodunitAction,
    ): Result<SubmissionReceipt, SubmitError> = bridge.submitHostAction(action)
}
