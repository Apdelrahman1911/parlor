package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.PlatformBackHandler
import com.parlor.app.permissions.rememberP2pPermissionGate
import com.parlor.app.resources.Res
import com.parlor.app.resources.home_whodunit_open
import com.parlor.app.resources.home_whodunit_open_description
import com.parlor.app.resources.home_whodunit_subtitle
import com.parlor.app.resources.home_whodunit_tagline
import com.parlor.app.resources.home_whodunit_title
import com.parlor.app.shell.library.CasePickerScreen
import com.parlor.app.shell.multiplayer.HostSessionFlow
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.app.shell.multiplayer.PeerSessionFlow
import com.parlor.app.shell.playmode.PlayModePickerScreen
import com.parlor.content.repository.CaseRepository
import com.parlor.core.ids.ModeId
import com.parlor.engine.definition.GameDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.WhodunitPlayModePolicy
import com.parlor.games.whodunit.ui.flow.WhodunitGameFlow
import com.parlor.games.whodunit.ui.screens.setup.ModeSelectionScreen
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.PlayMode
import com.parlor.session.multidevice.MultiplayerSessionRole
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal class WhodunitGameShellBinding(
    private val whodunitDefinition: WhodunitDefinition,
) : GameShellBinding {
    override val definition: GameDefinition<*, *, *> = whodunitDefinition
    override val capabilities = GameShellCapabilities(
        setOf(
            GameEntryMode.PassAndPlay,
            GameEntryMode.Host,
            GameEntryMode.Join,
        ),
    )

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = stringResource(Res.string.home_whodunit_title),
        subtitle = stringResource(Res.string.home_whodunit_subtitle),
        tagline = stringResource(Res.string.home_whodunit_tagline),
        openLabel = stringResource(Res.string.home_whodunit_open),
        openContentDescription = stringResource(Res.string.home_whodunit_open_description),
    )

    @Composable
    override fun Content(
        launch: GameShellLaunch,
        onExit: () -> Unit,
        modifier: Modifier,
    ) {
        require(launch.gameId == definition.id) { "Whodunit binding received another game" }
        WhodunitShellContent(
            launch = launch,
            capabilities = capabilities,
            onExit = onExit,
            modifier = modifier,
        )
    }
}

private enum class WhodunitShellScreen {
    Setup,
    LocalCasePicker,
    LocalGame,
    HostPermission,
    HostName,
    HostCasePicker,
    HostMode,
    HostLobby,
    JoinPermission,
    JoinName,
    JoinPrompt,
    PeerLobby,
    ResumePermission,
    ResumePeer,
}

