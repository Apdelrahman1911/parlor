package com.parlor.games.lastlight.ui.flow.multidevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.OfflineBanner
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ReconnectingOverlay
import com.parlor.designsystem.components.SessionExitBackAction
import com.parlor.designsystem.components.SessionExitConfirmation
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.designsystem.components.SessionExitOverlay
import com.parlor.designsystem.components.coveredByReconnectingOverlay
import com.parlor.designsystem.components.sessionExitBackAction
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.lastlight.LastLightIds
import com.parlor.games.lastlight.domain.rules.LastLightSessionRules
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.md_peer_connecting_format
import com.parlor.games.lastlight.resources.md_peer_error_title
import com.parlor.games.lastlight.resources.md_peer_eyebrow
import com.parlor.games.lastlight.resources.md_peer_leave
import com.parlor.games.lastlight.resources.md_peer_leave_description
import com.parlor.games.lastlight.resources.md_peer_offline_banner
import com.parlor.games.lastlight.resources.md_peer_reconnecting
import com.parlor.games.lastlight.resources.md_peer_reconnecting_leave
import com.parlor.games.lastlight.resources.md_peer_reconnecting_leave_description
import com.parlor.games.lastlight.resources.md_peer_room_code_format
import com.parlor.games.lastlight.resources.md_peer_room_format
import com.parlor.games.lastlight.resources.md_peer_waiting_for_start
import com.parlor.games.lastlight.resources.md_network_open_settings
import com.parlor.games.lastlight.resources.md_network_open_settings_description
import com.parlor.games.lastlight.resources.md_network_recovery_help
import com.parlor.games.lastlight.resources.md_network_retry
import com.parlor.games.lastlight.resources.md_network_retry_description
import com.parlor.games.lastlight.resources.md_back
import com.parlor.games.lastlight.resources.md_back_description
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.needsRecoveryGuidance
import com.parlor.session.multidevice.awaitAuthoritativeSessionStart
import com.parlor.session.multidevice.asNetError
import com.parlor.session.multidevice.MultiplayerOpenMode
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import com.parlor.session.multidevice.RetainedMultiplayerCheckpoint
import com.parlor.session.multidevice.RetainedSessionOperation
import com.parlor.session.multidevice.RetainedValueResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Last Light peer entry owned by the retained multiplayer session. */
@Suppress("CyclomaticComplexMethod") // Exhaustive retained owner/handshake state rendering.
@Composable
fun LastLightPeerLobbyFlow(
    transport: RoomTransport,
    code: String,
    peerName: String,
    resumeExistingSession: Boolean = false,
    onBackToHome: () -> Unit,
    onOpenNetworkSettings: (() -> Unit)? = null,
    backRequestId: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val sessionOwner: ProcessMultiplayerSessionOwner = koinInject()
    val route = remember(code, peerName, resumeExistingSession) {
        MultiplayerSessionRoute.peer(
            gameId = LastLightIds.GameId,
            displayName = peerName,
            roomCode = code,
            resumeExistingSession = resumeExistingSession,
        )
    }
    val ownerState by sessionOwner.state.collectAsState()
    val ownedSession = (ownerState as? ProcessMultiplayerState.Active)
        ?.session
        ?.takeIf { it.route == route }
    val ownerError = when (val state = ownerState) {
        is ProcessMultiplayerState.Failed -> state.takeIf { it.route == route }?.error
        is ProcessMultiplayerState.Retryable -> state.takeIf { it.route == route }?.lastError
        else -> null
    }
    var acquireError by remember(route) { mutableStateOf<NetError?>(null) }
    var joinError by remember { mutableStateOf<NetError?>(null) }
    var joinAttempt by remember { mutableStateOf(0) }
    var finalLeaveInFlight by remember { mutableStateOf(false) }
    var retryInFlight by remember { mutableStateOf(false) }
    var leaveConfirmationOpen by remember(route) { mutableStateOf(false) }
    val flowScope = rememberCoroutineScope()
    val presentationState = rememberSaveableStateHolder()

    var hostLost by remember { mutableStateOf(false) }
    var selfOffline by remember { mutableStateOf(false) }

    val checkpoint by produceState<RetainedMultiplayerCheckpoint?>(
        initialValue = ownedSession?.checkpoint?.value,
        key1 = ownedSession,
    ) {
        val session = ownedSession
        if (session == null) {
            value = null
        } else {
            session.checkpoint.collect { value = it }
        }
    }
    val startCheckpoint = checkpoint as? LastLightStartCheckpoint
    val startCheckpointState by produceState<LastLightStartCheckpointState>(
        initialValue = startCheckpoint?.state?.value ?: LastLightStartCheckpointState.Waiting,
        key1 = startCheckpoint,
    ) {
        val retained = startCheckpoint
        if (retained == null) {
            value = LastLightStartCheckpointState.Waiting
        } else {
            retained.state.collect { value = it }
        }
    }
    val sessionStart = (startCheckpointState as? LastLightStartCheckpointState.Started)?.start

    LaunchedEffect(transport, route, joinAttempt) {
        acquireError = null
        joinError = null
        val result = sessionOwner.acquire(route) { mode ->
            when (mode) {
                MultiplayerOpenMode.Resume -> transport.resumeLastSession()
                MultiplayerOpenMode.Join -> transport.join(code, peerName)
                MultiplayerOpenMode.Host -> error("Peer route requested host acquisition")
            }
        }
        when (result) {
            is Result.Success -> Unit
            is Result.Failure -> acquireError = result.error
        }
    }

    LaunchedEffect(ownedSession) {
        val session = ownedSession ?: return@LaunchedEffect
        when (
            val installed = session.getOrCreateCheckpoint(LAST_LIGHT_START_CHECKPOINT_KIND) {
                LastLightStartCheckpoint()
            }
        ) {
            is RetainedValueResult.KindConflict -> joinError = NetError.IncompatibleProtocol
            is RetainedValueResult.CreationFailed -> joinError = installed.error
            is RetainedValueResult.Ready -> {
                val retained = installed.value as LastLightStartCheckpoint
                retained.start(
                    scope = session.scope,
                    onUnexpectedFailure = {
                        LastLightStartCheckpointState.Failed(
                            NetError.TransportFailure("session start failed"),
                        )
                    },
                ) {
                    val outcome = runLastLightStartHandshake(session)
                    if (outcome is LastLightStartCheckpointState.Failed) {
                        sessionOwner.preparePeerRetry(session, outcome.error)
                    }
                    outcome
                }
            }
        }
    }

    val finalBackToHome: () -> Unit = {
        if (!finalLeaveInFlight) {
            finalLeaveInFlight = true
            val presentationKey = (ownedSession?.runtime?.value as? LastLightPeerRuntime)?.bridge?.protocol?.sessionId?.raw
            flowScope.launch {
                val discarded = try {
                    sessionOwner.leaveRoute(
                        route,
                        com.parlor.networking.protocol.SessionEndReason.Cancelled,
                    )
                } catch (cancelled: CancellationException) {
                    finalLeaveInFlight = false
                    throw cancelled
                } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                    Result.Failure(NetError.TransportFailure("final leave failed"))
                }
                when (discarded) {
                    is Result.Success -> {
                        presentationKey?.let(presentationState::removeState)
                        onBackToHome()
                    }
                    is Result.Failure -> {
                        joinError = discarded.error
                        finalLeaveInFlight = false
                        leaveConfirmationOpen = false
                    }
                }
            }
        }
    }

    val current = ownedSession?.room
    val start = sessionStart
    var gameStarted by remember(route) { mutableStateOf(false) }
    LaunchedEffect(start) {
        if (start != null) gameStarted = true
    }
    val gameIsActive = gameStarted || start != null
    LaunchedEffect(backRequestId) {
        if (backRequestId > 0L && !finalLeaveInFlight) {
            when (sessionExitBackAction(SessionExitKind.Peer, gameIsActive)) {
                SessionExitBackAction.Confirm -> leaveConfirmationOpen = true
                SessionExitBackAction.ExitImmediately -> finalBackToHome()
            }
        }
    }
    val localNetworkAccess by transport.localNetworkAccess.collectAsState()
    val checkpointFailure = startCheckpointState as? LastLightStartCheckpointState.Failed
    val renderedPeerError = joinError ?: checkpointFailure?.error ?: ownerError ?: acquireError
    val retryConnection: () -> Unit = retry@{
        if (finalLeaveInFlight || retryInFlight) return@retry
        val failedCheckpoint = checkpointFailure
        val session = ownedSession
        if (failedCheckpoint == null || session == null) {
            joinAttempt++
            return@retry
        }
        retryInFlight = true
        flowScope.launch {
            when (val prepared = sessionOwner.preparePeerRetry(session, failedCheckpoint.error)) {
                is Result.Success -> joinAttempt++
                is Result.Failure -> acquireError = prepared.error
            }
            retryInFlight = false
        }
    }

    if (leaveConfirmationOpen) {
        SessionExitConfirmation(
            kind = SessionExitKind.Peer,
            onStay = { leaveConfirmationOpen = false },
            onExit = finalBackToHome,
            exitInFlight = finalLeaveInFlight,
            destructive = true,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            SessionExitOverlay(
                visible = gameIsActive && !hostLost,
                onClick = { leaveConfirmationOpen = true },
                modifier = Modifier
                    .fillMaxSize()
                    .coveredByReconnectingOverlay(hostLost),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (selfOffline) {
                        OfflineBanner(label = stringResource(Res.string.md_peer_offline_banner))
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            renderedPeerError != null -> LastLightPeerErrorState(
                                title = stringResource(Res.string.md_peer_error_title),
                                detail = lastlightNetworkErrorMessage(renderedPeerError),
                                showNetworkRecovery = localNetworkAccess.needsRecoveryGuidance,
                                onRetry = retryConnection.takeIf { joinError == null },
                                onOpenNetworkSettings = onOpenNetworkSettings.takeIf {
                                    localNetworkAccess.needsRecoveryGuidance
                                },
                                onBack = finalBackToHome,
                                actionsEnabled = !finalLeaveInFlight && !retryInFlight,
                                backInFlight = finalLeaveInFlight,
                                retryInFlight = retryInFlight,
                                modifier = Modifier.fillMaxSize(),
                            )
                            current == null -> LastLightPeerConnectingState(
                                code = code,
                                resuming = resumeExistingSession,
                                onLeave = finalBackToHome,
                                leaveEnabled = !finalLeaveInFlight,
                                leaveInFlight = finalLeaveInFlight,
                                modifier = Modifier.fillMaxSize(),
                            )
                            start == null -> LastLightPeerWaitingForStart(
                                room = current,
                                peerName = peerName,
                                onLeave = finalBackToHome,
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> LastLightMultiDevicePeerFlow(
                                players = start.players,
                                selfPlayerId = current.selfPlayerId,
                                protocol = start.protocol,
                                acceptedStartOffer = start.offer,
                                ownedSession = checkNotNull(ownedSession),
                                presentationState = presentationState,
                                onBackToHome = finalBackToHome,
                                onRequestLeave = { leaveConfirmationOpen = true },
                                modifier = Modifier.fillMaxSize(),
                                onHostLostChanged = { hostLost = it },
                                onSelfOfflineChanged = { selfOffline = it },
                            )
                        }
                    }
                }
            }
            if (hostLost) {
                ReconnectingOverlay(
                    title = stringResource(Res.string.md_peer_reconnecting),
                    leaveLabel = stringResource(Res.string.md_peer_reconnecting_leave),
                    leaveContentDescription = stringResource(
                        Res.string.md_peer_reconnecting_leave_description,
                    ),
                    onLeave = { leaveConfirmationOpen = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LastLightPeerConnectingState(
    code: String,
    resuming: Boolean,
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
                text = if (resuming) {
                    stringResource(Res.string.md_peer_reconnecting)
                } else {
                    stringResource(Res.string.md_peer_connecting_format, code)
                },
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.md_peer_leave),
                contentDescription = stringResource(Res.string.md_peer_leave_description),
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
private fun LastLightPeerWaitingForStart(
    room: LocalRoom,
    peerName: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info by room.info.collectAsState()
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            EyebrowLabel(text = stringResource(Res.string.md_peer_eyebrow))
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.l,
                hero = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    Text(
                        text = stringResource(
                            Res.string.md_peer_room_format,
                            info.hostDisplayName,
                            peerName,
                        ),
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.md_peer_waiting_for_start),
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                    )
                    Text(
                        text = stringResource(Res.string.md_peer_room_code_format, info.code),
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(ParlorTheme.spacing.l))
            ParlorButton(
                label = stringResource(Res.string.md_peer_leave),
                contentDescription = stringResource(Res.string.md_peer_leave_description),
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun LastLightPeerErrorState(
    title: String,
    detail: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showNetworkRecovery: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onOpenNetworkSettings: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
    backInFlight: Boolean = false,
    retryInFlight: Boolean = false,
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
                    loading = retryInFlight,
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

private const val LAST_LIGHT_START_CHECKPOINT_KIND = "last-light/start/v1"

private class LastLightStartCheckpoint : RetainedMultiplayerCheckpoint {
    override val checkpointKind: String = LAST_LIGHT_START_CHECKPOINT_KIND
    private val operation = RetainedSessionOperation<LastLightStartCheckpointState>(
        LastLightStartCheckpointState.Waiting,
    )
    val state: StateFlow<LastLightStartCheckpointState> = operation.state

    suspend fun start(
        scope: CoroutineScope,
        onUnexpectedFailure: (Exception) -> LastLightStartCheckpointState,
        operation: suspend () -> LastLightStartCheckpointState,
    ) = this.operation.start(scope, onUnexpectedFailure, operation)
}

private sealed interface LastLightStartCheckpointState {
    data object Waiting : LastLightStartCheckpointState
    data class Started(val start: SessionStartingFromHost) : LastLightStartCheckpointState
    data class Failed(val error: NetError) : LastLightStartCheckpointState
}

private suspend fun runLastLightStartHandshake(
    session: ProcessMultiplayerSession,
): LastLightStartCheckpointState = when (
    val started = awaitAuthoritativeSessionStart(
        room = session.room,
        expectedGameId = LastLightIds.GameId,
        expectedGameVersion = LastLightHostRoomBridge.GAME_VERSION,
    ) { message, _ -> acceptsLastLightStart(message, session.room) }
) {
    is Result.Success -> {
        val offer = started.data.offer
        LastLightStartCheckpointState.Started(
            SessionStartingFromHost(
                players = offer.players,
                protocol = started.data.protocol,
                offer = offer,
            ),
        )
    }
    is Result.Failure -> LastLightStartCheckpointState.Failed(started.error.asNetError())
}

private data class SessionStartingFromHost(
    val players: List<Player>,
    val protocol: SessionProtocol,
    val offer: HostMessage.SessionStarting,
)

internal fun acceptsLastLightStart(message: HostMessage.SessionStarting, room: LocalRoom): Boolean {
    val ids = message.players.map(Player::id)
    return message.caseId == LastLightIds.CaseId.raw &&
        message.modeId == LastLightIds.StandardModeId.raw &&
        LastLightSessionRules.isValidRoster(message.players) &&
        room.selfPlayerId in ids && room.info.value.hostPlayerId in ids &&
        message.players.firstOrNull()?.id == room.info.value.hostPlayerId
}
