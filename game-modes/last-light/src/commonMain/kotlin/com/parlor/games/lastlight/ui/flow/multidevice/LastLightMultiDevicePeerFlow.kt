package com.parlor.games.lastlight.ui.flow.multidevice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.md_net_error_incompatible
import com.parlor.games.lastlight.resources.md_peer_initial_snapshot_failed
import com.parlor.games.lastlight.resources.md_peer_initial_snapshot_loading
import com.parlor.games.lastlight.resources.md_session_ended
import com.parlor.games.lastlight.resources.md_session_ended_body
import com.parlor.games.lastlight.resources.md_session_new_room_body
import com.parlor.games.lastlight.resources.md_session_paused
import com.parlor.games.lastlight.resources.peer_command_duplicate
import com.parlor.games.lastlight.resources.peer_command_invalid
import com.parlor.games.lastlight.resources.peer_command_session_error
import com.parlor.games.lastlight.resources.peer_command_stale
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.session.multidevice.PeerCommandDelivery
import com.parlor.session.multidevice.PeerCommandProgress
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.RetainedValueResult
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** A retained passive mirror; gameplay never runs a peer reducer or predicts a card removal. */
@Composable
internal fun LastLightMultiDevicePeerFlow(
    players: List<Player>,
    selfPlayerId: PlayerId,
    protocol: SessionProtocol,
    acceptedStartOffer: HostMessage.SessionStarting,
    ownedSession: ProcessMultiplayerSession,
    presentationState: SaveableStateHolder,
    onBackToHome: () -> Unit,
    onRequestLeave: () -> Unit,
    modifier: Modifier = Modifier,
    onHostLostChanged: (Boolean) -> Unit = {},
    onSelfOfflineChanged: (Boolean) -> Unit = {},
) {
    val definition: LastLightDefinition = koinInject()
    val runtimeLookup by produceState<RetainedValueResult<*>?>(null, ownedSession) {
        value = ownedSession.getOrCreateRuntime(LAST_LIGHT_PEER_RUNTIME_KIND) { scope ->
            LastLightPeerRuntime(
                definition, players, selfPlayerId, ownedSession.room, protocol, acceptedStartOffer, scope,
            )
        }
    }
    val runtime = (runtimeLookup as? RetainedValueResult.Ready<*>)?.value as? LastLightPeerRuntime
    if (runtime == null) {
        LastLightNetworkStatus(
            title = stringResource(
                if (runtimeLookup == null) {
                    Res.string.md_peer_initial_snapshot_loading
                } else {
                    Res.string.md_peer_initial_snapshot_failed
                },
            ),
            body = null,
            onLeave = onRequestLeave,
            modifier = modifier,
        )
        return
    }
    val bridge = runtime.bridge
    LastLightCommandOutcomeEffect(bridge)
    val connection by bridge.connectionState.collectAsState()
    val lifecycle by ownedSession.room.lifecycle.collectAsState()
    val terminalReason by bridge.terminalReason.collectAsState()
    val terminalError by bridge.terminalError.collectAsState()
    val recoveryEpoch by bridge.recoveryEpoch.collectAsState()
    LaunchedEffect(connection, terminalReason, terminalError) {
        onHostLostChanged(connection.hostLost && terminalReason == null && terminalError == null)
        onSelfOfflineChanged(connection.selfOffline && terminalReason == null && terminalError == null)
    }
    val projection by runtime.session.privateStateFor(selfPlayerId).collectAsState()
    val hasSnapshot by bridge.hasAuthoritativeSnapshot.collectAsState()
    val snapshotError by bridge.initialSnapshotError.collectAsState()
    val commandProgress by bridge.commandProgress.collectAsState()
    val state = projection.state
    val connected = !connection.hostLost && !connection.selfOffline && lifecycle == RoomLifecycleState.Active

    when {
        terminalError != null -> LastLightNetworkStatus(
            title = stringResource(Res.string.md_session_ended),
            body = lastlightNetworkErrorMessage(checkNotNull(terminalError)),
            onLeave = onBackToHome,
            modifier = modifier,
        )
        terminalReason != null || state.public.endedEarly -> LastLightNetworkStatus(
            title = stringResource(Res.string.md_session_ended),
            body = stringResource(
                when (terminalReason) {
                    SessionEndReason.IncompatibleVersion -> Res.string.md_net_error_incompatible
                    SessionEndReason.RejoinExpired -> Res.string.md_session_ended_body
                    else -> Res.string.md_session_new_room_body
                },
            ),
            onLeave = onBackToHome,
            modifier = modifier,
        )
        !hasSnapshot -> LastLightNetworkStatus(
            title = stringResource(
                if (snapshotError == null) {
                    Res.string.md_peer_initial_snapshot_loading
                } else {
                    Res.string.md_peer_initial_snapshot_failed
                },
            ),
            body = null,
            onLeave = onRequestLeave,
            modifier = modifier,
        )
        state.public.disconnectedPlayers.isNotEmpty() -> LastLightNetworkStatus(
            title = stringResource(Res.string.md_session_paused),
            body = null,
            onLeave = onRequestLeave,
            modifier = modifier,
        )
        else -> presentationState.SaveableStateProvider(protocol.sessionId.raw) {
            LastLightMultiplayerTable(
                sessionId = protocol.sessionId.raw,
                state = state,
                selfPlayerId = selfPlayerId,
                isHost = false,
                actions = runtime.actions,
                connected = connected,
                commandsAllowed = commandProgress is PeerCommandProgress.Idle,
                recoveryEpoch = recoveryEpoch,
                recoveryEpochReader = { bridge.recoveryEpoch.value },
                onReturnToLobby = onBackToHome,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LastLightCommandOutcomeEffect(bridge: LastLightPeerRoomBridge) {
    val toastState = LocalParlorToastState.current
    val stale = stringResource(Res.string.peer_command_stale)
    val invalid = stringResource(Res.string.peer_command_invalid)
    val sessionError = stringResource(Res.string.peer_command_session_error)
    val duplicate = stringResource(Res.string.peer_command_duplicate)
    LaunchedEffect(bridge, stale, invalid, sessionError, duplicate) {
        bridge.commandProgress.collect { progress ->
            if (
                progress is PeerCommandProgress.Awaiting &&
                progress.delivery == PeerCommandDelivery.RecoveryTimedOut
            ) {
                toastState.show(sessionError, ParlorToastSeverity.Danger)
            }
            val resolved = progress as? PeerCommandProgress.Resolved ?: return@collect
            val message = when (resolved.outcome.status) {
                CommandStatus.Applied -> null
                CommandStatus.Duplicate -> duplicate
                CommandStatus.StaleRevision,
                CommandStatus.SequenceGap -> stale
                CommandStatus.InvalidAction,
                CommandStatus.Unauthorized,
                CommandStatus.PayloadTooLarge,
                CommandStatus.UnknownCommand -> invalid
                CommandStatus.IncompatibleVersion,
                CommandStatus.SessionEnded,
                CommandStatus.SessionSuspended -> sessionError
            }
            message?.let { toastState.show(it, ParlorToastSeverity.Warning) }
            bridge.acknowledgeCommandOutcome(resolved.outcome.commandId)
        }
    }
}
