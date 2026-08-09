package com.parlor.games.mafia.ui.flow.multidevice

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
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CandleFlame
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.OfflineBanner
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ReconnectingOverlay
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.mafia.domain.rules.MafiaSessionRules
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.md_peer_connecting_format
import com.parlor.games.mafia.resources.md_peer_error_title
import com.parlor.games.mafia.resources.md_peer_error_detail
import com.parlor.games.mafia.resources.md_peer_eyebrow
import com.parlor.games.mafia.resources.md_peer_leave
import com.parlor.games.mafia.resources.md_peer_leave_description
import com.parlor.games.mafia.resources.md_peer_offline_banner
import com.parlor.games.mafia.resources.md_peer_reconnecting
import com.parlor.games.mafia.resources.md_peer_reconnecting_leave
import com.parlor.games.mafia.resources.md_peer_reconnecting_leave_description
import com.parlor.games.mafia.resources.md_peer_room_code_format
import com.parlor.games.mafia.resources.md_peer_room_format
import com.parlor.games.mafia.resources.md_peer_waiting_for_start
import com.parlor.games.mafia.resources.md_network_open_settings
import com.parlor.games.mafia.resources.md_network_open_settings_description
import com.parlor.games.mafia.resources.md_network_recovery_help
import com.parlor.games.mafia.resources.md_network_retry
import com.parlor.games.mafia.resources.md_network_retry_description
import com.parlor.games.mafia.resources.setup_back
import com.parlor.games.mafia.resources.setup_back_description
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

/**
 * Mafia-side peer lobby. Mirrors composeApp's shell `PeerSessionFlow` but
 * is Mafia-specific: no case loading (Mafia has no external content), and
 * once the host's acknowledged start transaction commits, it dispatches to
 * [MafiaMultiDevicePeerFlow] instead of the Whodunit peer flow.
 */
@Composable
fun MafiaPeerLobbyFlow(
    transport: RoomTransport,
    code: String,
    peerName: String,
    resumeExistingSession: Boolean = false,
    onBackToHome: () -> Unit,
    onOpenNetworkSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var room by remember { mutableStateOf<LocalRoom?>(null) }
    var joinError by remember { mutableStateOf<NetError?>(null) }
    var sessionStart by remember { mutableStateOf<SessionStartingFromHost?>(null) }
    var joinAttempt by remember { mutableStateOf(0) }
    var retryPolicy by remember(transport, code, peerName, resumeExistingSession) {
        mutableStateOf(PeerSessionRetryPolicy.initial(resumeExistingSession))
    }
    var retainedRetryRoom by remember(transport, code, peerName) {
        mutableStateOf<LocalRoom?>(null)
    }
    var finalLeaveInFlight by remember { mutableStateOf(false) }
    val flowScope = rememberCoroutineScope()

    var hostLost by remember { mutableStateOf(false) }
    var selfOffline by remember { mutableStateOf(false) }

    LaunchedEffect(transport, code, resumeExistingSession, joinAttempt) {
        joinError = null
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
        when (
            val start = awaitAuthoritativeSessionStart(
                room = active,
                expectedGameId = MafiaIds.GameId,
                expectedGameVersion = MafiaHostRoomBridge.GAME_VERSION,
            ) { msg, _ ->
                val ids = msg.players.map(Player::id)
                msg.caseId == "default" &&
                    msg.modeId == MafiaIds.ClassicModeId.raw &&
                    MafiaSessionRules.isValidRoster(msg.players) &&
                    active.selfPlayerId in ids &&
                    active.info.value.hostPlayerId in ids
            }
        ) {
            is Result.Success -> {
                val offer = start.data.offer
                sessionStart = SessionStartingFromHost(
                    caseId = offer.caseId,
                    modeId = offer.modeId,
                    players = offer.players,
                    seed = offer.sessionNonce,
                    protocol = start.data.protocol,
                )
            }
            is Result.Failure -> {
                joinError = start.error.asNetError()
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

    val current = room
    LaunchedEffect(current) {
        if (current != null) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    current.leave()
                }
            }
        }
    }

    val finalBackToHome: () -> Unit = {
        val active = room
        val retained = retainedRetryRoom
        val leaveTarget = retained ?: active
        if (leaveTarget == null) {
            onBackToHome()
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
                        onBackToHome()
                    }
                    is Result.Failure -> {
                        joinError = discarded.error
                        finalLeaveInFlight = false
                    }
                }
            }
        }
    }

    val start = sessionStart
    val localNetworkAccess by transport.localNetworkAccess.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selfOffline) {
                OfflineBanner(label = stringResource(Res.string.md_peer_offline_banner))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    joinError != null -> MafiaPeerErrorState(
                        title = stringResource(Res.string.md_peer_error_title),
                        detail = stringResource(Res.string.md_peer_error_detail),
                        showNetworkRecovery = localNetworkAccess.needsRecoveryGuidance,
                        onRetry = { if (!finalLeaveInFlight) joinAttempt++ },
                        onOpenNetworkSettings = onOpenNetworkSettings.takeIf {
                            localNetworkAccess.needsRecoveryGuidance
                        },
                        onBack = finalBackToHome,
                        actionsEnabled = !finalLeaveInFlight,
                        backInFlight = finalLeaveInFlight,
                        modifier = Modifier.fillMaxSize(),
                    )
                    current == null -> MafiaPeerConnectingState(code = code, modifier = Modifier.fillMaxSize())
                    start == null -> MafiaPeerWaitingForStart(
                        room = current,
                        peerName = peerName,
                        onLeave = finalBackToHome,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> MafiaMultiDevicePeerFlow(
                        players = start.players,
                        selfPlayerId = current.selfPlayerId,
                        seed = start.seed,
                        room = current,
                        protocol = start.protocol,
                        onBackToHome = finalBackToHome,
                        modifier = Modifier.fillMaxSize(),
                        onHostLostChanged = { hostLost = it },
                        onSelfOfflineChanged = { selfOffline = it },
                    )
                }
            }
        }
        if (hostLost) {
            ReconnectingOverlay(
                title = stringResource(Res.string.md_peer_reconnecting),
                leaveLabel = stringResource(Res.string.md_peer_reconnecting_leave),
                leaveContentDescription = stringResource(Res.string.md_peer_reconnecting_leave_description),
                onLeave = finalBackToHome,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MafiaPeerConnectingState(code: String, modifier: Modifier = Modifier) {
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
                text = stringResource(Res.string.md_peer_connecting_format, code),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MafiaPeerWaitingForStart(
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
            EyebrowLabel(text = stringResource(Res.string.md_peer_eyebrow))
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.dramatic,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.l,
                hero = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    Text(
                        text = stringResource(Res.string.md_peer_room_format, info.displayName, peerName),
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
private fun MafiaPeerErrorState(
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

private data class SessionStartingFromHost(
    val caseId: String,
    val modeId: String,
    val players: List<Player>,
    val seed: Long,
    val protocol: SessionProtocol,
)