@Composable
private fun WhodunitShellContent(
    launch: GameShellLaunch,
    capabilities: GameShellCapabilities,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    val caseRepository: CaseRepository = koinInject()
    val transport: RoomTransport = koinInject()
    val permissionGate = rememberP2pPermissionGate(transport.localNetworkAccess)
    val openNetworkSettings = permissionGate::openAppSettings.takeIf {
        permissionGate.canOpenNetworkSettings
    }
    val restoredRoute = (launch as? GameShellLaunch.RestoreOwnedMultiplayer)?.route

    var screen by remember(launch) { mutableStateOf(launch.initialWhodunitScreen()) }
    var localCaseId by remember(launch) { mutableStateOf(DEFAULT_CASE_ID) }
    var localPlayMode: PlayMode by remember(launch) { mutableStateOf(PlayMode.PassAndPlay) }
    val resumeSessionId = (launch as? GameShellLaunch.ResumeLocal)?.sessionId
    var hostName by remember(launch) {
        mutableStateOf(
            restoredRoute
                ?.takeIf { it.role == MultiplayerSessionRole.Host }
                ?.displayName
                .orEmpty(),
        )
    }
    var hostCaseId by remember(launch) {
        mutableStateOf(
            restoredRoute
                ?.takeIf { it.role == MultiplayerSessionRole.Host }
                ?.contentId
                .orEmpty(),
        )
    }
    var hostModeId by remember(launch) {
        mutableStateOf(
            restoredRoute
                ?.takeIf { it.role == MultiplayerSessionRole.Host }
                ?.modeId
                ?.let(::ModeId)
                ?: WhodunitIds.ClassicVoteModeId,
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

    PlatformBackHandler(enabled = true) {
        when (screen) {
            WhodunitShellScreen.LocalCasePicker -> screen = WhodunitShellScreen.Setup
            WhodunitShellScreen.HostMode -> screen = WhodunitShellScreen.HostCasePicker
            WhodunitShellScreen.LocalGame,
            WhodunitShellScreen.HostLobby,
            WhodunitShellScreen.PeerLobby,
            WhodunitShellScreen.ResumePeer,
            -> Unit
            else -> onExit()
        }
    }

    when (screen) {
        WhodunitShellScreen.Setup -> PlayModePickerScreen(
            onModeSelected = { mode ->
                if (
                    capabilities.supports(mode) &&
                    WhodunitPlayModePolicy.supportsLocalEntry(mode)
                ) {
                    localPlayMode = mode
                    screen = WhodunitShellScreen.LocalCasePicker
                }
            },
            onHost = { screen = WhodunitShellScreen.HostPermission },
            onJoin = { screen = WhodunitShellScreen.JoinPermission },
            onBack = onExit,
            multiplayerEnabled = capabilities.supports(GameEntryMode.Host) &&
                capabilities.supports(GameEntryMode.Join),
            soloEnabled = capabilities.supports(GameEntryMode.Solo),
            modifier = modifier,
        )

        WhodunitShellScreen.LocalCasePicker -> CasePickerScreen(
            repository = caseRepository,
            onCasePicked = { summary ->
                localCaseId = summary.caseId
                screen = WhodunitShellScreen.LocalGame
            },
            onBack = { screen = WhodunitShellScreen.Setup },
            modifier = modifier,
        )

        WhodunitShellScreen.LocalGame -> WhodunitGameFlow(
            onBackToLibrary = onExit,
            modifier = modifier,
            resumeSessionId = resumeSessionId,
            caseId = localCaseId,
            playMode = localPlayMode,
        )

        WhodunitShellScreen.HostPermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = WhodunitShellScreen.HostName },
            onBack = onExit,
        )

        WhodunitShellScreen.HostName -> NameInputScreen(
            isHost = true,
            initial = hostName,
            onConfirm = { name ->
                hostName = name
                screen = WhodunitShellScreen.HostCasePicker
            },
            onBack = onExit,
            modifier = modifier,
        )

        WhodunitShellScreen.HostCasePicker -> CasePickerScreen(
            repository = caseRepository,
            onCasePicked = { summary ->
                hostCaseId = summary.caseId
                screen = WhodunitShellScreen.HostMode
            },
            onBack = onExit,
            modifier = modifier,
        )

        WhodunitShellScreen.HostMode -> ModeSelectionScreen(
            onModeSelected = { mode ->
                hostModeId = mode
                screen = WhodunitShellScreen.HostLobby
            },
            modifier = modifier,
        )

        WhodunitShellScreen.HostLobby -> {
            if (hostCaseId.isBlank() || hostName.isBlank()) {
                InvalidGameRouteFallback(onExit)
            } else {
                HostSessionFlow(
                    transport = transport,
                    caseId = hostCaseId,
                    modeId = hostModeId,
                    hostName = hostName,
                    onBackToLibrary = onExit,
                    onOpenNetworkSettings = openNetworkSettings,
                    modifier = modifier,
                )
            }
        }

        WhodunitShellScreen.JoinPermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = WhodunitShellScreen.JoinName },
            onBack = onExit,
        )

        WhodunitShellScreen.JoinName -> NameInputScreen(
            isHost = false,
            initial = peerName,
            onConfirm = { name ->
                peerName = name
                screen = WhodunitShellScreen.JoinPrompt
            },
            onBack = onExit,
            modifier = modifier,
        )

        WhodunitShellScreen.JoinPrompt -> JoinPromptScreen(
            onConfirm = { code ->
                pendingJoinCode = code
                screen = WhodunitShellScreen.PeerLobby
            },
            onCancel = onExit,
            modifier = modifier,
        )

        WhodunitShellScreen.PeerLobby -> {
            if (pendingJoinCode.isBlank() || peerName.isBlank()) {
                InvalidGameRouteFallback(onExit)
            } else {
                PeerSessionFlow(
                    transport = transport,
                    code = pendingJoinCode,
                    peerName = peerName,
                    onBackToLibrary = onExit,
                    onOpenNetworkSettings = openNetworkSettings,
                    modifier = modifier,
                )
            }
        }

        WhodunitShellScreen.ResumePermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = WhodunitShellScreen.ResumePeer },
            onBack = onExit,
        )

        WhodunitShellScreen.ResumePeer -> PeerSessionFlow(
            transport = transport,
            code = "",
            peerName = peerName,
            resumeExistingSession = true,
            onBackToLibrary = onExit,
            onOpenNetworkSettings = openNetworkSettings,
            modifier = modifier,
        )
    }
}

private fun GameShellLaunch.initialWhodunitScreen(): WhodunitShellScreen = when (this) {
    is GameShellLaunch.New -> WhodunitShellScreen.Setup
    is GameShellLaunch.ResumeLocal -> WhodunitShellScreen.LocalGame
    is GameShellLaunch.ResumeMultiplayer -> WhodunitShellScreen.ResumePermission
    is GameShellLaunch.RestoreOwnedMultiplayer -> when (route.role) {
        MultiplayerSessionRole.Host -> WhodunitShellScreen.HostLobby
        MultiplayerSessionRole.Peer -> if (route.resumeExistingSession) {
            WhodunitShellScreen.ResumePeer
        } else {
            WhodunitShellScreen.PeerLobby
        }
    }
}

private const val DEFAULT_CASE_ID = "last-dinner"
