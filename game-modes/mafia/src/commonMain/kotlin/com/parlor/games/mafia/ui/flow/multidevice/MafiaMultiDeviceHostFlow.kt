package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.core.time.Clock
import com.parlor.designsystem.components.ContinueWithoutDialog
import com.parlor.designsystem.components.HostDisconnectedOverlay
import com.parlor.designsystem.components.ReconnectingOverlay
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.domain.party.MafiaReadinessGate
import com.parlor.games.mafia.domain.phase.MafiaPhase
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
import com.parlor.networking.room.NetError
import com.parlor.session.party.PartyAwareSession
import com.parlor.session.passandplay.PassAndPlaySessionController
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.RetainedValueResult
import kotlinx.coroutines.launch
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
 *  - Ingests authenticated `PeerMessage.ClientCommand` payloads through the
 *    shared coordinator, authorising via
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
    ownedSession: ProcessMultiplayerSession,
    sessionOwner: ProcessMultiplayerSessionOwner,
    onBackToHome: () -> Unit,
    onRetryStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock: Clock = koinInject()
    val definition: MafiaDefinition = koinInject()
    val uiScope = rememberCoroutineScope()
    val seed = requireNotNull(ownedSession.hostSeed) {
        "Mafia host session is missing its private reducer seed"
    }
    val runtimeLookup by produceState<RetainedValueResult<*>?>(
        initialValue = null,
        key1 = ownedSession,
    ) {
        value = ownedSession.getOrCreateRuntime(MAFIA_HOST_RUNTIME_KIND) { runtimeScope ->
            MafiaHostRuntime(
                definition = definition,
                clock = clock,
                players = players,
                seed = seed,
                room = ownedSession.room,
                scope = runtimeScope,
            )
        }
    }
    val runtime = (runtimeLookup as? RetainedValueResult.Ready<*>)?.value as? MafiaHostRuntime
    if (runtime == null) {
        ReconnectingOverlay(
            title = stringResource(
                if (runtimeLookup == null) {
                    Res.string.md_host_starting
                } else {
                    Res.string.md_host_start_failed_title
                },
            ),
            leaveLabel = stringResource(Res.string.md_host_start_cancel),
            leaveContentDescription = stringResource(Res.string.md_host_start_cancel_description),
            onLeave = {
                uiScope.launch {
                    sessionOwner.finalLeave(ownedSession, SessionEndReason.Cancelled)
                    onBackToHome()
                }
            },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val scope = runtime.scope
    val room = runtime.room
    val session = runtime.session
    val bridge = runtime.bridge
    val startGate by runtime.startGate.collectAsState()

    var terminalExitInFlight by remember(runtime) { mutableStateOf(false) }
    val exitToHome: (SessionEndReason) -> Unit = { reason ->
        if (!terminalExitInFlight) {
            terminalExitInFlight = true
            runtime.beginExit()
            uiScope.launch {
                sessionOwner.finalLeave(ownedSession, reason)
                onBackToHome()
            }
        }
    }
    val retryStartAfterTerminal: () -> Unit = {
        if (!terminalExitInFlight) {
            terminalExitInFlight = true
            runtime.beginExit()
            uiScope.launch {
                sessionOwner.prepareHostRetry(ownedSession)
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

    when (val gate = startGate) {
        HostStartGateState.Started -> {
            if (disconnectedPlayer != null && state.phase != MafiaPhase.PostGame) {
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
                            scope.launch { bridge.continueWithout(disconnectedPlayer.id) }
                        },
                        modifier = modifier.fillMaxSize(),
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
                        modifier = modifier.fillMaxSize(),
                    )
                }
            } else {
                MafiaMultiDevicePhaseRouter(
                    state = state,
                    selfPlayerId = room.selfPlayerId,
                    isHost = true,
                    session = session,
                    scope = scope,
                    onBackToHome = { exitToHome(SessionEndReason.HostLeft) },
                    modifier = modifier.fillMaxSize(),
                )
            }
        }
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
                modifier = modifier.fillMaxSize(),
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
                modifier = modifier.fillMaxSize(),
            )
    }
}
