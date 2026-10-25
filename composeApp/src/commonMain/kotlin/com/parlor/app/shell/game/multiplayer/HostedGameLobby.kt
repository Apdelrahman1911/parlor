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
import com.parlor.app.resources.room_game_error_body
import com.parlor.app.resources.room_game_opening
import com.parlor.core.result.Result
import com.parlor.designsystem.components.InPlaceBackOwner
import com.parlor.designsystem.components.ProvideInPlaceBackOwner
import com.parlor.designsystem.components.SessionExitConfirmation
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomMember
import com.parlor.networking.security.SecureIds
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
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
internal fun <S : GameState, A : GameAction, E : GameEvent> HostedGameLobby(
    spec: MultiplayerGameSpec<S, A, E>, transport: RoomTransport, name: String, onExit: () -> Unit,
    onSettings: (() -> Unit)?, backRequest: Long, modifier: Modifier,
) {
    val owner: ProcessMultiplayerSessionOwner = koinInject()
    val route = remember(spec.definition.id, name) { MultiplayerSessionRoute.host(spec.definition.id, name) }
    val ownerState by owner.state.collectAsState()
    val session = (ownerState as? ProcessMultiplayerState.Active)?.session?.takeIf { it.route == route }
    val ownerError = (ownerState as? ProcessMultiplayerState.Failed)?.takeIf { it.route == route }?.error
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
        when (val result = owner.acquire(route, hostSeed = SecureIds.randomLong()) { mode ->
            check(mode == MultiplayerOpenMode.Host)
            transport.host(HostConfig(name, maxRemotePlayers = spec.definition.supportedPlayerCounts.last - 1,
                gameProtocol = HostedGameProtocol(spec.definition.id, spec.version)))
        }) {
            is Result.Success -> Unit
            is Result.Failure -> error = result.error
        }
    }
    val setupLookup by produceState<RetainedValueResult<*>?>(null, session) {
        value = session?.getOrCreateCheckpoint(spec.kind("setup")) { GameHostSetupCheckpoint(spec.kind("setup"), spec.defaultCaseId) }
    }
    val setup = (setupLookup as? RetainedValueResult.Ready<*>)?.value as? GameHostSetupCheckpoint
    val frozen by produceState<List<RoomMember>?>(session?.frozenRoster?.value, session) {
        session?.frozenRoster?.collect { value = it }
    }
    LaunchedEffect(frozen) { if (frozen != null) hasStarted = true }
    val leave: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                when (val result = owner.leaveRoute(route, if (hasStarted) SessionEndReason.HostLeft else SessionEndReason.Cancelled)) {
                    is Result.Success -> onExit()
                    is Result.Failure -> { error = result.error; confirm = false; busy = false }
                }
            }
        }
    }
    val requestLeave: () -> Unit = { if (!busy) { if (hasStarted || frozen != null) confirm = true else leave() } }
    LaunchedEffect(backRequest) {
        dispatchGameBack(backRequest, busy, confirm, { confirm = false }, inPlaceBack::handleBack, requestLeave)
    }
    val retry: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                val result = session?.let { owner.prepareHostRetry(it, SessionEndReason.Cancelled) } ?: Result.Success(Unit)
                when (result) {
                    is Result.Success -> { hasStarted = false; attempt++ }
                    is Result.Failure -> error = result.error
                }
                busy = false
            }
        }
    }
    val problem = error ?: ownerError
    ProvideInPlaceBackOwner(inPlaceBack) {
        when {
            confirm -> SessionExitConfirmation(SessionExitKind.Host, { if (!busy) confirm = false }, leave, busy, true, modifier)
            problem != null -> RoomGameStatus(stringResource(Res.string.room_game_error), leave, modifier,
                body = roomGameError(problem), onRetry = retry, enabled = !busy,
                onSettings = onSettings.takeIf { access.needsRecoveryGuidance })
            setupLookup != null && setup == null -> RoomGameStatus(stringResource(Res.string.room_game_error), leave, modifier,
                body = stringResource(Res.string.room_game_error_body), onNewRoom = retry, enabled = !busy)
            session == null || setup == null -> RoomGameStatus(
                stringResource(Res.string.room_game_opening), leave, modifier, enabled = !busy)
            frozen == null -> HostRoomControls(spec, session.room, setup, { setup.freeze(session) }, requestLeave, busy, modifier)
            else -> HostedGamePanel(spec, session, checkNotNull(frozen), setup.state.value.caseId, requestLeave, retry, busy, modifier)
        }
    }
}
