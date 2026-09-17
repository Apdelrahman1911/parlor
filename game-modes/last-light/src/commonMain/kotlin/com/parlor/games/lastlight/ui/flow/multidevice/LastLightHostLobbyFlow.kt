package com.parlor.games.lastlight.ui.flow.multidevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.ContextRibbon
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorContextTone
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.SessionExitBackAction
import com.parlor.games.lastlight.ui.flow.common.LastLightExitConfirmation
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.designsystem.components.StickyActionLayout
import com.parlor.designsystem.components.sessionExitBackAction
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.md_host_cancel
import com.parlor.games.lastlight.resources.md_host_cancel_description
import com.parlor.games.lastlight.resources.md_host_admission_failed
import com.parlor.games.lastlight.resources.md_host_approve
import com.parlor.games.lastlight.resources.md_host_approve_description
import com.parlor.games.lastlight.resources.md_host_decline
import com.parlor.games.lastlight.resources.md_host_decline_description
import com.parlor.games.lastlight.resources.md_host_error_title
import com.parlor.games.lastlight.resources.md_host_eyebrow
import com.parlor.games.lastlight.resources.md_host_hosting_as_format
import com.parlor.games.lastlight.resources.md_host_context_authority
import com.parlor.games.lastlight.resources.md_host_context_local
import com.parlor.games.lastlight.resources.md_host_opening_room
import com.parlor.games.lastlight.resources.md_host_join_request_format
import com.parlor.games.lastlight.resources.md_host_pending_eyebrow
import com.parlor.games.lastlight.resources.md_host_player_bullet_format
import com.parlor.games.lastlight.resources.md_host_players_in_room
import com.parlor.games.lastlight.resources.md_host_room_code
import com.parlor.games.lastlight.resources.md_host_start_description
import com.parlor.games.lastlight.resources.md_host_start_blocked
import com.parlor.games.lastlight.resources.md_host_start_need_more
import com.parlor.games.lastlight.resources.md_host_start_pending
import com.parlor.games.lastlight.resources.md_host_start_too_many
import com.parlor.games.lastlight.resources.md_host_start_failed
import com.parlor.games.lastlight.resources.md_host_start_with_format
import com.parlor.games.lastlight.resources.md_host_waiting_for_players
import com.parlor.games.lastlight.resources.md_network_open_settings
import com.parlor.games.lastlight.resources.md_network_open_settings_description
import com.parlor.games.lastlight.resources.md_network_recovery_help
import com.parlor.games.lastlight.resources.md_network_retry
import com.parlor.games.lastlight.resources.md_network_retry_description
import com.parlor.games.lastlight.resources.md_back
import com.parlor.games.lastlight.resources.md_back_description
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

