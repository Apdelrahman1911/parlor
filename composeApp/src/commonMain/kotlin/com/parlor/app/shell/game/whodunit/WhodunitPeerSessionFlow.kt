package com.parlor.app.shell.game.whodunit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.parlor.app.resources.party_offline_banner
import com.parlor.app.resources.party_reconnecting_overlay_leave
import com.parlor.app.resources.party_reconnecting_overlay_leave_description
import com.parlor.app.resources.party_reconnecting_overlay_title
import com.parlor.designsystem.components.OfflineBanner
import com.parlor.designsystem.components.ReconnectingOverlay
import com.parlor.app.resources.error_back
import com.parlor.app.resources.error_back_description
import com.parlor.app.resources.peer_connecting_format
import com.parlor.app.resources.peer_error_title
import com.parlor.app.resources.peer_eyebrow
import com.parlor.app.resources.peer_leave
import com.parlor.app.resources.peer_leave_description
import com.parlor.app.resources.peer_room_code_label_format
import com.parlor.app.resources.peer_room_header_format
import com.parlor.app.resources.peer_waiting_for_start
import com.parlor.app.resources.network_open_settings
import com.parlor.app.resources.network_open_settings_description
import com.parlor.app.resources.network_recovery_help
import com.parlor.app.resources.network_retry
import com.parlor.app.resources.network_retry_description
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
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.matches
import com.parlor.games.whodunit.domain.rules.WhodunitRules
import com.parlor.games.whodunit.ui.flow.WhodunitMultiplayerPeerFlow
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
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
import org.koin.core.qualifier.named

private const val LOADING_FLAME_SIZE_DP: Float = 72f

