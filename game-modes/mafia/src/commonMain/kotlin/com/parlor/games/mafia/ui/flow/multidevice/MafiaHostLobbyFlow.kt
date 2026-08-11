package com.parlor.games.mafia.ui.flow.multidevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.SessionExitAffordance
import com.parlor.designsystem.components.SessionExitBackAction
import com.parlor.designsystem.components.SessionExitConfirmation
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.designsystem.components.StickyActionBar
import com.parlor.designsystem.components.sessionExitBackAction
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.md_host_cancel
import com.parlor.games.mafia.resources.md_host_cancel_description
import com.parlor.games.mafia.resources.md_host_admission_failed
import com.parlor.games.mafia.resources.md_host_approve
import com.parlor.games.mafia.resources.md_host_approve_description
import com.parlor.games.mafia.resources.md_host_decline
import com.parlor.games.mafia.resources.md_host_decline_description
import com.parlor.games.mafia.resources.md_host_error_detail
import com.parlor.games.mafia.resources.md_host_error_title
import com.parlor.games.mafia.resources.md_host_eyebrow
import com.parlor.games.mafia.resources.md_host_hosting_as_format
import com.parlor.games.mafia.resources.md_host_opening_room
import com.parlor.games.mafia.resources.md_host_join_request_format
import com.parlor.games.mafia.resources.md_host_pending_eyebrow
import com.parlor.games.mafia.resources.md_host_player_bullet_format
import com.parlor.games.mafia.resources.md_host_players_in_room
import com.parlor.games.mafia.resources.md_host_room_code
import com.parlor.games.mafia.resources.md_host_start_description
import com.parlor.games.mafia.resources.md_host_start_blocked
import com.parlor.games.mafia.resources.md_host_start_need_more
import com.parlor.games.mafia.resources.md_host_start_pending
import com.parlor.games.mafia.resources.md_host_start_too_many
import com.parlor.games.mafia.resources.md_host_start_failed
import com.parlor.games.mafia.resources.md_host_start_with_format
import com.parlor.games.mafia.resources.md_host_waiting_for_players
import com.parlor.games.mafia.resources.md_network_open_settings
import com.parlor.games.mafia.resources.md_network_open_settings_description
import com.parlor.games.mafia.resources.md_network_recovery_help
import com.parlor.games.mafia.resources.md_network_retry
import com.parlor.games.mafia.resources.md_network_retry_description
import com.parlor.games.mafia.resources.setup_back
import com.parlor.games.mafia.resources.setup_back_description
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomMember
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.security.SecureIds
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.needsRecoveryGuidance
import com.parlor.session.multidevice.MultiplayerOpenMode
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Mafia-side host lobby. Mirrors composeApp's shell `HostSessionFlow` but
 * is Mafia-specific: no case loading (Mafia has no external content), and
 * once started it dispatches to [MafiaMultiDeviceHostFlow] instead of the
 * Whodunit host flow. Lives in the Mafia module so composeApp's shell
 * doesn't need to know anything Mafia-specific.
 */
