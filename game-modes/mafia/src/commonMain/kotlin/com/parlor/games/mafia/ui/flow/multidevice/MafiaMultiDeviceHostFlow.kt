package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.result.Result
import com.parlor.core.time.Clock
import com.parlor.designsystem.components.ContinueWithoutDialog
import com.parlor.designsystem.components.HostDisconnectedOverlay
import com.parlor.designsystem.components.ReconnectingOverlay
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.session.SubmitError
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.action.MafiaAction
import com.parlor.games.mafia.domain.event.MafiaEvent
import com.parlor.games.mafia.domain.party.MafiaReadinessGate
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.state.MafiaState
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.md_host_continue_without_description_format
import com.parlor.games.mafia.resources.md_host_continue_without_dialog_body_format
import com.parlor.games.mafia.resources.md_host_continue_without_dialog_cancel
import com.parlor.games.mafia.resources.md_host_continue_without_dialog_confirm_description_format
import com.parlor.games.mafia.resources.md_host_continue_without_dialog_confirm_format
import com.parlor.games.mafia.resources.md_host_continue_without_dialog_title_format
import com.parlor.games.mafia.resources.md_host_continue_without_format
import com.parlor.games.mafia.resources.md_host_leave_session
import com.parlor.games.mafia.resources.md_host_leave_session_description
import com.parlor.games.mafia.resources.md_host_peer_away_body_format
import com.parlor.games.mafia.resources.md_host_peer_away_title
import com.parlor.games.mafia.resources.md_host_start_cancel
import com.parlor.games.mafia.resources.md_host_start_cancel_description
import com.parlor.games.mafia.resources.md_host_start_failed_body
import com.parlor.games.mafia.resources.md_host_start_failed_timeout
import com.parlor.games.mafia.resources.md_host_start_failed_title
import com.parlor.games.mafia.resources.md_host_start_retry
import com.parlor.games.mafia.resources.md_host_start_retry_description
import com.parlor.games.mafia.resources.md_host_starting
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.session.PlayMode
import com.parlor.session.SessionController
import com.parlor.session.SubmissionReceipt
import com.parlor.session.party.PartyAwareSession
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.beginExit
import com.parlor.session.multidevice.toHostStartGateState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Multi-device **host** entry for Mafia.
 *
 * Mirrors `WhodunitMultiplayerHostFlow` one-for-one but for the Mafia
 * domain types. The host runs the canonical reducer through a
 * [PassAndPlaySessionController], wraps it in a [PartyAwareSession] with
 * [MafiaReadinessGate] so phase-advance ticks behave identically to
 * pass-and-play, and shells the room transport through a
 * [MafiaHostRoomBridge] that:
 *
 *  - Broadcasts `MafiaProjectionPolicy.toPublic` snapshots on every host
 *    state change.
 *  - Direct-sends each peer their own `MafiaPrivate` slice.
 *  - Ingests `PeerMessage.ActionSubmit`, authorising via
 *    [com.parlor.games.mafia.domain.authority.MafiaActionAuthority] before
 *    forwarding to the controller.
 *
 * The host UI itself renders against the canonical state — same
 * [MafiaMultiDevicePhaseRouter] peers use, but with `isHost = true` so
 * host-only chrome (Resolve night, Open vote, host's setup screen) is
 * surfaced. Privacy is structural: peers receive only their own slice
 * because the bridge never produces another peer's slice for transmission.
 *
 * Pause is not implemented for Mafia in this version (no `paused` field in
 * `MafiaPublic`), so the pause overlay from the Whodunit flow is omitted.
 */
@Composable
fun MafiaMultiDeviceHostFlow(
    players: List<Player>,
    seed: Long,
    room: LocalRoom,
    onBackToHome: () -> Unit,
    onRetryStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock: Clock = koinInject()
    val definition: MafiaDefinition = koinInject()
    val scope = rememberCoroutineScope()

    // Freeze the roster at game start. The caller recomputes `players` from the
    // live room membership on every change, so keying the canonical session on
    // it rebuilt the controller — wiping roles/phase/votes — whenever a peer
    // dropped or returned mid-game. Membership churn is handled through the
    // bridge (MarkPlayerDisconnected/Reconnected). See PROBLEMS_PARLOR.md → CC-01.
    val rosterAtStart = remember { players }

    val sessionConfig = remember(rosterAtStart, seed) {
        SessionConfig(
            sessionId = SessionId("mafia-mp-host-${seed.toString(16)}"),
            caseId = CaseId("default"),
            modeId = MafiaIds.ClassicModeId,
            players = rosterAtStart,
            randomSeed = seed,
        )
    }
    val hostPlayMode = remember(room) {
        PlayMode.MultiDevice(selfPlayerId = room.selfPlayerId, isHost = true)
    }
    val rawSession = remember(sessionConfig) {
        PassAndPlaySessionController(
            definition = definition,
            config = sessionConfig,
            reducerContext = DefaultReducerContext(
                clock = clock,
                random = RandomSource.seeded(seed),
            ),
            scope = scope,
        )
    }
    // Bridge talks to the raw controller (it needs hostState to project
    // private slices). The UI submits through the PartyAwareSession wrapper
    // so phase-advance ticks behave identically to pass-and-play; in
    // MultiDevice mode the wrapper is a transparent pass-through for peer
    // actions (peers ack themselves) but still applies host-side gates.
    val partySession: SessionController<MafiaState, MafiaAction, MafiaEvent> =
        remember(rawSession, hostPlayMode) {
            PartyAwareSession(rawSession, hostPlayMode, MafiaReadinessGate)
        }
    val bridge = remember(rawSession, room, rosterAtStart) {
        MafiaHostRoomBridge(
            rawSession,
            room,
            rosterAtStart,
            scope,
            reconcileRoomTopology = true,
            requireStartHandshake = true,
        )
    }
    val session: SessionController<MafiaState, MafiaAction, MafiaEvent> =
        remember(partySession, bridge) {
            PublishingMafiaSessionController(partySession, bridge)
        }
    var startGate by remember(bridge) {
        mutableStateOf<HostStartGateState>(HostStartGateState.Starting)
    }
    LaunchedEffect(bridge) {
        val result = bridge.announceStart(
            caseId = "default",
            modeId = MafiaIds.ClassicModeId.raw,
        ).toHostStartGateState()
        if (startGate != HostStartGateState.Exiting) startGate = result
    }
    LaunchedEffect(bridge) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                try {
                    bridge.terminate(SessionEndReason.HostLeft)
                } finally {
                    try {
                        room.leave()
                    } finally {
                        bridge.close()
                    }
                }
            }
        }
    }

    var terminalExitInFlight by remember(bridge) { mutableStateOf(false) }
    val exitToHome: (SessionEndReason) -> Unit = { reason ->
        if (!terminalExitInFlight) {
            terminalExitInFlight = true
            startGate = startGate.beginExit()
            scope.launch {
                bridge.terminate(reason)
                room.leave()
                onBackToHome()
            }
        }
    }
    val retryStartAfterTerminal: () -> Unit = {
        if (!terminalExitInFlight) {
            terminalExitInFlight = true
            startGate = startGate.beginExit()
            scope.launch {
                bridge.terminate(SessionEndReason.Cancelled)
                room.leave()
                onRetryStart()
            }
        }
    }

    // The host is a seated player AND the authority on their own device, so the
    // router must render the FULL host state (privatePerPlayer[self] + hostOnly),
    // exactly like pass-and-play (MafiaGameFlow uses session.hostState). Rendering
    // the PUBLIC projection — which strips privatePerPlayer and redacts hostOnly —
    // meant the host could never see their own role or take night actions.
    // Peers correctly render their private projection in the peer flow.
    // See PROBLEMS_PARLOR.md → mafia-ui-001.
    val authoritativeState = requireNotNull(session.hostState) {
        "The Mafia host flow requires a host projection"
    }
    val hostProjection by authoritativeState.collectAsState()
    val state = hostProjection.state
    var confirmContinueFor by remember { mutableStateOf<Player?>(null) }
    val disconnectedPlayer = state.public.disconnectedPlayers
        .asSequence()
        .mapNotNull { playerId -> state.players.firstOrNull { it.id == playerId } }
        .firstOrNull()

    LaunchedEffect(disconnectedPlayer?.id) {
        if (disconnectedPlayer == null) {
            confirmContinueFor = null
        } else if (confirmContinueFor?.id != disconnectedPlayer.id) {
            confirmContinueFor = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (startGate == HostStartGateState.Started) {
            MafiaMultiDevicePhaseRouter(
                state = state,
                selfPlayerId = room.selfPlayerId,
                isHost = true,
                session = session,
                scope = scope,
                onBackToHome = { exitToHome(SessionEndReason.HostLeft) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (
            startGate == HostStartGateState.Started &&
            disconnectedPlayer != null &&
            state.phase != MafiaPhase.PostGame
        ) {
            val playerName = disconnectedPlayer.displayName
            if (confirmContinueFor?.id == disconnectedPlayer.id) {
                ContinueWithoutDialog(
                    title = stringResource(
                        Res.string.md_host_continue_without_dialog_title_format,
                        playerName,
                    ),
                    body = stringResource(
                        Res.string.md_host_continue_without_dialog_body_format,
                        playerName,
                    ),
                    cancelLabel = stringResource(
                        Res.string.md_host_continue_without_dialog_cancel,
                    ),
                    confirmLabel = stringResource(
                        Res.string.md_host_continue_without_dialog_confirm_format,
                        playerName,
                    ),
                    confirmContentDescription = stringResource(
                        Res.string.md_host_continue_without_dialog_confirm_description_format,
                        playerName,
                    ),
                    onCancel = { confirmContinueFor = null },
                    onConfirm = {
                        confirmContinueFor = null
                        scope.launch {
                            bridge.continueWithout(disconnectedPlayer.id)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                HostDisconnectedOverlay(
                    title = stringResource(Res.string.md_host_peer_away_title),
                    body = stringResource(
                        Res.string.md_host_peer_away_body_format,
                        playerName,
                    ),
                    continueLabel = stringResource(
                        Res.string.md_host_continue_without_format,
                        playerName,
                    ),
                    continueContentDescription = stringResource(
                        Res.string.md_host_continue_without_description_format,
                        playerName,
                    ),
                    leaveLabel = stringResource(Res.string.md_host_leave_session),
                    leaveContentDescription = stringResource(
                        Res.string.md_host_leave_session_description,
                    ),
                    onContinue = { confirmContinueFor = disconnectedPlayer },
                    onLeave = { exitToHome(SessionEndReason.Cancelled) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        when (val gate = startGate) {
            HostStartGateState.Started -> Unit
            HostStartGateState.Starting,
            HostStartGateState.Exiting -> ReconnectingOverlay(
                title = stringResource(Res.string.md_host_starting),
                leaveLabel = stringResource(Res.string.md_host_start_cancel),
                leaveContentDescription = stringResource(
                    Res.string.md_host_start_cancel_description,
                ),
                onLeave = {
                    exitToHome(SessionEndReason.Cancelled)
                },
                modifier = Modifier.fillMaxSize(),
            )
            is HostStartGateState.Failed -> HostDisconnectedOverlay(
                title = stringResource(Res.string.md_host_start_failed_title),
                body = stringResource(
                    if (gate.error == NetError.Timeout) {
                        Res.string.md_host_start_failed_timeout
                    } else {
                        Res.string.md_host_start_failed_body
                    },
                ),
                continueLabel = stringResource(Res.string.md_host_start_retry),
                continueContentDescription = stringResource(
                    Res.string.md_host_start_retry_description,
                ),
                leaveLabel = stringResource(Res.string.md_host_start_cancel),
                leaveContentDescription = stringResource(
                    Res.string.md_host_start_cancel_description,
                ),
                onContinue = {
                    retryStartAfterTerminal()
                },
                onLeave = {
                    exitToHome(SessionEndReason.Cancelled)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
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
