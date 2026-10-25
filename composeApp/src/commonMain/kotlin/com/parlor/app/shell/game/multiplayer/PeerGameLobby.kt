package com.parlor.app.shell.game.multiplayer

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
import com.parlor.app.resources.Res
import com.parlor.app.resources.room_game_error
import com.parlor.app.resources.room_game_host
import com.parlor.app.resources.room_game_incompatible
import com.parlor.app.resources.room_game_joining
import com.parlor.app.resources.room_game_wait_host
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.components.InPlaceBackOwner
import com.parlor.designsystem.components.ProvideInPlaceBackOwner
import com.parlor.designsystem.components.SessionExitConfirmation
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.needsRecoveryGuidance
import com.parlor.session.multidevice.MultiplayerOpenMode
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import com.parlor.session.multidevice.RetainedValueResult
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun <S : GameState, A : GameAction, E : GameEvent> PeerGameLobby(
    spec: MultiplayerGameSpec<S, A, E>, transport: RoomTransport, code: String, name: String, resume: Boolean,
    onExit: () -> Unit, onSettings: (() -> Unit)?, backRequest: Long, modifier: Modifier,
) {
    val owner: ProcessMultiplayerSessionOwner = koinInject()
    val route = remember(spec.definition.id, code, name, resume) { MultiplayerSessionRoute.peer(spec.definition.id, name, code, resume) }
    val ownerState by owner.state.collectAsState()
    val session = (ownerState as? ProcessMultiplayerState.Active)?.session?.takeIf { it.route == route }
    val ownerError = ownerState.peerRouteError(route)
    val scope = rememberCoroutineScope()
    val inPlaceBack = remember(route) { InPlaceBackOwner() }
    var attempt by remember(route) { mutableStateOf(0) }
    var error by remember(route) { mutableStateOf<NetError?>(null) }
    var busy by remember(route) { mutableStateOf(false) }
    var confirm by remember(route) { mutableStateOf(false) }
    var hasStarted by remember(route) { mutableStateOf(false) }
    val access by transport.localNetworkAccess.collectAsState()
    LaunchedEffect(transport, route, attempt) {
        error = null
        when (val result = owner.acquire(route) { mode ->
            when (mode) {
                MultiplayerOpenMode.Join -> transport.join(code, name)
                MultiplayerOpenMode.Resume -> transport.resumeLastSession()
                MultiplayerOpenMode.Host -> error("Peer route cannot host")
            }
        }) {
            is Result.Success -> Unit
            is Result.Failure -> error = result.error
        }
    }
    val lookup by produceState<RetainedValueResult<*>?>(null, session) {
        value = session?.getOrCreateCheckpoint(spec.kind("start")) { GamePeerStartCheckpoint(spec.kind("start")) }
    }
    val checkpoint = (lookup as? RetainedValueResult.Ready<*>)?.value as? GamePeerStartCheckpoint
    LaunchedEffect(checkpoint, session) { if (checkpoint != null && session != null) checkpoint.start(spec, session, owner) }
    val start by produceState<GameStartState>(checkpoint?.state?.value ?: GameStartState.Waiting, checkpoint) {
        checkpoint?.state?.collect { value = it }
    }
    val accepted = (start as? GameStartState.Started)?.value
    LaunchedEffect(accepted) { if (accepted != null) hasStarted = true }
    val leave: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                when (val result = owner.leaveRoute(route, SessionEndReason.Cancelled)) {
                    is Result.Success -> onExit()
                    is Result.Failure -> { error = result.error; confirm = false; busy = false }
                }
            }
        }
    }
    val requestLeave: () -> Unit = { if (!busy) { if (hasStarted || accepted != null) confirm = true else leave() } }
    LaunchedEffect(backRequest) {
        dispatchGameBack(backRequest, busy, confirm, { confirm = false }, inPlaceBack::handleBack, requestLeave)
    }
    val retry: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                val failed = (start as? GameStartState.Failed)?.error
                val result = if (session != null && failed != null) owner.preparePeerRetry(session, failed) else Result.Success(Unit)
                when (result) {
                    is Result.Success -> attempt++
                    is Result.Failure -> error = result.error
                }
                busy = false
            }
        }
    }
    val problem = error ?: (start as? GameStartState.Failed)?.error ?: ownerError
    ProvideInPlaceBackOwner(inPlaceBack) {
        when {
            confirm -> SessionExitConfirmation(SessionExitKind.Peer, { if (!busy) confirm = false }, leave, busy, true, modifier)
            problem != null -> RoomGameStatus(stringResource(Res.string.room_game_error), leave, modifier,
                body = roomGameError(problem), onRetry = retry, enabled = !busy,
                onSettings = onSettings.takeIf { access.needsRecoveryGuidance })
            lookup != null && checkpoint == null -> RoomGameStatus(stringResource(Res.string.room_game_error), leave, modifier,
                body = stringResource(Res.string.room_game_incompatible), enabled = !busy)
            session == null -> RoomGameStatus(stringResource(Res.string.room_game_joining), leave, modifier, enabled = !busy)
            accepted == null -> RoomGameStatus(stringResource(Res.string.room_game_wait_host), leave, modifier,
                body = stringResource(Res.string.room_game_host, session.room.info.value.hostDisplayName.asBidiArgument()), enabled = !busy)
            else -> PeerGamePanel(spec, session, accepted, requestLeave, leave, busy, modifier)
        }
    }
}

private fun ProcessMultiplayerState.peerRouteError(route: MultiplayerSessionRoute): NetError? = when (this) {
    is ProcessMultiplayerState.Failed -> takeIf { it.route == route }?.error
    is ProcessMultiplayerState.Retryable -> takeIf { it.route == route }?.lastError
    else -> null
}
