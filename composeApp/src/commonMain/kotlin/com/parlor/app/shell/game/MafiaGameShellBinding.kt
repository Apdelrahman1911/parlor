package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.permissions.rememberP2pPermissionGate
import com.parlor.app.resources.Res
import com.parlor.app.resources.home_mafia_open
import com.parlor.app.resources.home_mafia_open_description
import com.parlor.app.resources.home_mafia_subtitle
import com.parlor.app.resources.home_mafia_tagline
import com.parlor.app.resources.home_mafia_title
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.app.shell.playmode.PlayModePickerScreen
import com.parlor.engine.definition.GameDefinition
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.ui.flow.multidevice.MafiaHostLobbyFlow
import com.parlor.games.mafia.ui.flow.multidevice.MafiaHostRoomBridge
import com.parlor.games.mafia.ui.flow.multidevice.MafiaPeerLobbyFlow
import com.parlor.games.mafia.ui.flow.passandplay.MafiaGameFlow
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.PlayMode
import com.parlor.session.multidevice.MultiplayerSessionRole
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal class MafiaGameShellBinding(
    private val mafiaDefinition: MafiaDefinition,
) : GameShellBinding {
    override val definition: GameDefinition<*, *, *> = mafiaDefinition
    override val capabilities = GameShellCapabilities(
        setOf(
            GameEntryMode.PassAndPlay,
            GameEntryMode.Host,
            GameEntryMode.Join,
        ),
    )
    override val multiplayerContract = GameShellMultiplayerContract(
        gameId = mafiaDefinition.id,
        gameVersion = MafiaHostRoomBridge.GAME_VERSION,
        supportedPlayerCounts = mafiaDefinition.supportedPlayerCounts,
    )

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = stringResource(Res.string.home_mafia_title),
        subtitle = stringResource(Res.string.home_mafia_subtitle),
        tagline = stringResource(Res.string.home_mafia_tagline),
        openLabel = stringResource(Res.string.home_mafia_open),
        openContentDescription = stringResource(Res.string.home_mafia_open_description),
    )

    @Composable
    override fun Content(
        launch: GameShellLaunch,
        onExit: () -> Unit,
        backRequest: GameShellBackRequest,
        modifier: Modifier,
    ) {
        require(launch.gameId == definition.id) { "Mafia binding received another game" }
        MafiaShellContent(
            launch = launch,
            capabilities = capabilities,
            onExit = onExit,
            backRequest = backRequest,
            modifier = modifier,
        )
    }
}

private enum class MafiaShellScreen {
    Setup,
    LocalGame,
    HostPermission,
    HostName,
    HostLobby,
    JoinPermission,
    JoinName,
    JoinPrompt,
    PeerLobby,
    ResumePermission,
    ResumePeer,
}

