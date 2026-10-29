package com.parlor.app.shell.game.multiplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.permissions.rememberP2pPermissionGate
import com.parlor.app.shell.game.GameShellBackRequest
import com.parlor.app.shell.game.GameShellCapabilities
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.InvalidGameRouteFallback
import com.parlor.app.shell.game.P2pPermissionRoute
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.app.shell.playmode.PlayModePickerScreen
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.multidevice.MultiplayerSessionRole
import org.koin.compose.koinInject

private enum class ShellStep { Setup, HostPermission, HostName, Host, JoinPermission, JoinName, JoinCode, Peer, ResumePermission, Resume }

@Composable
internal fun <S : GameState, A : GameAction, E : GameEvent> MultiplayerShellContent(
    spec: MultiplayerGameSpec<S, A, E>, launch: GameShellLaunch, capabilities: GameShellCapabilities,
    onExit: () -> Unit, backRequest: GameShellBackRequest, modifier: Modifier,
) {
    if (launch is GameShellLaunch.ResumeLocal) {
        InvalidGameRouteFallback(onExit)
        return
    }
    val transport: RoomTransport = koinInject()
    val gate = rememberP2pPermissionGate(transport.localNetworkAccess)
    val owned = (launch as? GameShellLaunch.RestoreOwnedMultiplayer)?.route
    var name by remember(launch) {
        mutableStateOf(owned?.displayName ?: (launch as? GameShellLaunch.ResumeMultiplayer)?.displayName.orEmpty())
    }
    var code by remember(launch) { mutableStateOf(owned?.roomCode.orEmpty()) }
    var step by remember(launch) {
        mutableStateOf(when {
            launch is GameShellLaunch.ResumeMultiplayer -> ShellStep.ResumePermission
            owned?.role == MultiplayerSessionRole.Host -> ShellStep.Host
            owned?.resumeExistingSession == true -> ShellStep.Resume
            owned != null -> ShellStep.Peer
            else -> ShellStep.Setup
        })
    }
    var activeBack by remember(launch) { mutableStateOf(0L) }
    LaunchedEffect(backRequest.id) {
        if (backRequest.id > 0L) {
            if (step in setOf(ShellStep.Host, ShellStep.Peer, ShellStep.Resume)) activeBack = backRequest.id else onExit()
        }
    }
    val settings = gate::openAppSettings.takeIf { gate.canOpenNetworkSettings }
    when (step) {
        ShellStep.Setup -> PlayModePickerScreen(
            onModeSelected = {}, onHost = { step = ShellStep.HostPermission }, onJoin = { step = ShellStep.JoinPermission },
            onBack = onExit, capabilities = capabilities,
            supportedPlayerCounts = spec.definition.supportedPlayerCounts, modifier = modifier,
        )
        ShellStep.HostPermission -> P2pPermissionRoute(gate, { step = ShellStep.HostName }, onExit)
        ShellStep.JoinPermission -> P2pPermissionRoute(gate, { step = ShellStep.JoinName }, onExit)
        ShellStep.ResumePermission -> P2pPermissionRoute(gate, { step = ShellStep.Resume }, onExit)
        ShellStep.HostName, ShellStep.JoinName -> NameInputScreen(
            isHost = step == ShellStep.HostName, initial = name,
            onConfirm = { name = it; step = if (step == ShellStep.HostName) ShellStep.Host else ShellStep.JoinCode },
            onBack = onExit, modifier = modifier,
        )
        ShellStep.JoinCode -> JoinPromptScreen(onConfirm = { code = it; step = ShellStep.Peer }, onCancel = onExit, modifier = modifier)
        ShellStep.Host -> if (name.isBlank()) InvalidGameRouteFallback(onExit) else
            HostedGameLobby(spec, transport, name, onExit, settings, activeBack, modifier)
        ShellStep.Peer, ShellStep.Resume -> if (name.isBlank() || (step == ShellStep.Peer && code.isBlank())) {
            InvalidGameRouteFallback(onExit)
        } else {
            PeerGameLobby(spec, transport, code, name, step == ShellStep.Resume, onExit, settings, activeBack, modifier)
        }
    }
}