/**
 * End-to-end peer flow: join the room, prepare and acknowledge the host's
 * start offer, wait for its authoritative commit, then hand off to
 * [WhodunitMultiplayerPeerFlow]. The process-scoped session owner retains the
 * [LocalRoom] and the one-shot start transaction across UI recreation.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // Exhaustive retained owner/handshake state rendering.
@Composable
fun WhodunitPeerSessionFlow(
    transport: RoomTransport,
    code: String,
    peerName: String,
    resumeExistingSession: Boolean = false,
    onBackToLibrary: () -> Unit,
    onOpenNetworkSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository: CaseRepository = koinInject()
    val payloadValidator: PayloadValidator<WhodunitCase> =
        koinInject(qualifier = named("whodunit"))
    val sessionOwner: ProcessMultiplayerSessionOwner = koinInject()
    val route = remember(code, peerName, resumeExistingSession) {
        MultiplayerSessionRoute.peer(
            gameId = WhodunitIds.GameId,
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
    // Keep the typed error so the rendering site can localise it. Stringifying
    // here would lose the type and ship "NetError$TransportFailure(reason=...)"
    // straight to the user.
    var joinError by remember { mutableStateOf<NetError?>(null) }
    var joinAttempt by remember { mutableStateOf(0) }
    var finalLeaveInFlight by remember { mutableStateOf(false) }
    var retryInFlight by remember { mutableStateOf(false) }
    val flowScope = rememberCoroutineScope()

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
    val startCheckpoint = checkpoint as? WhodunitStartCheckpoint
    val startCheckpointState by produceState<WhodunitStartCheckpointState>(
        initialValue = startCheckpoint?.state?.value ?: WhodunitStartCheckpointState.Waiting,
        key1 = startCheckpoint,
    ) {
        val retained = startCheckpoint
        if (retained == null) {
            value = WhodunitStartCheckpointState.Waiting
        } else {
            retained.state.collect { value = it }
        }
    }
    val sessionStart = (startCheckpointState as? WhodunitStartCheckpointState.Started)?.start

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
            val installed = session.getOrCreateCheckpoint(WHODUNIT_START_CHECKPOINT_KIND) {
                WhodunitStartCheckpoint()
            }
        ) {
            is RetainedValueResult.KindConflict -> joinError = NetError.IncompatibleProtocol
            is RetainedValueResult.CreationFailed -> joinError = installed.error
            is RetainedValueResult.Ready -> {
                val retained = installed.value as WhodunitStartCheckpoint
                retained.start(
                    scope = session.scope,
                    onUnexpectedFailure = {
                        WhodunitStartCheckpointState.Failed(
                            NetError.TransportFailure("session start failed"),
                        )
                    },
                ) {
                    val outcome = runWhodunitStartHandshake(
                        session = session,
                        repository = repository,
                        payloadValidator = payloadValidator,
                    )
                    if (outcome is WhodunitStartCheckpointState.Failed) {
                        sessionOwner.preparePeerRetry(session, outcome.netError)
                    }
                    outcome
                }
            }
        }
    }

    val finalBackToLibrary: () -> Unit = {
        if (!finalLeaveInFlight) {
            finalLeaveInFlight = true
            flowScope.launch {
                val discarded = try {
                    sessionOwner.leaveRoute(route, com.parlor.networking.protocol.SessionEndReason.Cancelled)
                } catch (cancelled: CancellationException) {
                    finalLeaveInFlight = false
                    throw cancelled
                } catch (_: Exception) {
                    Result.Failure(NetError.TransportFailure("final leave failed"))
                }
                when (discarded) {
                    is Result.Success -> {
                        onBackToLibrary()
                    }
                    is Result.Failure -> {
                        joinError = discarded.error
                        finalLeaveInFlight = false
                    }
                }
            }
        }
    }

    val current = ownedSession?.room
    val start = sessionStart

    // The game-owned peer bridge exposes durable connection state through
    // these callbacks. Keeping presentation state at the shell root makes the
    // reconnect overlay and offline banner survive inner-screen transitions.
    var hostLost by remember { mutableStateOf(false) }
    var selfOffline by remember { mutableStateOf(false) }
    val localNetworkAccess by transport.localNetworkAccess.collectAsState()
    val checkpointFailure = startCheckpointState as? WhodunitStartCheckpointState.Failed
    val renderedCaseError = checkpointFailure?.dataError
    val renderedJoinError = joinError ?: checkpointFailure?.netError ?: ownerError ?: acquireError
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
            when (val prepared = sessionOwner.preparePeerRetry(session, failedCheckpoint.netError)) {
                is Result.Success -> joinAttempt++
                is Result.Failure -> acquireError = prepared.error
            }
            retryInFlight = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selfOffline) {
                OfflineBanner(label = stringResource(Res.string.party_offline_banner))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    renderedCaseError != null -> PeerErrorState(
                        error = dataErrorMessage(renderedCaseError),
                        onRetry = retryConnection.takeIf { joinError == null },
                        onBack = finalBackToLibrary,
                        actionsEnabled = !finalLeaveInFlight && !retryInFlight,
                        backInFlight = finalLeaveInFlight,
                        retryInFlight = retryInFlight,
                        modifier = Modifier.fillMaxSize(),
                    )
                    renderedJoinError != null -> PeerErrorState(
                        error = netErrorMessage(renderedJoinError),
                        onRetry = retryConnection.takeIf { joinError == null },
                        onOpenNetworkSettings = onOpenNetworkSettings.takeIf {
                            localNetworkAccess.needsRecoveryGuidance
                        },
                        showNetworkRecovery = localNetworkAccess.needsRecoveryGuidance,
                        onBack = finalBackToLibrary,
                        actionsEnabled = !finalLeaveInFlight && !retryInFlight,
                        backInFlight = finalLeaveInFlight,
                        retryInFlight = retryInFlight,
                        modifier = Modifier.fillMaxSize(),
                    )
                    current == null -> PeerConnectingState(
                        code = code,
                        resuming = resumeExistingSession,
                        onLeave = finalBackToLibrary,
                        leaveEnabled = !finalLeaveInFlight,
                        leaveInFlight = finalLeaveInFlight,
                        modifier = Modifier.fillMaxSize(),
                    )
                    start == null -> PeerWaitingForHostStart(
                        current,
                        peerName,
                        modifier = Modifier.fillMaxSize(),
                        onLeave = finalBackToLibrary,
                    )
                    else -> PeerSessionWithCase(
                        room = current,
                        ownedSession = checkNotNull(ownedSession),
                        start = start,
                        onBackToLibrary = finalBackToLibrary,
                        modifier = Modifier.fillMaxSize(),
                        onHostLostChanged = { hostLost = it },
                        onSelfOfflineChanged = { selfOffline = it },
                    )
                }
            }
        }
        if (hostLost) {
            ReconnectingOverlay(
                title = stringResource(Res.string.party_reconnecting_overlay_title),
                leaveLabel = stringResource(Res.string.party_reconnecting_overlay_leave),
                leaveContentDescription = stringResource(Res.string.party_reconnecting_overlay_leave_description),
                onLeave = finalBackToLibrary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PeerSessionWithCase(
    room: LocalRoom,
    ownedSession: ProcessMultiplayerSession,
    start: SessionStartingFromHost,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    onHostLostChanged: (Boolean) -> Unit = {},
    onSelfOfflineChanged: (Boolean) -> Unit = {},
) {
    val selfId = room.selfPlayerId
    WhodunitMultiplayerPeerFlow(
        case = start.case,
        modeId = ModeId(start.modeId),
        players = start.players,
        selfPlayerId = selfId,
        seed = start.seed,
        protocol = start.protocol,
        ownedSession = ownedSession,
        onBackToLibrary = onBackToLibrary,
        modifier = modifier,
        onHostLostChanged = onHostLostChanged,
        onSelfOfflineChanged = onSelfOfflineChanged,
    )
}

@Composable
private fun PeerConnectingState(
    code: String,
    resuming: Boolean,
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
                text = if (resuming) {
                    stringResource(Res.string.party_reconnecting_overlay_title)
                } else {
                    stringResource(Res.string.peer_connecting_format).replace("%1\$s", code)
                },
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            ParlorButton(
                label = stringResource(Res.string.peer_leave),
                contentDescription = stringResource(Res.string.peer_leave_description),
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
private fun PeerWaitingForHostStart(
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
                .padding(ParlorTheme.spacing.l)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            EyebrowLabel(text = stringResource(Res.string.peer_eyebrow))

            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.dramatic,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.l,
                hero = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    Text(
                        text = stringResource(Res.string.peer_room_header_format)
                            .replace("%1\$s", info.displayName)
                            .replace("%2\$s", peerName),
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.peer_waiting_for_start),
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                    )
                    Text(
                        text = stringResource(Res.string.peer_room_code_label_format)
                            .replace("%1\$s", info.code),
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textTertiary,
                    )
                }
            }

            Spacer(Modifier.height(ParlorTheme.spacing.l))

            ParlorButton(
                label = stringResource(Res.string.peer_leave),
                contentDescription = stringResource(Res.string.peer_leave_description),
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun PeerErrorState(
    error: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onOpenNetworkSettings: (() -> Unit)? = null,
    showNetworkRecovery: Boolean = false,
    actionsEnabled: Boolean = true,
    backInFlight: Boolean = false,
    retryInFlight: Boolean = false,
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
                text = stringResource(Res.string.peer_error_title),
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
                    loading = retryInFlight,
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

private const val WHODUNIT_START_CHECKPOINT_KIND = "whodunit/start/v1"

/** Process-owned start transaction; it survives loss of every UI collector. */
private class WhodunitStartCheckpoint : RetainedMultiplayerCheckpoint {
    override val checkpointKind: String = WHODUNIT_START_CHECKPOINT_KIND
    private val operation = RetainedSessionOperation<WhodunitStartCheckpointState>(
        WhodunitStartCheckpointState.Waiting,
    )
    val state: StateFlow<WhodunitStartCheckpointState> = operation.state

