package com.parlor.app.shell.multiplayer

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
import com.parlor.app.shell.dataErrorMessage
import com.parlor.app.shell.netErrorMessage
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerSessionAttempt
import com.parlor.networking.room.PeerSessionRetryPolicy
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.needsRecoveryGuidance
import com.parlor.session.multidevice.awaitAuthoritativeSessionStart
import com.parlor.session.multidevice.asNetError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/**
 * End-to-end peer flow: join the room, prepare and acknowledge the host's
 * start offer, wait for its authoritative commit, then hand off to
 * [WhodunitMultiplayerPeerFlow].
 * Owns the [LocalRoom] for the lifetime of the session.
 */
@Composable
fun PeerSessionFlow(
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
    var room by remember { mutableStateOf<LocalRoom?>(null) }
    // Keep the typed error so the rendering site can localise it. Stringifying
    // here would lose the type and ship "NetError$TransportFailure(reason=...)"
    // straight to the user.
    var joinError by remember { mutableStateOf<NetError?>(null) }
    var sessionStart by remember { mutableStateOf<SessionStartingFromHost?>(null) }
    var casePreparationError by remember { mutableStateOf<DataError?>(null) }
    var joinAttempt by remember { mutableStateOf(0) }
    var retryPolicy by remember(transport, code, peerName, resumeExistingSession) {
        mutableStateOf(PeerSessionRetryPolicy.initial(resumeExistingSession))
    }
    var retainedRetryRoom by remember(transport, code, peerName) {
        mutableStateOf<LocalRoom?>(null)
    }
    var finalLeaveInFlight by remember { mutableStateOf(false) }
    val flowScope = rememberCoroutineScope()

    LaunchedEffect(transport, code, resumeExistingSession, joinAttempt) {
        joinError = null
        casePreparationError = null
        sessionStart = null
        val result = when (retryPolicy.nextAttempt) {
            PeerSessionAttempt.Resume -> transport.resumeLastSession()
            PeerSessionAttempt.Join -> transport.join(code, peerName)
        }
        when (result) {
            is Result.Success -> {
                retainedRetryRoom = null
                retryPolicy = retryPolicy.afterRoomAcquired()
                room = result.data
            }
            is Result.Failure -> joinError = result.error
        }
    }

    LaunchedEffect(room) {
        val active = room ?: return@LaunchedEffect
        var preparedCase: ValidatedCase<WhodunitCase>? = null
        when (
            val start = awaitAuthoritativeSessionStart(
                room = active,
                expectedGameId = WhodunitIds.GameId,
                expectedGameVersion = WhodunitHostRoomBridge.GAME_VERSION,
            ) { msg, _ ->
                val ids = msg.players.map(Player::id)
                val modeId = ModeId(msg.modeId)
                val structurallyValid =
                    WhodunitRules.supportedPlayerCounts(modeId) != null &&
                        active.selfPlayerId in ids &&
                        active.info.value.hostPlayerId in ids &&
                        ids.distinct().size == ids.size
                if (!structurallyValid) return@awaitAuthoritativeSessionStart false

                when (
                    val loaded = repository.loadCase(
                        CaseId(msg.caseId),
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
                            msg.modeId in it.envelope.supportedModes &&
                                supportedCounts != null &&
                                msg.players.size in supportedCounts &&
                                msg.matches(it.envelope)
                        }?.also { preparedCase = it } != null
                    }
                    is Result.Failure -> {
                        casePreparationError = loaded.error
                        false
                    }
                }
            }
        ) {
            is Result.Success -> {
                val offer = start.data.offer
                val case = preparedCase
                if (case == null) {
                    joinError = NetError.IncompatibleProtocol
                    when (val closed = active.closeForRetry()) {
                        is Result.Success -> Unit
                        is Result.Failure -> joinError = closed.error
                    }
                    retainedRetryRoom = active
                    retryPolicy = retryPolicy.afterPostAdmissionStartFailure()
                    if (room === active) room = null
                } else {
                    sessionStart = SessionStartingFromHost(
                        case = case,
                        modeId = offer.modeId,
                        players = offer.players,
                        seed = offer.sessionNonce,
                        protocol = start.data.protocol,
                    )
                }
            }
            is Result.Failure -> {
                if (casePreparationError == null) joinError = start.error.asNetError()
                when (val closed = active.closeForRetry()) {
                    is Result.Success -> Unit
                    is Result.Failure -> joinError = closed.error
                }
                retainedRetryRoom = active
                retryPolicy = retryPolicy.afterPostAdmissionStartFailure()
                if (room === active) room = null
            }
        }
    }

    // Keep room teardown alive through composition cancellation. Launching
    // from `onDispose` on a rememberCoroutineScope is racy because that scope
    // is cancelled as the composition is removed, which can strand discovery
    // and sockets. The owner effect mirrors the host/Mafia flows and performs
    // the terminal leave in a NonCancellable section.
    LaunchedEffect(room) {
        val active = room ?: return@LaunchedEffect
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                active.leave()
            }
        }
    }

    val finalBackToLibrary: () -> Unit = {
        val active = room
        val retained = retainedRetryRoom
        val leaveTarget = retained ?: active
        if (leaveTarget == null) {
            onBackToLibrary()
        } else if (!finalLeaveInFlight) {
            finalLeaveInFlight = true
            flowScope.launch {
                val discarded = try {
                    if (retained != null) {
                        retained.discardRejoinCapability()
                    } else {
                        leaveTarget.finalLeave()
                    }
                } catch (cancelled: CancellationException) {
                    finalLeaveInFlight = false
                    throw cancelled
                } catch (_: Exception) {
                    Result.Failure(NetError.TransportFailure("final leave failed"))
                }
                when (discarded) {
                    is Result.Success -> {
                        if (retainedRetryRoom === retained) retainedRetryRoom = null
                        if (room === active) room = null
                        onBackToLibrary()
                    }
                    is Result.Failure -> {
                        casePreparationError = null
                        joinError = discarded.error
                        finalLeaveInFlight = false
                    }
                }
            }
        }
    }

    val current = room
    val start = sessionStart

    // The game-owned peer bridge exposes durable connection state through
    // these callbacks. Keeping presentation state at the shell root makes the
    // reconnect overlay and offline banner survive inner-screen transitions.
    var hostLost by remember { mutableStateOf(false) }
    var selfOffline by remember { mutableStateOf(false) }
    val localNetworkAccess by transport.localNetworkAccess.collectAsState()
    val renderedCaseError = casePreparationError
    val renderedJoinError = joinError

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selfOffline) {
                OfflineBanner(label = stringResource(Res.string.party_offline_banner))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    renderedCaseError != null -> PeerErrorState(
                        error = dataErrorMessage(renderedCaseError),
                        onRetry = { if (!finalLeaveInFlight) joinAttempt++ },
                        onBack = finalBackToLibrary,
                        actionsEnabled = !finalLeaveInFlight,
                        backInFlight = finalLeaveInFlight,
                        modifier = Modifier.fillMaxSize(),
                    )
                    renderedJoinError != null -> PeerErrorState(
                        error = netErrorMessage(renderedJoinError),
                        onRetry = { if (!finalLeaveInFlight) joinAttempt++ },
                        onOpenNetworkSettings = onOpenNetworkSettings.takeIf {
                            localNetworkAccess.needsRecoveryGuidance
                        },
                        showNetworkRecovery = localNetworkAccess.needsRecoveryGuidance,
                        onBack = finalBackToLibrary,
                        actionsEnabled = !finalLeaveInFlight,
                        backInFlight = finalLeaveInFlight,
                        modifier = Modifier.fillMaxSize(),
                    )
                    current == null -> PeerConnectingState(code, modifier = Modifier.fillMaxSize())
                    start == null -> PeerWaitingForHostStart(
                        current,
                        peerName,
                        modifier = Modifier.fillMaxSize(),
                        onLeave = finalBackToLibrary,
                    )
                    else -> PeerSessionWithCase(
                        room = current,
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
        room = room,
        protocol = start.protocol,
        onBackToLibrary = onBackToLibrary,
        modifier = modifier,
        onHostLostChanged = onHostLostChanged,
        onSelfOfflineChanged = onSelfOfflineChanged,
    )
}

@Composable
private fun PeerConnectingState(code: String, modifier: Modifier = Modifier) {
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
                text = stringResource(Res.string.peer_connecting_format).replace("%1\$s", code),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
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

/** Local snapshot of a SessionStarting message — keeps the Compose state simple. */
private data class SessionStartingFromHost(
    val case: ValidatedCase<WhodunitCase>,
    val modeId: String,
    val players: List<Player>,
    val seed: Long,
    val protocol: SessionProtocol,
)
