package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.permissions.rememberP2pPermissionGate
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightVisibilityReader
import com.parlor.app.resources.Res
import com.parlor.app.resources.home_lastlight_open
import com.parlor.app.resources.home_lastlight_open_description
import com.parlor.app.resources.home_lastlight_kicker
import com.parlor.app.resources.home_lastlight_subtitle
import com.parlor.app.resources.home_lastlight_tagline
import com.parlor.app.resources.home_lastlight_title
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.app.shell.playmode.PlayModePickerScreen
import com.parlor.engine.definition.GameDefinition
import com.parlor.designsystem.theme.ParlorAccent
import com.parlor.designsystem.theme.ParlorAccentScope
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.lastlight.ui.flow.multidevice.LastLightHostLobbyFlow
import com.parlor.games.lastlight.ui.flow.multidevice.LastLightHostRoomBridge
import com.parlor.games.lastlight.ui.flow.multidevice.LastLightPeerLobbyFlow
import com.parlor.games.lastlight.ui.flow.passandplay.LastLightGameFlow
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.PlayMode
import com.parlor.session.multidevice.MultiplayerSessionRole
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal class LastLightGameShellBinding(
    private val lastlightDefinition: LastLightDefinition,
) : GameShellBinding {
    override val definition: GameDefinition<*, *, *> = lastlightDefinition
    override val capabilities = GameShellCapabilities(
        setOf(
            GameEntryMode.PassAndPlay,
            GameEntryMode.Host,
            GameEntryMode.Join,
        ),
    )
    override val multiplayerContract = GameShellMultiplayerContract(
        gameId = lastlightDefinition.id,
        gameVersion = LastLightHostRoomBridge.GAME_VERSION,
        supportedPlayerCounts = lastlightDefinition.supportedPlayerCounts,
    )

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = stringResource(Res.string.home_lastlight_title),
        subtitle = stringResource(Res.string.home_lastlight_subtitle),
        tagline = stringResource(Res.string.home_lastlight_tagline),
        openLabel = stringResource(Res.string.home_lastlight_open),
        openContentDescription = stringResource(Res.string.home_lastlight_open_description),
        kicker = stringResource(Res.string.home_lastlight_kicker),
        accent = ParlorAccent.Amber,
    )

    @Composable
    override fun Content(
        launch: GameShellLaunch,
        onExit: () -> Unit,
        backRequest: GameShellBackRequest,
        modifier: Modifier,
    ) {
        require(launch.gameId == definition.id) { "LastLight binding received another game" }
        val lifecycle: AppLifecycleCoordinator = koinInject()
        val visibility by lifecycle.visibility.collectAsState()
        val readVisibility = remember(lifecycle) {
            {
                val current = lifecycle.visibility.value
                LastLightProcessVisibility(!current.privateContentCovered, current.privacyEpoch)
            }
        }
        CompositionLocalProvider(
            LocalLastLightProcessVisibility provides LastLightProcessVisibility(
                isForeground = !visibility.privateContentCovered,
                concealmentEpoch = visibility.privacyEpoch,
            ),
            LocalLastLightVisibilityReader provides readVisibility,
        ) {
            ParlorAccentScope(ParlorAccent.Amber) {
                LastLightShellContent(
                    launch = launch,
                    capabilities = capabilities,
                    supportedPlayerCounts = definition.supportedPlayerCounts,
                    onExit = onExit,
                    backRequest = backRequest,
                    modifier = modifier,
                )
            }
        }
    }
}

private enum class LastLightShellScreen {
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
private fun LastLightShellContent(
    launch: GameShellLaunch,
    capabilities: GameShellCapabilities,
    supportedPlayerCounts: IntRange,
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

    var screen by remember(launch) { mutableStateOf(launch.initialLastLightScreen()) }
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
                LastLightShellScreen.LocalGame,
                LastLightShellScreen.HostLobby,
                LastLightShellScreen.PeerLobby,
                LastLightShellScreen.ResumePeer,
                -> activeBackRequestId = backRequest.id
                else -> onExit()
            }
        }
    }

    when (screen) {
        LastLightShellScreen.Setup -> PlayModePickerScreen(
            onModeSelected = { mode ->
                if (
                    mode is PlayMode.PassAndPlay &&
                    capabilities.supports(mode)
                ) {
                    screen = LastLightShellScreen.LocalGame
                }
            },
            onHost = { screen = LastLightShellScreen.HostPermission },
            onJoin = { screen = LastLightShellScreen.JoinPermission },
            onBack = onExit,
            capabilities = capabilities,
            supportedPlayerCounts = supportedPlayerCounts,
            modifier = modifier,
        )

        LastLightShellScreen.LocalGame -> LastLightGameFlow(
            onBackToHome = onExit,
            resumeSessionId = resumeSessionId,
            backRequestId = activeBackRequestId,
            modifier = modifier,
        )

        LastLightShellScreen.HostPermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = LastLightShellScreen.HostName },
            onBack = onExit,
        )

        LastLightShellScreen.HostName -> NameInputScreen(
            isHost = true,
            initial = hostName,
            onConfirm = { name ->
                hostName = name
                screen = LastLightShellScreen.HostLobby
            },
            onBack = onExit,
            modifier = modifier,
        )

        LastLightShellScreen.HostLobby -> {
            if (hostName.isBlank()) {
                InvalidGameRouteFallback(onExit)
            } else {
                LastLightHostLobbyFlow(
                    transport = transport,
                    hostName = hostName,
                    onBackToHome = onExit,
                    onOpenNetworkSettings = openNetworkSettings,
                    backRequestId = activeBackRequestId,
                    modifier = modifier,
                )
            }
        }

        LastLightShellScreen.JoinPermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = LastLightShellScreen.JoinName },
            onBack = onExit,
        )

        LastLightShellScreen.JoinName -> NameInputScreen(
            isHost = false,
            initial = peerName,
            onConfirm = { name ->
                peerName = name
                screen = LastLightShellScreen.JoinPrompt
            },
            onBack = onExit,
            modifier = modifier,
        )

        LastLightShellScreen.JoinPrompt -> JoinPromptScreen(
            onConfirm = { code ->
                pendingJoinCode = code
                screen = LastLightShellScreen.PeerLobby
            },
            onCancel = onExit,
            modifier = modifier,
        )

        LastLightShellScreen.PeerLobby -> {
            if (pendingJoinCode.isBlank() || peerName.isBlank()) {
                InvalidGameRouteFallback(onExit)
            } else {
                LastLightPeerLobbyFlow(
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

        LastLightShellScreen.ResumePermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = LastLightShellScreen.ResumePeer },
            onBack = onExit,
        )

        LastLightShellScreen.ResumePeer -> LastLightPeerLobbyFlow(
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

private fun GameShellLaunch.initialLastLightScreen(): LastLightShellScreen = when (this) {
    is GameShellLaunch.New -> LastLightShellScreen.Setup
    is GameShellLaunch.ResumeLocal -> LastLightShellScreen.LocalGame
    is GameShellLaunch.ResumeMultiplayer -> LastLightShellScreen.ResumePermission
    is GameShellLaunch.RestoreOwnedMultiplayer -> when (route.role) {
        MultiplayerSessionRole.Host -> LastLightShellScreen.HostLobby
        MultiplayerSessionRole.Peer -> if (route.resumeExistingSession) {
            LastLightShellScreen.ResumePeer
        } else {
            LastLightShellScreen.PeerLobby
        }
    }
}