/** Last Light host entry owned by the retained multiplayer session. */
@Composable
fun LastLightHostLobbyFlow(
    transport: RoomTransport,
    hostName: String,
    onBackToHome: () -> Unit,
    onOpenNetworkSettings: (() -> Unit)? = null,
    backRequestId: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val sessionOwner: ProcessMultiplayerSessionOwner = koinInject()
    val scope = rememberCoroutineScope()
    val presentationState = rememberSaveableStateHolder()
    val toastState = LocalParlorToastState.current
    val startBlockedText = stringResource(Res.string.md_host_start_blocked)
    val startFailedText = stringResource(Res.string.md_host_start_failed)
    val route = remember(hostName) {
        MultiplayerSessionRoute.host(
            gameId = LastLightIds.GameId,
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
    var retryInFlight by remember(route) { mutableStateOf(false) }
    var leaveConfirmationOpen by remember(route) { mutableStateOf(false) }

    val leaveToHome: (SessionEndReason) -> Unit = { reason ->
        if (hostLobbyActionsEnabled(leaveInFlight, retryInFlight)) {
            leaveInFlight = true
            val presentationKey = (ownedSession?.runtime?.value as? LastLightHostRuntime)?.bridge?.protocol?.sessionId?.raw
            scope.launch {
                when (val left = sessionOwner.leaveRoute(route, reason)) {
                    is Result.Success -> {
                        presentationKey?.let(presentationState::removeState)
                        onBackToHome()
                    }
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
                        maxRemotePlayers = LAST_LIGHT_MAX_PLAYERS - 1,
                        gameProtocol = HostedGameProtocol(
                            gameId = LastLightIds.GameId,
                            gameVersion = LastLightHostRoomBridge.GAME_VERSION,
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
    val renderedHostError = ownerError ?: acquireError
    var gameStarted by remember(route) { mutableStateOf(false) }
    LaunchedEffect(frozenRoster) {
        if (frozenRoster != null) gameStarted = true
    }
    val gameIsActive = gameStarted || frozenRoster != null
    LaunchedEffect(backRequestId) {
        if (backRequestId > 0L && hostLobbyActionsEnabled(leaveInFlight, retryInFlight)) {
            when (sessionExitBackAction(SessionExitKind.Host, gameIsActive)) {
                SessionExitBackAction.Confirm -> leaveConfirmationOpen = true
                SessionExitBackAction.ExitImmediately -> cancelToHome()
            }
        }
    }
    if (leaveConfirmationOpen) {
        LastLightExitConfirmation(
            kind = SessionExitKind.Host,
            onStay = { leaveConfirmationOpen = false },
            onExit = { leaveToHome(SessionEndReason.HostLeft) },
            exitInFlight = leaveInFlight,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            when {
                renderedHostError != null -> LastLightLobbyErrorState(
                    title = stringResource(Res.string.md_host_error_title),
                    detail = lastlightNetworkErrorMessage(renderedHostError),
                    showNetworkRecovery = localNetworkAccess.needsRecoveryGuidance,
                    onRetry = { if (hostLobbyActionsEnabled(leaveInFlight, retryInFlight)) hostAttempt++ },
                    onOpenNetworkSettings = onOpenNetworkSettings.takeIf {
                        localNetworkAccess.needsRecoveryGuidance
                    },
                    onBack = cancelToHome,
                    actionsEnabled = hostLobbyActionsEnabled(leaveInFlight, retryInFlight),
                    backInFlight = leaveInFlight,
                    modifier = Modifier.fillMaxSize(),
                )
                current == null -> LastLightLobbyLoadingState(
                    label = stringResource(Res.string.md_host_opening_room),
                    onLeave = cancelToHome,
                    leaveEnabled = hostLobbyActionsEnabled(leaveInFlight, retryInFlight),
                    leaveInFlight = leaveInFlight,
                    modifier = Modifier.fillMaxSize(),
                )
                frozenRoster == null -> LastLightHostLobbyContent(
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
                    LastLightMultiDeviceHostFlow(
                        players = players,
                        ownedSession = checkNotNull(ownedSession),
                        presentationState = presentationState,
                        onLeaveAfterEnd = leaveToHome,
                        onNewRoom = { reason ->
                            if (hostLobbyActionsEnabled(leaveInFlight, retryInFlight)) {
                                retryInFlight = true
                                val previous = checkNotNull(ownedSession)
                                val presentationKey = (previous.runtime.value as? LastLightHostRuntime)?.bridge?.protocol?.sessionId?.raw
                                // Closing removes the game child. The waiter belongs to
                                // this surviving lobby so completion can open the new room.
                                scope.launch {
                                    when (val closed = sessionOwner.prepareHostRetry(previous, reason)) {
                                        is Result.Success -> {
                                            presentationKey?.let(presentationState::removeState)
                                            startInFlight = false
                                            gameStarted = false
                                            hostAttempt++
                                        }
                                        is Result.Failure -> acquireError = closed.error
                                    }
                                    retryInFlight = false
                                }
                            }
                        },
                        operationInFlight = leaveInFlight || retryInFlight,
                        onRequestLeave = {
                            if (hostLobbyActionsEnabled(leaveInFlight, retryInFlight)) leaveConfirmationOpen = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LastLightHostLobbyContent(
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
    val playerCount = connectedMembers.size + 1
    val canStart =
        playerCount in LAST_LIGHT_MIN_PLAYERS..LAST_LIGHT_MAX_PLAYERS &&
            pendingAdmissions.isEmpty()
    val startLabel = when {
        pendingAdmissions.isNotEmpty() ->
            stringResource(Res.string.md_host_start_pending)
        playerCount < LAST_LIGHT_MIN_PLAYERS ->
            stringResource(Res.string.md_host_start_need_more)
        playerCount > LAST_LIGHT_MAX_PLAYERS ->
            stringResource(
                Res.string.md_host_start_too_many,
                LAST_LIGHT_MAX_PLAYERS,
            )
        else -> stringResource(Res.string.md_host_start_with_format, playerCount)
    }
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        StickyActionLayout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .parlorSafeContentPadding(
                            horizontal = ParlorTheme.spacing.l,
                            top = ParlorTheme.spacing.l,
                            bottom = ParlorTheme.spacing.l,
                        ),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
                ) {
                    ContextRibbon(
                        label = stringResource(Res.string.md_host_context_local),
                        detail = stringResource(Res.string.md_host_context_authority),
                        tone = ParlorContextTone.Host,
                    )
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
            },
            actions = {
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
            },
        )
    }
}

@Composable
private fun LastLightLobbyLoadingState(
    label: String,
    onLeave: () -> Unit,
    leaveEnabled: Boolean,
    leaveInFlight: Boolean,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .parlorSafeContentPadding(ParlorTheme.spacing.xl),
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
private fun LastLightLobbyErrorState(
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
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.xl),
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
                label = stringResource(Res.string.md_back),
                contentDescription = stringResource(Res.string.md_back_description),
                onClick = onBack,
                enabled = actionsEnabled,
                loading = backInFlight,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

private fun hostLobbyActionsEnabled(leaveInFlight: Boolean, retryInFlight: Boolean): Boolean =
    !leaveInFlight && !retryInFlight
