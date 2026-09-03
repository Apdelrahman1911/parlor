package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.permissions.rememberP2pPermissionGate
import com.parlor.app.resources.Res
import com.parlor.app.resources.home_whodunit_open
import com.parlor.app.resources.home_whodunit_open_description
import com.parlor.app.resources.home_whodunit_subtitle
import com.parlor.app.resources.home_whodunit_tagline
import com.parlor.app.resources.home_whodunit_title
import com.parlor.games.whodunit.ui.screens.setup.WhodunitCasePickerScreen
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostSessionFlow
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitPeerSessionFlow
import com.parlor.app.shell.playmode.PlayModePickerScreen
import com.parlor.content.repository.CaseRepository
import com.parlor.core.ids.ModeId
import com.parlor.engine.definition.GameDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.WhodunitPlayModePolicy
import com.parlor.games.whodunit.ui.flow.WhodunitGameFlow
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.games.whodunit.ui.screens.setup.ModeSelectionScreen
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.PlayMode
import com.parlor.session.multidevice.MultiplayerSessionRole
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal val SHIPPED_WHODUNIT_PLAYER_COUNTS: IntRange = 6..6

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
    override val multiplayerContract = GameShellMultiplayerContract(
        gameId = WhodunitIds.GameId,
        gameVersion = WhodunitHostRoomBridge.GAME_VERSION,
        supportedPlayerCounts = SHIPPED_WHODUNIT_PLAYER_COUNTS,
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
        backRequest: GameShellBackRequest,
        modifier: Modifier,
    ) {
        require(launch.gameId == definition.id) { "Whodunit binding received another game" }
        WhodunitShellContent(
            launch = launch,
            capabilities = capabilities,
            supportedPlayerCounts = SHIPPED_WHODUNIT_PLAYER_COUNTS,
            onExit = onExit,
            backRequest = backRequest,
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

@Suppress("LongMethod") // Exhaustive declarative rendering of the shell's typed screen state.
@Composable
private fun WhodunitShellContent(
    launch: GameShellLaunch,
    capabilities: GameShellCapabilities,
    supportedPlayerCounts: IntRange,
    onExit: () -> Unit,
    backRequest: GameShellBackRequest,
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
    var activeBackRequestId by remember(launch) { mutableStateOf(0L) }
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
    var hostCaseModes by remember(launch) { mutableStateOf<List<String>>(emptyList()) }
    var hostCasePlayerCounts by remember(launch) { mutableStateOf<IntRange?>(null) }
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

    LaunchedEffect(backRequest.id) {
        if (backRequest.id > 0L) {
            when (screen) {
                WhodunitShellScreen.LocalCasePicker -> screen = WhodunitShellScreen.Setup
                WhodunitShellScreen.HostMode -> screen = WhodunitShellScreen.HostCasePicker
                WhodunitShellScreen.LocalGame,
                WhodunitShellScreen.HostLobby,
                WhodunitShellScreen.PeerLobby,
                WhodunitShellScreen.ResumePeer,
                -> activeBackRequestId = backRequest.id
                else -> onExit()
            }
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
            capabilities = capabilities,
            supportedPlayerCounts = supportedPlayerCounts,
            modifier = modifier,
        )

        WhodunitShellScreen.LocalCasePicker -> WhodunitCasePickerScreen(
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
            backRequestId = activeBackRequestId,
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

        WhodunitShellScreen.HostCasePicker -> WhodunitCasePickerScreen(
            repository = caseRepository,
            onCasePicked = { summary ->
                hostCaseId = summary.caseId
                hostCaseModes = summary.supportedModes
                hostCasePlayerCounts = summary.supportedPlayerCounts.toIntRange()
                screen = WhodunitShellScreen.HostMode
            },
            onBack = onExit,
            modifier = modifier,
        )

        WhodunitShellScreen.HostMode -> {
            val casePlayerCounts = hostCasePlayerCounts
            if (casePlayerCounts == null || hostCaseModes.isEmpty()) {
                InvalidGameRouteFallback { screen = WhodunitShellScreen.HostCasePicker }
            } else {
                ModeSelectionScreen(
                    onModeSelected = { mode ->
                        hostModeId = mode
                        screen = WhodunitShellScreen.HostLobby
                    },
                    onBack = { screen = WhodunitShellScreen.HostCasePicker },
                    caseSupportedModes = hostCaseModes,
                    caseSupportedPlayerCounts = casePlayerCounts,
                    modifier = modifier,
                )
            }
        }

        WhodunitShellScreen.HostLobby -> {
            if (hostCaseId.isBlank() || hostName.isBlank()) {
                InvalidGameRouteFallback(onExit)
            } else {
                WhodunitHostSessionFlow(
                    transport = transport,
                    caseId = hostCaseId,
                    modeId = hostModeId,
                    hostName = hostName,
                    onBackToLibrary = onExit,
                    onOpenNetworkSettings = openNetworkSettings,
                    backRequestId = activeBackRequestId,
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
                WhodunitPeerSessionFlow(
                    transport = transport,
                    code = pendingJoinCode,
                    peerName = peerName,
                    onBackToLibrary = onExit,
                    onOpenNetworkSettings = openNetworkSettings,
                    backRequestId = activeBackRequestId,
                    modifier = modifier,
                )
            }
        }

        WhodunitShellScreen.ResumePermission -> P2pPermissionRoute(
            gate = permissionGate,
            onContinue = { screen = WhodunitShellScreen.ResumePeer },
            onBack = onExit,
        )

        WhodunitShellScreen.ResumePeer -> WhodunitPeerSessionFlow(
            transport = transport,
            code = "",
            peerName = peerName,
            resumeExistingSession = true,
            onBackToLibrary = onExit,
            onOpenNetworkSettings = openNetworkSettings,
            backRequestId = activeBackRequestId,
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