    suspend fun start(
        scope: CoroutineScope,
        onUnexpectedFailure: (Exception) -> WhodunitStartCheckpointState,
        operation: suspend () -> WhodunitStartCheckpointState,
    ) = this.operation.start(scope, onUnexpectedFailure, operation)
}

private sealed interface WhodunitStartCheckpointState {
    data object Waiting : WhodunitStartCheckpointState
    data class Started(val start: SessionStartingFromHost) : WhodunitStartCheckpointState
    data class Failed(
        val netError: NetError,
        val dataError: DataError? = null,
    ) : WhodunitStartCheckpointState
}

private suspend fun runWhodunitStartHandshake(
    session: ProcessMultiplayerSession,
    repository: CaseRepository,
    payloadValidator: PayloadValidator<WhodunitCase>,
): WhodunitStartCheckpointState {
    val room = session.room
    var preparedCase: ValidatedCase<WhodunitCase>? = null
    var preparationError: DataError? = null
    return when (
        val started = awaitAuthoritativeSessionStart(
            room = room,
            expectedGameId = WhodunitIds.GameId,
            expectedGameVersion = WhodunitHostRoomBridge.GAME_VERSION,
        ) { message, _ ->
            val ids = message.players.map(Player::id)
            val modeId = ModeId(message.modeId)
            val structurallyValid =
                WhodunitRules.supportedPlayerCounts(modeId) != null &&
                    room.selfPlayerId in ids &&
                    room.info.value.hostPlayerId in ids &&
                    ids.distinct().size == ids.size
            if (!structurallyValid) return@awaitAuthoritativeSessionStart false

            when (
                val loaded = repository.loadCase(
                    CaseId(message.caseId),
                    payloadValidator,
                )
            ) {
                is Result.Success -> {
                    val loadedCase = loaded.data
                    val supportedCounts = WhodunitRules.supportedPlayerCountsForCase(
                        modeId = modeId,
                        casePlayerCounts = loadedCase.envelope.supportedPlayerCounts.toIntRange(),
                        availableCharacters = loadedCase.payload.characters.size,
                    )
                    loadedCase.takeIf {
                        message.modeId in it.envelope.supportedModes &&
                            supportedCounts != null &&
                            message.players.size in supportedCounts &&
                            message.matches(it.envelope)
                    }?.also { preparedCase = it } != null
                }
                is Result.Failure -> {
                    preparationError = loaded.error
                    false
                }
            }
        }
    ) {
        is Result.Success -> {
            val case = preparedCase
            if (case == null) {
                WhodunitStartCheckpointState.Failed(
                    netError = preparationError?.let {
                        NetError.TransportFailure("local game preparation failed")
                    } ?: NetError.IncompatibleProtocol,
                    dataError = preparationError,
                )
            } else {
                val offer = started.data.offer
                WhodunitStartCheckpointState.Started(
                    SessionStartingFromHost(
                        case = case,
                        modeId = offer.modeId,
                        players = offer.players,
                        seed = offer.sessionNonce,
                        protocol = started.data.protocol,
                    ),
                )
            }
        }
        is Result.Failure -> WhodunitStartCheckpointState.Failed(
            netError = preparationError?.let {
                NetError.TransportFailure("local game preparation failed")
            } ?: started.error.asNetError(),
            dataError = preparationError,
        )
    }
}

/** Validated start data retained because the network frame is consumed exactly once. */
private data class SessionStartingFromHost(
    val case: ValidatedCase<WhodunitCase>,
    val modeId: String,
    val players: List<Player>,
    val seed: Long,
    val protocol: SessionProtocol,
)
