package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.md_peer_reconnecting
import com.parlor.games.mafia.resources.md_peer_reconnecting_leave
import com.parlor.games.mafia.resources.md_peer_reconnecting_leave_description
import com.parlor.games.mafia.resources.md_peer_initial_snapshot_failed
import com.parlor.games.mafia.resources.md_peer_initial_snapshot_loading
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.designsystem.components.ReconnectingOverlay
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.games.mafia.resources.peer_command_duplicate
import com.parlor.games.mafia.resources.peer_command_invalid
import com.parlor.games.mafia.resources.peer_command_session_error
import com.parlor.games.mafia.resources.peer_command_stale
import com.parlor.games.mafia.resources.peer_command_waiting
import com.parlor.networking.protocol.CommandStatus
import com.parlor.session.multidevice.PeerCommandProgress
import com.parlor.session.multidevice.PeerCommandDelivery
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.RetainedValueResult
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Multi-device **peer** entry for Mafia.
 *
 * Mirrors `WhodunitMultiplayerPeerFlow` for the Mafia domain. The peer
 * spins up a [MafiaPeerRoomBridge] whose [com.parlor.session.multidevice.ShadowSessionController]
 * is updated by inbound host snapshots and direct-sent private slices. The
 * peer never reduces game state locally — every action it submits is sent
 * to the host over the wire, and every state change is reflected only when
 * the host's snapshot arrives.
 *
 * Privacy on this device is structural: the bridge inbox rejects any
 * `PrivateStateForPlayer` whose `target != selfPlayerId`, and the host
 * never produced another peer's `MafiaPrivate` for transmission in the
 * first place. The router has no way to render another peer's role,
 * coordination snapshot, or detective result because that data simply
 * isn't on this device.
 *
 * Connection chrome ([onHostLostChanged] / [onSelfOfflineChanged]) is
 * derived from the bridge's durable `connectionState` StateFlow, so a newly
 * recreated UI immediately renders the current state instead of waiting for
 * another one-shot connection event.
 */
