package com.parlor.app.shell.game.whodunit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.parlor.app.resources.Res
import com.parlor.app.resources.error_back
import com.parlor.app.resources.error_back_description
import com.parlor.app.resources.host_cancel
import com.parlor.app.resources.host_error_title
import com.parlor.app.resources.host_start_description
import com.parlor.app.resources.host_start_blocked
import com.parlor.app.resources.host_start_with_players_format
import com.parlor.app.resources.host_cancel_description
import com.parlor.app.resources.host_approve
import com.parlor.app.resources.host_approve_description
import com.parlor.app.resources.host_decline
import com.parlor.app.resources.host_decline_description
import com.parlor.app.resources.host_join_request_format
import com.parlor.app.resources.host_members_empty
import com.parlor.app.resources.host_members_eyebrow
import com.parlor.app.resources.host_pending_eyebrow
import com.parlor.app.resources.host_room_code_eyebrow
import com.parlor.app.resources.host_starting
import com.parlor.app.resources.host_title
import com.parlor.app.resources.network_open_settings
import com.parlor.app.resources.network_open_settings_description
import com.parlor.app.resources.network_recovery_help
import com.parlor.app.resources.network_retry
import com.parlor.app.resources.network_retry_description
import com.parlor.app.resources.host_hosting_as_format
import com.parlor.app.resources.host_start_need_more_format
import com.parlor.app.resources.host_start_pending
import com.parlor.app.resources.host_start_too_many_format
import com.parlor.app.shell.dataErrorMessage
import com.parlor.app.shell.netErrorMessage
import com.parlor.content.repository.CaseRepository
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.SessionExitAffordance
import com.parlor.designsystem.components.SessionExitBackAction
import com.parlor.designsystem.components.SessionExitConfirmation
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.designsystem.components.StickyActionBar
import com.parlor.designsystem.components.sessionExitBackAction
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.rules.WhodunitRules
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.ui.flow.WhodunitMultiplayerHostFlow
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomMember
import com.parlor.networking.security.SecureIds
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.needsRecoveryGuidance
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.session.multidevice.MultiplayerOpenMode
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

private const val LOADING_FLAME_SIZE_DP: Float = 72f

/**
 * End-to-end host flow: open a room, show the lobby, and once the host taps
 * **Start**, hand off to [WhodunitMultiplayerHostFlow] which announces the
 * session and renders the game. The process-scoped owner retains and closes
 * the [LocalRoom]; this composable is only a reattachable UI client.
 */