@Suppress("LongMethod") // Exhaustive declarative rendering of the shell's typed screen state.
@Composable
private fun MafiaShellContent(
    launch: GameShellLaunch,
    capabilities: GameShellCapabilities,
    onExit: () -> Unit,
    backRequest: GameShellBackRequest,
    modifier: Modifier,
) {
    val transport: RoomTransport = koinInject()
    val permissionGate = rememberP2pPermissionGate(transport.localNetworkAccess)
    val openNetworkSettings = permissionGate::openAppSettings.takeIf {
        permissionGate.canOpenNetworkSettings
    }
    val restoredRoute = (launch as? GameShellLaunch.RestoreOwnedMultiplayer)?.route

    var screen by remember(launch) { mutableStateOf(launch.initialMafiaScreen()) }
    var activeBackRequestId by remember(launch) { mutableStateOf(0L) }
    val resumeSessionId = (launch as? GameShellLaunch.ResumeLocal)?.sessionId
    var hostName by remember(launch) {
        mutableStateOf(
            restoredRoute
                ?.takeIf { it.role == MultiplayerSessionRole.Host }
                ?.displayName
                .orEmpty(),
        )
    }
    var peerName by remember(launch) {
        mutableStateOf(
            when (launch) {
                is GameShellLaunch.ResumeMultiplayer -> launch.displayName
                is GameShellLaunch.RestoreOwnedMultiplayer -> launch.route
                    .takeIf { it.role == MultiplayerSessionRole.Peer }
                    ?.displayName
                    .orEmpty()
                else -> ""
            },
        )
    }
    var pendingJoinCode by remember(launch) {
        mutableStateOf(
            restoredRoute
                ?.takeIf { it.role == MultiplayerSessionRole.Peer }
                ?.roomCode
                .orEmpty(),
        )
    }

    LaunchedEffect(backRequest.id) {
        if (backRequest.id > 0L) {
            when (screen) {
                MafiaShellScreen.LocalGame,
                MafiaShellScreen.HostLobby,
                MafiaShellScreen.PeerLobby,
                MafiaShellScreen.ResumePeer,
                -> activeBackRequestId = backRequest.id
                else -> onExit()
            }
        }
    }

    when (screen) {
        MafiaShellScreen.Setup -> PlayModePickerScreen(
            onModeSelected = { mode ->
                if (
                    mode is PlayMode.PassAndPlay &&
                    capabilities.supports(mode)
                ) {
                    screen = MafiaShellScreen.LocalGame
                }
            },
            onHost = { screen = MafiaShellScreen.HostPermission },
            onJoin = { screen = MafiaShellScreen.JoinPermission },
            onBack = onExit,
            capabilities = capabilities,
            modifier = modifier,
        )

        MafiaShellScreen.LocalGame -> MafiaGameFlow(
            onBackToHome = onExit,
            resumeSessionId = resumeSessionId,
            backRequestId = activeBackRequestId,
            modifier = modifier,
        )

        MafiaShellScreen.HostPermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = MafiaShellScreen.HostName },
            onBack = onExit,
        )

        MafiaShellScreen.HostName -> NameInputScreen(
            isHost = true,
            initial = hostName,
            onConfirm = { name ->
                hostName = name
                screen = MafiaShellScreen.HostLobby
            },
            onBack = onExit,
            modifier = modifier,
        )

        MafiaShellScreen.HostLobby -> {
            if (hostName.isBlank()) {
                InvalidGameRouteFallback(onExit)
            } else {
                MafiaHostLobbyFlow(
                    transport = transport,
                    hostName = hostName,
                    onBackToHome = onExit,
                    onOpenNetworkSettings = openNetworkSettings,
                    backRequestId = activeBackRequestId,
                    modifier = modifier,
                )
            }
        }

        MafiaShellScreen.JoinPermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = MafiaShellScreen.JoinName },
            onBack = onExit,
        )

        MafiaShellScreen.JoinName -> NameInputScreen(
            isHost = false,
            initial = peerName,
            onConfirm = { name ->
                peerName = name
                screen = MafiaShellScreen.JoinPrompt
            },
            onBack = onExit,
            modifier = modifier,
        )

        MafiaShellScreen.JoinPrompt -> JoinPromptScreen(
            onConfirm = { code ->
                pendingJoinCode = code
                screen = MafiaShellScreen.PeerLobby
            },
            onCancel = onExit,
            modifier = modifier,
        )

        MafiaShellScreen.PeerLobby -> {
            if (pendingJoinCode.isBlank() || peerName.isBlank()) {
                InvalidGameRouteFallback(onExit)
            } else {
                MafiaPeerLobbyFlow(
                    transport = transport,
                    code = pendingJoinCode,
                    peerName = peerName,
                    onBackToHome = onExit,
                    onOpenNetworkSettings = openNetworkSettings,
                    backRequestId = activeBackRequestId,
                    modifier = modifier,
                )
            }
        }

        MafiaShellScreen.ResumePermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = MafiaShellScreen.ResumePeer },
            onBack = onExit,
        )

        MafiaShellScreen.ResumePeer -> MafiaPeerLobbyFlow(
            transport = transport,
            code = "",
            peerName = peerName,
            resumeExistingSession = true,
            onBackToHome = onExit,
            onOpenNetworkSettings = openNetworkSettings,
            backRequestId = activeBackRequestId,
            modifier = modifier,
        )
    }
}

private fun GameShellLaunch.initialMafiaScreen(): MafiaShellScreen = when (this) {
    is GameShellLaunch.New -> MafiaShellScreen.Setup
    is GameShellLaunch.ResumeLocal -> MafiaShellScreen.LocalGame
    is GameShellLaunch.ResumeMultiplayer -> MafiaShellScreen.ResumePermission
    is GameShellLaunch.RestoreOwnedMultiplayer -> when (route.role) {
        MultiplayerSessionRole.Host -> MafiaShellScreen.HostLobby
        MultiplayerSessionRole.Peer -> if (route.resumeExistingSession) {
            MafiaShellScreen.ResumePeer
        } else {
            MafiaShellScreen.PeerLobby
        }
    }
}