@Composable
fun MafiaMultiDevicePeerFlow(
    players: List<Player>,
    selfPlayerId: PlayerId,
    seed: Long,
    protocol: SessionProtocol,
    acceptedStartOffer: HostMessage.SessionStarting,
    ownedSession: ProcessMultiplayerSession,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    onHostLostChanged: (Boolean) -> Unit = {},
    onSelfOfflineChanged: (Boolean) -> Unit = {},
) {
    val definition: MafiaDefinition = koinInject()
    val runtimeLookup by produceState<RetainedValueResult<*>?>(
        initialValue = null,
        key1 = ownedSession,
    ) {
        value = ownedSession.getOrCreateRuntime(MAFIA_PEER_RUNTIME_KIND) { runtimeScope ->
            MafiaPeerRuntime(
                definition = definition,
                players = players,
                selfPlayerId = selfPlayerId,
                seed = seed,
                room = ownedSession.room,
                protocol = protocol,
                acceptedStartOffer = acceptedStartOffer,
                scope = runtimeScope,
            )
        }
    }
    val runtime = (runtimeLookup as? RetainedValueResult.Ready<*>)?.value as? MafiaPeerRuntime
    if (runtime == null) {
        ReconnectingOverlay(
            title = stringResource(
                if (runtimeLookup == null) {
                    Res.string.md_peer_initial_snapshot_loading
                } else {
                    Res.string.md_peer_initial_snapshot_failed
                },
            ),
            leaveLabel = stringResource(Res.string.md_peer_reconnecting_leave),
            leaveContentDescription = stringResource(
                Res.string.md_peer_reconnecting_leave_description,
            ),
            onLeave = onBackToHome,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val bridge = runtime.bridge
    val scope = runtime.scope

    val toastState = LocalParlorToastState.current
    val staleCommandCopy = stringResource(Res.string.peer_command_stale)
    val invalidCommandCopy = stringResource(Res.string.peer_command_invalid)
    val sessionCommandCopy = stringResource(Res.string.peer_command_session_error)
    val duplicateCommandCopy = stringResource(Res.string.peer_command_duplicate)
    LaunchedEffect(
        bridge,
        staleCommandCopy,
        invalidCommandCopy,
        sessionCommandCopy,
        duplicateCommandCopy,
    ) {
        bridge.commandProgress.collect { progress ->
            if (
                progress is PeerCommandProgress.Awaiting &&
                progress.delivery == PeerCommandDelivery.RecoveryTimedOut
            ) {
                toastState.show(sessionCommandCopy, ParlorToastSeverity.Danger)
                return@collect
            }
            val resolved = progress as? PeerCommandProgress.Resolved ?: return@collect
            val presentation = when (resolved.outcome.status) {
                CommandStatus.Applied -> null
                CommandStatus.Duplicate -> duplicateCommandCopy to ParlorToastSeverity.Info
                CommandStatus.StaleRevision,
                CommandStatus.SequenceGap -> staleCommandCopy to ParlorToastSeverity.Warning
                CommandStatus.InvalidAction,
                CommandStatus.Unauthorized,
                CommandStatus.PayloadTooLarge,
                CommandStatus.UnknownCommand -> invalidCommandCopy to ParlorToastSeverity.Danger
                CommandStatus.IncompatibleVersion,
                CommandStatus.SessionEnded,
                CommandStatus.SessionSuspended ->
                    sessionCommandCopy to ParlorToastSeverity.Danger
            }
            presentation?.let { (text, severity) -> toastState.show(text, severity) }
            bridge.acknowledgeCommandOutcome(resolved.outcome.commandId)
        }
    }

    LaunchedEffect(bridge) {
        bridge.hostDisconnected.collect { onBackToHome() }
    }

    val connectionState by bridge.connectionState.collectAsState()
    LaunchedEffect(connectionState.hostLost) {
        onHostLostChanged(connectionState.hostLost)
    }
    LaunchedEffect(connectionState.selfOffline) {
        onSelfOfflineChanged(connectionState.selfOffline)
    }

    val session = runtime.session
    // Render from the peer's own projection. The controller's public bucket
    // remains strictly public and can therefore be logged/rebroadcast safely.
    val playerProjection by session.privateStateFor(selfPlayerId).collectAsState()
    val state = playerProjection.state
    val hasAuthoritativeSnapshot by bridge.hasAuthoritativeSnapshot.collectAsState()
    val initialSnapshotError by bridge.initialSnapshotError.collectAsState()
    val commandProgress by bridge.commandProgress.collectAsState()

    if (!hasAuthoritativeSnapshot || state.public.disconnectedPlayers.isNotEmpty()) {
        ReconnectingOverlay(
            title = if (!hasAuthoritativeSnapshot) {
                stringResource(
                    if (initialSnapshotError == null) {
                        Res.string.md_peer_initial_snapshot_loading
                    } else {
                        Res.string.md_peer_initial_snapshot_failed
                    },
                )
            } else {
                stringResource(Res.string.md_peer_reconnecting)
            },
            leaveLabel = stringResource(Res.string.md_peer_reconnecting_leave),
            leaveContentDescription = stringResource(
                Res.string.md_peer_reconnecting_leave_description,
            ),
            onLeave = onBackToHome,
            modifier = modifier.fillMaxSize(),
        )
    } else if (commandProgress !is PeerCommandProgress.Idle) {
        ReconnectingOverlay(
            title = stringResource(Res.string.peer_command_waiting),
            leaveLabel = stringResource(Res.string.md_peer_reconnecting_leave),
            leaveContentDescription = stringResource(
                Res.string.md_peer_reconnecting_leave_description,
            ),
            onLeave = onBackToHome,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        MafiaMultiDevicePhaseRouter(
            state = state,
            selfPlayerId = selfPlayerId,
            isHost = false,
            session = session,
            scope = scope,
            onBackToHome = onBackToHome,
            modifier = modifier.fillMaxSize(),
        )
    }
}