@Composable
fun MafiaHostLobbyFlow(
    transport: RoomTransport,
    hostName: String,
    onBackToHome: () -> Unit,
    onOpenNetworkSettings: (() -> Unit)? = null,
    backRequestId: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val sessionOwner: ProcessMultiplayerSessionOwner = koinInject()
    val scope = rememberCoroutineScope()
    val toastState = LocalParlorToastState.current
    val startBlockedText = stringResource(Res.string.md_host_start_blocked)
    val startFailedText = stringResource(Res.string.md_host_start_failed)
    val route = remember(hostName) {
        MultiplayerSessionRoute.host(
            gameId = MafiaIds.GameId,
            displayName = hostName,
        )
    }
    val ownerState by sessionOwner.state.collectAsState()
    val ownedSession = (ownerState as? ProcessMultiplayerState.Active)
        ?.session
        ?.takeIf { it.route == route }
    val ownerError = (ownerState as? ProcessMultiplayerState.Failed)
        ?.takeIf { it.route == route }
        ?.error
    var acquireError by remember(route) { mutableStateOf<NetError?>(null) }
    var hostAttempt by remember { mutableStateOf(0) }
    var startInFlight by remember(route) { mutableStateOf(false) }
    var leaveInFlight by remember(route) { mutableStateOf(false) }
    var leaveConfirmationOpen by remember(route) { mutableStateOf(false) }

    val leaveToHome: (SessionEndReason) -> Unit = { reason ->
        if (!leaveInFlight) {
            leaveInFlight = true
            scope.launch {
                when (val left = sessionOwner.leaveRoute(route, reason)) {
                    is Result.Success -> onBackToHome()
                    is Result.Failure -> {
                        acquireError = left.error
                        leaveInFlight = false
                        leaveConfirmationOpen = false
                    }
                }
            }
        }
    }
    val cancelToHome: () -> Unit = { leaveToHome(SessionEndReason.Cancelled) }

    LaunchedEffect(transport, route, hostAttempt) {
        acquireError = null
        when (
            val result = sessionOwner.acquire(
                route = route,
                hostSeed = SecureIds.randomLong(),
            ) { mode ->
                check(mode == MultiplayerOpenMode.Host)
                transport.host(
                    HostConfig(
                        hostDisplayName = hostName,
                        maxRemotePlayers = MafiaSettings.MAX_PLAYERS - 1,
                        gameProtocol = HostedGameProtocol(
                            gameId = MafiaIds.GameId,
                            gameVersion = MafiaHostRoomBridge.GAME_VERSION,
                        ),
                    ),
                )
            }
        ) {
            is Result.Success -> startInFlight = false
            is Result.Failure -> acquireError = result.error
        }
    }

    val frozenRoster by produceState<List<RoomMember>?>(
        initialValue = ownedSession?.frozenRoster?.value,
        key1 = ownedSession,
    ) {
        val session = ownedSession
        if (session == null) {
            value = null
        } else {
            session.frozenRoster.collect { value = it }
        }
    }

    val current = ownedSession?.room
    val localNetworkAccess by transport.localNetworkAccess.collectAsState()
    var gameStarted by remember(route) { mutableStateOf(false) }
    LaunchedEffect(frozenRoster) {
        if (frozenRoster != null) gameStarted = true
    }
    val gameIsActive = gameStarted || frozenRoster != null
    LaunchedEffect(backRequestId) {
        if (backRequestId > 0L && !leaveInFlight) {
            when (sessionExitBackAction(SessionExitKind.Host, gameIsActive)) {
                SessionExitBackAction.Confirm -> leaveConfirmationOpen = true
                SessionExitBackAction.ExitImmediately -> cancelToHome()
            }
        }
    }
    if (leaveConfirmationOpen) {
        SessionExitConfirmation(
            kind = SessionExitKind.Host,
            onStay = { leaveConfirmationOpen = false },
            onExit = { leaveToHome(SessionEndReason.HostLeft) },
            exitInFlight = leaveInFlight,
            destructive = true,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            when {
                ownerError != null || acquireError != null -> MafiaLobbyErrorState(
                    title = stringResource(Res.string.md_host_error_title),
                    detail = stringResource(Res.string.md_host_error_detail),
                    showNetworkRecovery = localNetworkAccess.needsRecoveryGuidance,
                    onRetry = { hostAttempt++ },
                    onOpenNetworkSettings = onOpenNetworkSettings.takeIf {
                        localNetworkAccess.needsRecoveryGuidance
                    },
                    onBack = cancelToHome,
                    actionsEnabled = !leaveInFlight,
                    backInFlight = leaveInFlight,
                    modifier = Modifier.fillMaxSize(),
                )
                current == null -> MafiaLobbyLoadingState(
                    label = stringResource(Res.string.md_host_opening_room),
                    onLeave = cancelToHome,
                    leaveEnabled = !leaveInFlight,
                    leaveInFlight = leaveInFlight,
                    modifier = Modifier.fillMaxSize(),
                )
                frozenRoster == null -> MafiaHostLobbyContent(
                    room = current,
                    hostName = hostName,
                    startInFlight = startInFlight,
                    onStart = {
                        if (!startInFlight) {
                            startInFlight = true
                            scope.launch {
                                when (
                                    val frozen = checkNotNull(ownedSession).freezeAdmissions()
                                ) {
                                    is Result.Success -> Unit
                                    is Result.Failure -> {
                                        val blocked = frozen.error == NetError.CommandInFlight
                                        toastState.show(
                                            text = if (blocked) {
                                                startBlockedText
                                            } else {
                                                startFailedText
                                            },
                                            severity = if (blocked) {
                                                ParlorToastSeverity.Warning
                                            } else {
                                                ParlorToastSeverity.Danger
                                            },
                                        )
                                        startInFlight = false
                                    }
                                }
                            }
                        }
                    },
                    onLeave = cancelToHome,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> {
                    val roster = checkNotNull(frozenRoster)
                    val players = remember(roster, hostName, current) {
                        val hostPlayer = Player(
                            id = current.info.value.hostPlayerId,
                            displayName = hostName,
                            seat = 0,
                        )
                        val peers = roster.mapIndexed { index, member ->
                            Player(member.playerId, member.displayName, seat = index + 1)
                        }
                        listOf(hostPlayer) + peers
                    }
                    MafiaMultiDeviceHostFlow(
                        players = players,
                        ownedSession = checkNotNull(ownedSession),
                        sessionOwner = sessionOwner,
                        onBackToHome = onBackToHome,
                        onRetryStart = {
                            startInFlight = false
                            hostAttempt++
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (gameIsActive) {
                SessionExitAffordance(
                    onClick = { leaveConfirmationOpen = true },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(ParlorTheme.spacing.m),
                )
            }
        }
    }
}

@Composable
private fun MafiaHostLobbyContent(
    room: LocalRoom,
    hostName: String,
    startInFlight: Boolean,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info by room.info.collectAsState()
    val members by room.members.collectAsState()
    val pendingAdmissions by room.pendingAdmissions.collectAsState()
    val connectedMembers = members.filter(RoomMember::connected)
    val scope = rememberCoroutineScope()
    val toastState = LocalParlorToastState.current
    val admissionFailedText = stringResource(Res.string.md_host_admission_failed)
    var admissionsInFlight by remember(room) { mutableStateOf(emptySet<PlayerId>()) }
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ParlorTheme.spacing.l,
                        end = ParlorTheme.spacing.l,
                        top = ParlorTheme.spacing.l,
                        bottom = ParlorTheme.spacing.xxxl + ParlorTheme.spacing.xxl,
                    ),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            ) {
                EyebrowLabel(text = stringResource(Res.string.md_host_eyebrow))

                ParlorCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = ParlorTheme.radii.elevated,
                    contentPadding = ParlorTheme.spacing.l,
                    hero = true,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                        EyebrowLabel(text = stringResource(Res.string.md_host_room_code), accent = false)
                        Text(
                            text = info.code,
                            style = ParlorTheme.typography.displayHero,
                            color = ParlorTheme.colors.accentEmber,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(Res.string.md_host_hosting_as_format, hostName),
                            style = ParlorTheme.typography.bodyMedium,
                            color = ParlorTheme.colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                EyebrowLabel(text = stringResource(Res.string.md_host_players_in_room), accent = false)
                if (connectedMembers.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.md_host_waiting_for_players),
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textTertiary,
                    )
                } else {
                    connectedMembers.forEach { member ->
                        Text(
                            text = stringResource(Res.string.md_host_player_bullet_format, member.displayName),
                            style = ParlorTheme.typography.bodyLarge,
                            color = ParlorTheme.colors.textPrimary,
                        )
                    }
                }

                if (pendingAdmissions.isNotEmpty()) {
                    EyebrowLabel(
                        text = stringResource(Res.string.md_host_pending_eyebrow),
                        accent = false,
                    )
                    pendingAdmissions.forEach { admission ->
                        ParlorCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = ParlorTheme.spacing.m,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                            ) {
                                Text(
                                    text = stringResource(
                                        Res.string.md_host_join_request_format,
                                        admission.displayName,
                                    ),
                                    style = ParlorTheme.typography.bodyLarge,
                                    color = ParlorTheme.colors.textPrimary,
                                )
                                ParlorButton(
                                    label = stringResource(Res.string.md_host_approve),
                                    contentDescription = stringResource(
                                        Res.string.md_host_approve_description,
                                        admission.displayName,
                                    ),
                                    onClick = {
                                        if (admission.playerId !in admissionsInFlight) {
                                            admissionsInFlight += admission.playerId
                                            scope.launch {
                                                val approved = room.approveAdmission(
                                                    admission.playerId,
                                                )
                                                if (approved is Result.Failure) {
                                                    toastState.show(
                                                        admissionFailedText,
                                                        ParlorToastSeverity.Danger,
                                                    )
                                                }
                                                admissionsInFlight -= admission.playerId
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = admission.playerId !in admissionsInFlight,
                                )
                                ParlorButton(
                                    label = stringResource(Res.string.md_host_decline),
                                    contentDescription = stringResource(
                                        Res.string.md_host_decline_description,
                                        admission.displayName,
                                    ),
                                    onClick = {
                                        if (admission.playerId !in admissionsInFlight) {
                                            admissionsInFlight += admission.playerId
                                            scope.launch {
                                                val rejected = room.rejectAdmission(
                                                    admission.playerId,
                                                )
                                                if (rejected is Result.Failure) {
                                                    toastState.show(
                                                        admissionFailedText,
                                                        ParlorToastSeverity.Danger,
                                                    )
                                                }
                                                admissionsInFlight -= admission.playerId
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    variant = ParlorButtonVariant.Secondary,
                                    enabled = admission.playerId !in admissionsInFlight,
                                )
                            }
                        }
                    }
                }
            }

            val playerCount = connectedMembers.size + 1
            val canStart =
                playerCount in MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS &&
                    pendingAdmissions.isEmpty()
            val startLabel = when {
                pendingAdmissions.isNotEmpty() ->
                    stringResource(Res.string.md_host_start_pending)
                playerCount < MafiaSettings.MIN_PLAYERS ->
                    stringResource(Res.string.md_host_start_need_more)
                playerCount > MafiaSettings.MAX_PLAYERS ->
                    stringResource(
                        Res.string.md_host_start_too_many,
                        MafiaSettings.MAX_PLAYERS,
                    )
                else -> stringResource(Res.string.md_host_start_with_format, playerCount)
            }
            StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                ParlorButton(
                    label = startLabel,
                    contentDescription = stringResource(Res.string.md_host_start_description),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canStart && !startInFlight,
                )
                ParlorButton(
                    label = stringResource(Res.string.md_host_cancel),
                    contentDescription = stringResource(Res.string.md_host_cancel_description),
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ParlorButtonVariant.Secondary,
                )
            }
        }
    }
}

@Composable
private fun MafiaLobbyLoadingState(
    label: String,
    onLeave: () -> Unit,
    leaveEnabled: Boolean,
    leaveInFlight: Boolean,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CandleFlame(size = androidx.compose.ui.unit.Dp(72f))
            Text(
                text = label,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.md_host_cancel),
                contentDescription = stringResource(Res.string.md_host_cancel_description),
                onClick = onLeave,
                enabled = leaveEnabled,
                loading = leaveInFlight,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun MafiaLobbyErrorState(
    title: String,
    detail: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showNetworkRecovery: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onOpenNetworkSettings: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
    backInFlight: Boolean = false,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
            if (showNetworkRecovery) {
                Text(
                    text = stringResource(Res.string.md_network_recovery_help),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                ParlorButton(
                    label = stringResource(Res.string.md_network_retry),
                    contentDescription = stringResource(Res.string.md_network_retry_description),
                    onClick = onRetry,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (onOpenNetworkSettings != null) {
                ParlorButton(
                    label = stringResource(Res.string.md_network_open_settings),
                    contentDescription = stringResource(
                        Res.string.md_network_open_settings_description,
                    ),
                    onClick = onOpenNetworkSettings,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ParlorButtonVariant.Secondary,
                )
            }
            ParlorButton(
                label = stringResource(Res.string.setup_back),
                contentDescription = stringResource(Res.string.setup_back_description),
                onClick = onBack,
                enabled = actionsEnabled,
                loading = backInFlight,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}
