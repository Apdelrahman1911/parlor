package com.parlor.games.lastlight.ui.flow.multidevice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import com.parlor.core.time.Clock
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.md_host_peer_away_body_format
import com.parlor.games.lastlight.resources.md_host_peer_away_title
import com.parlor.games.lastlight.resources.md_host_start_failed_body
import com.parlor.games.lastlight.resources.md_host_start_failed_timeout
import com.parlor.games.lastlight.resources.md_host_start_failed_title
import com.parlor.games.lastlight.resources.md_host_starting
import com.parlor.games.lastlight.resources.md_session_ended
import com.parlor.games.lastlight.resources.md_session_ended_body
import com.parlor.games.lastlight.resources.md_session_paused
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.session.multidevice.HostStartGateState
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.RetainedValueResult
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Restores the retained host runtime and displays only the host seat's private projection. */
@Composable
internal fun LastLightMultiDeviceHostFlow(
    players: List<Player>,
    ownedSession: ProcessMultiplayerSession,
    presentationState: SaveableStateHolder,
    onLeaveAfterEnd: (SessionEndReason) -> Unit,
    onNewRoom: (SessionEndReason) -> Unit,
    operationInFlight: Boolean,
    onRequestLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock: Clock = koinInject()
    val definition: LastLightDefinition = koinInject()
    val seed = requireNotNull(ownedSession.hostSeed) { "Host session requires its private reducer seed" }
    val runtimeLookup by produceState<RetainedValueResult<*>?>(null, ownedSession) {
        value = ownedSession.getOrCreateRuntime(LAST_LIGHT_HOST_RUNTIME_KIND) { scope ->
            LastLightHostRuntime(definition, clock, players, seed, ownedSession.room, scope)
        }
    }
    val runtime = (runtimeLookup as? RetainedValueResult.Ready<*>)?.value as? LastLightHostRuntime
    if (runtime == null) {
        LastLightNetworkStatus(
            title = stringResource(
                if (runtimeLookup == null) Res.string.md_host_starting else Res.string.md_host_start_failed_title,
            ),
            body = null,
            onLeave = onRequestLeave,
            onNewRoom = if (runtimeLookup == null) null else ({ onNewRoom(SessionEndReason.Cancelled) }),
            actionsEnabled = !operationInFlight,
            modifier = modifier,
        )
        return
    }
    val startGate by runtime.startGate.collectAsState()
    val terminalReason by runtime.bridge.terminalReason.collectAsState()
    val recoveryEpoch by runtime.bridge.recoveryEpoch.collectAsState()
    val lifecycle by runtime.room.lifecycle.collectAsState()
    val playerProjection by runtime.session.privateStateFor(runtime.room.selfPlayerId).collectAsState()
    val state = playerProjection.state
    val disconnectedPlayer = state.players.firstOrNull { it.id in state.public.disconnectedPlayers }

    when {
        terminalReason != null || state.public.endedEarly -> LastLightNetworkStatus(
            title = stringResource(Res.string.md_session_ended),
            body = stringResource(Res.string.md_session_ended_body),
            onLeave = { onLeaveAfterEnd(SessionEndReason.Cancelled) },
            onNewRoom = { onNewRoom(SessionEndReason.Cancelled) },
            actionsEnabled = !operationInFlight,
            modifier = modifier,
        )
        startGate is HostStartGateState.Failed -> {
            val failed = startGate as HostStartGateState.Failed
            LastLightNetworkStatus(
                title = stringResource(Res.string.md_host_start_failed_title),
                body = stringResource(
                    if (failed.error == NetError.Timeout) {
                        Res.string.md_host_start_failed_timeout
                    } else {
                        Res.string.md_host_start_failed_body
                    },
                ),
                onLeave = { onLeaveAfterEnd(SessionEndReason.Cancelled) },
                onNewRoom = { onNewRoom(SessionEndReason.Cancelled) },
                actionsEnabled = !operationInFlight,
                modifier = modifier,
            )
        }
        startGate != HostStartGateState.Started -> LastLightNetworkStatus(
            title = stringResource(Res.string.md_host_starting),
            body = null,
            onLeave = onRequestLeave,
            actionsEnabled = !operationInFlight,
            modifier = modifier,
        )
        disconnectedPlayer != null -> LastLightNetworkStatus(
            title = stringResource(Res.string.md_host_peer_away_title),
            body = stringResource(Res.string.md_host_peer_away_body_format, disconnectedPlayer.displayName),
            onLeave = onRequestLeave,
            actionsEnabled = !operationInFlight,
            modifier = modifier,
        )
        lifecycle != RoomLifecycleState.Active -> LastLightNetworkStatus(
            title = stringResource(Res.string.md_session_paused),
            body = null,
            onLeave = onRequestLeave,
            actionsEnabled = !operationInFlight,
            modifier = modifier,
        )
        else -> presentationState.SaveableStateProvider(runtime.bridge.protocol.sessionId.raw) {
            LastLightMultiplayerTable(
                sessionId = runtime.bridge.protocol.sessionId.raw,
                state = state,
                selfPlayerId = runtime.room.selfPlayerId,
                isHost = true,
                actions = runtime.actions,
                connected = true,
                commandsAllowed = !operationInFlight,
                recoveryEpoch = recoveryEpoch,
                recoveryEpochReader = { runtime.bridge.recoveryEpoch.value },
                onReturnToLobby = { onNewRoom(SessionEndReason.Completed) },
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}