@Suppress("LongMethod") // Exhaustive typed owner/content-state rendering.
@Composable
fun WhodunitHostSessionFlow(
    transport: RoomTransport,
    caseId: String,
    modeId: ModeId,
    hostName: String,
    onBackToLibrary: () -> Unit,
    onOpenNetworkSettings: (() -> Unit)? = null,
    backRequestId: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val repository: CaseRepository = koinInject()
    val payloadValidator: PayloadValidator<WhodunitCase> = koinInject(qualifier = named("whodunit"))
    val sessionOwner: ProcessMultiplayerSessionOwner = koinInject()
    val scope = rememberCoroutineScope()

    val route = remember(caseId, modeId, hostName) {
        MultiplayerSessionRoute.host(
            gameId = WhodunitIds.GameId,
            displayName = hostName,
            contentId = caseId,
            modeId = modeId.raw,
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
    var startBlocked by remember { mutableStateOf(false) }
    var leaveInFlight by remember(route) { mutableStateOf(false) }
    var leaveConfirmationOpen by remember(route) { mutableStateOf(false) }

    val leaveToLibrary: (SessionEndReason) -> Unit = { reason ->
        if (!leaveInFlight) {
            leaveInFlight = true
            scope.launch {
                when (val left = sessionOwner.leaveRoute(route, reason)) {
                    is Result.Success -> onBackToLibrary()
                    is Result.Failure -> {
                        acquireError = left.error
                        leaveInFlight = false
                        leaveConfirmationOpen = false
                    }
                }
            }
        }
    }
    val cancelToLibrary: () -> Unit = { leaveToLibrary(SessionEndReason.Cancelled) }

    val caseResult by produceState<Result<ValidatedCase<WhodunitCase>, DataError>?>(
        initialValue = null,
        key1 = caseId,
    ) {
        value = repository.loadCase(CaseId(caseId), payloadValidator)
    }

    val case = (caseResult as? Result.Success)?.data
    val caseError = (caseResult as? Result.Failure)?.error
    val supportedPlayerCounts = case?.let {
        WhodunitRules.supportedPlayerCountsForCase(
            modeId = modeId,
            casePlayerCounts = it.envelope.supportedPlayerCounts.toIntRange(),
            availableCharacters = it.payload.characters.size,
        )
    }

    LaunchedEffect(transport, route, hostAttempt, supportedPlayerCounts) {
        val capacity = supportedPlayerCounts ?: return@LaunchedEffect
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
                        maxRemotePlayers = capacity.last - 1,
                        gameProtocol = HostedGameProtocol(
                            gameId = WhodunitIds.GameId,
                            gameVersion = WhodunitHostRoomBridge.GAME_VERSION,
                        ),
                    ),
                )
            }
        ) {
            is Result.Success -> startBlocked = false
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

    val localNetworkAccess by transport.localNetworkAccess.collectAsState()
    val renderedHostError = ownerError ?: acquireError
    val current = ownedSession?.room
    var gameStarted by remember(route) { mutableStateOf(false) }
    LaunchedEffect(frozenRoster) {
        if (frozenRoster != null) gameStarted = true
    }
    val gameIsActive = gameStarted || frozenRoster != null
    LaunchedEffect(backRequestId) {
        if (backRequestId > 0L && !leaveInFlight) {
            when (sessionExitBackAction(SessionExitKind.Host, gameIsActive)) {
                SessionExitBackAction.Confirm -> leaveConfirmationOpen = true
                SessionExitBackAction.ExitImmediately -> cancelToLibrary()
            }
        }
    }
    if (leaveConfirmationOpen) {
        SessionExitConfirmation(
            kind = SessionExitKind.Host,
            onStay = { leaveConfirmationOpen = false },
            onExit = { leaveToLibrary(SessionEndReason.HostLeft) },
            exitInFlight = leaveInFlight,
            destructive = true,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            when {
                renderedHostError != null -> HostErrorState(
                    error = netErrorMessage(renderedHostError),
                    onRetry = { hostAttempt++ },
                    onOpenNetworkSettings = onOpenNetworkSettings.takeIf {
                        localNetworkAccess.needsRecoveryGuidance
                    },
                    showNetworkRecovery = localNetworkAccess.needsRecoveryGuidance,
                    onBack = cancelToLibrary,
                    actionsEnabled = !leaveInFlight,
                    backInFlight = leaveInFlight,
                    modifier = Modifier.fillMaxSize(),
                )
                caseError != null -> HostErrorState(
                    dataErrorMessage(caseError),
                    onBack = cancelToLibrary,
                    actionsEnabled = !leaveInFlight,
                    backInFlight = leaveInFlight,
                    modifier = Modifier.fillMaxSize(),
                )
                case != null && supportedPlayerCounts == null -> HostErrorState(
                    dataErrorMessage(DataError.CorruptedData),
                    onBack = cancelToLibrary,
                    actionsEnabled = !leaveInFlight,
                    backInFlight = leaveInFlight,
                    modifier = Modifier.fillMaxSize(),
                )
                current == null || case == null -> HostLoadingState(
                    onLeave = cancelToLibrary,
                    leaveEnabled = !leaveInFlight,
                    leaveInFlight = leaveInFlight,
                    modifier = Modifier.fillMaxSize(),
                )
                frozenRoster == null -> HostLobbyContent(
                    room = current,
                    hostName = hostName,
                    supportedPlayerCounts = checkNotNull(supportedPlayerCounts),
                    modifier = Modifier.fillMaxSize(),
                    startBlocked = startBlocked,
                    onStart = {
                        scope.launch {
                            val session = ownedSession
                            when (val frozen = session.freezeAdmissions()) {
                                is Result.Success -> startBlocked = false
                                is Result.Failure -> {
                                    startBlocked = frozen.error == NetError.CommandInFlight
                                }
                            }
                        }
                    },
                    onLeave = cancelToLibrary,
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
                    WhodunitMultiplayerHostFlow(
                        case = case,
                        modeId = modeId,
                        players = players,
                        ownedSession = checkNotNull(ownedSession),
                        sessionOwner = sessionOwner,
                        onBackToLibrary = onBackToLibrary,
                        onRetryStart = {
                            startBlocked = false
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

@Suppress("LongMethod") // Declarative lobby layout covers every admission state and action.
@Composable
private fun HostLobbyContent(
    room: LocalRoom,
    hostName: String,
    supportedPlayerCounts: IntRange,
    startBlocked: Boolean,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info by room.info.collectAsState()
    val members by room.members.collectAsState()
    val pendingAdmissions by room.pendingAdmissions.collectAsState()
    val connectedMembers = members.filter(RoomMember::connected)
    val scope = rememberCoroutineScope()

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
            EyebrowLabel(text = stringResource(Res.string.host_title))

            // Hero room-code card — the screen's signature moment. The
            // indigo-tinted hero surface + dramatic elevation make the code
            // unmistakable when the host turns the phone to a friend.
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.dramatic,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.l,
                hero = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    EyebrowLabel(
                        text = stringResource(Res.string.host_room_code_eyebrow),
                        accent = false,
                    )
                    Text(
                        text = info.code,
                        style = ParlorTheme.typography.displayHero,
                        color = ParlorTheme.colors.accentEmber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(Res.string.host_hosting_as_format).replace("%1\$s", hostName),
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            EyebrowLabel(text = stringResource(Res.string.host_members_eyebrow), accent = false)
            if (connectedMembers.isEmpty()) {
                Text(
                    text = stringResource(Res.string.host_members_empty),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                )
            } else {
                connectedMembers.forEach { member ->
                    Text(
                        text = "· ${member.displayName}",
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textPrimary,
                    )
                }
            }

            if (pendingAdmissions.isNotEmpty()) {
                EyebrowLabel(
                    text = stringResource(Res.string.host_pending_eyebrow),
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
                                    Res.string.host_join_request_format,
                                    admission.displayName,
                                ),
                                style = ParlorTheme.typography.bodyLarge,
                                color = ParlorTheme.colors.textPrimary,
                            )
                            ParlorButton(
                                label = stringResource(Res.string.host_approve),
                                contentDescription = stringResource(
                                    Res.string.host_approve_description,
                                    admission.displayName,
                                ),
                                onClick = {
                                    scope.launch { room.approveAdmission(admission.playerId) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ParlorButton(
                                label = stringResource(Res.string.host_decline),
                                contentDescription = stringResource(
                                    Res.string.host_decline_description,
                                    admission.displayName,
                                ),
                                onClick = {
                                    scope.launch { room.rejectAdmission(admission.playerId) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                variant = ParlorButtonVariant.Secondary,
                            )
                        }
                    }
                }
            }

        }

        if (startBlocked) {
            Text(
                text = stringResource(Res.string.host_start_blocked),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.semanticDanger,
            )
        }

        val playerCount = connectedMembers.size + 1
        val canStart =
            playerCount in supportedPlayerCounts && pendingAdmissions.isEmpty()
        val startLabel = when {
            pendingAdmissions.isNotEmpty() ->
                stringResource(Res.string.host_start_pending)
            playerCount < supportedPlayerCounts.first ->
                stringResource(
                    Res.string.host_start_need_more_format,
                    supportedPlayerCounts.first.toString(),
                )
            playerCount > supportedPlayerCounts.last ->
                stringResource(
                    Res.string.host_start_too_many_format,
                    supportedPlayerCounts.last.toString(),
                )
            else ->
            stringResource(Res.string.host_start_with_players_format)
                .replace("%1\$s", playerCount.toString())
        }
        StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            ParlorButton(
                label = startLabel,
                contentDescription = stringResource(Res.string.host_start_description),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = canStart,
            )
            ParlorButton(
                label = stringResource(Res.string.host_cancel),
                contentDescription = stringResource(Res.string.host_cancel_description),
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Secondary,
            )
        }
        }
    }
}

@Composable
private fun HostLoadingState(
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
            CandleFlame(size = androidx.compose.ui.unit.Dp(LOADING_FLAME_SIZE_DP))
            Text(
                text = stringResource(Res.string.host_starting),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.host_cancel),
                contentDescription = stringResource(Res.string.host_cancel_description),
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
private fun HostErrorState(
    error: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onOpenNetworkSettings: (() -> Unit)? = null,
    showNetworkRecovery: Boolean = false,
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
                text = stringResource(Res.string.host_error_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
            if (showNetworkRecovery) {
                Text(
                    text = stringResource(Res.string.network_recovery_help),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                ParlorButton(
                    label = stringResource(Res.string.network_retry),
                    contentDescription = stringResource(Res.string.network_retry_description),
                    onClick = onRetry,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (onOpenNetworkSettings != null) {
                ParlorButton(
                    label = stringResource(Res.string.network_open_settings),
                    contentDescription = stringResource(Res.string.network_open_settings_description),
                    onClick = onOpenNetworkSettings,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ParlorButtonVariant.Secondary,
                )
            }
            ParlorButton(
                label = stringResource(Res.string.error_back),
                contentDescription = stringResource(Res.string.error_back_description),
                onClick = onBack,
                enabled = actionsEnabled,
                loading = backInFlight,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}
