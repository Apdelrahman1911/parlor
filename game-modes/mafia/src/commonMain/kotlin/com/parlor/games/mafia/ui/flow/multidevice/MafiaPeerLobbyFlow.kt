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
import com.parlor.games.mafia.domain.settings.MafiaSettings
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
import com.parlor.games.mafia.resources.setup_back
import com.parlor.games.mafia.resources.setup_back_description
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARLOR_PROTOCOL_MAJOR
import com.parlor.networking.protocol.ProtocolValidation
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.protocol.validateFor
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Mafia-side peer lobby. Mirrors composeApp's shell `PeerSessionFlow` but
 * is Mafia-specific: no case loading (Mafia has no external content), and
 * once the host's `SessionStarting` arrives, it dispatches to
 * [MafiaMultiDevicePeerFlow] instead of the Whodunit peer flow.
 */
@Composable
fun MafiaPeerLobbyFlow(
    transport: RoomTransport,
    code: String,
    peerName: String,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var room by remember { mutableStateOf<LocalRoom?>(null) }
    var joinError by remember { mutableStateOf<NetError?>(null) }
    var sessionStart by remember { mutableStateOf<SessionStartingFromHost?>(null) }

    var hostLost by remember { mutableStateOf(false) }
    var selfOffline by remember { mutableStateOf(false) }

    LaunchedEffect(transport, code) {
        when (val result = transport.join(code, peerName)) {
            is Result.Success -> room = result.data
            is Result.Failure -> joinError = result.error
        }
    }

    LaunchedEffect(room) {
        val active = room ?: return@LaunchedEffect
        // Take exactly one start frame, then relinquish the Channel-backed
        // inbox to MafiaPeerRoomBridge. A long-lived lobby collector would
        // race the coordinator and steal snapshots.
        val msg = active.incoming.filterIsInstance<HostMessage.SessionStarting>().first()
        val header = msg.header
        val candidate = header?.let {
            SessionProtocol(
                sessionId = it.sessionId,
                gameId = MafiaIds.GameId,
                gameVersion = MafiaHostRoomBridge.GAME_VERSION,
            )
        }
        val ids = msg.players.map(Player::id)
        if (
            header == null ||
            candidate == null ||
            header.protocol.major != PARLOR_PROTOCOL_MAJOR ||
            header.sequence != 0L ||
            header.validateFor(candidate) != ProtocolValidation.Valid ||
            msg.caseId != "default" ||
            msg.modeId != MafiaIds.ClassicModeId.raw ||
            msg.players.size !in MafiaSettings.MIN_PLAYERS..MafiaSettings.MAX_PLAYERS ||
            active.selfPlayerId !in ids ||
            active.info.value.hostPlayerId !in ids ||
            ids.distinct().size != ids.size
        ) {
            joinError = NetError.IncompatibleProtocol
            return@LaunchedEffect
        }
        sessionStart = SessionStartingFromHost(
            caseId = msg.caseId,
            modeId = msg.modeId,
            players = msg.players,
            seed = msg.sessionNonce,
            protocol = candidate,
        )
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

    val start = sessionStart

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
                        onBack = onBackToHome,
                        modifier = Modifier.fillMaxSize(),
                    )
                    current == null -> MafiaPeerConnectingState(code = code, modifier = Modifier.fillMaxSize())
                    start == null -> MafiaPeerWaitingForStart(
                        room = current,
                        peerName = peerName,
                        onLeave = onBackToHome,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> MafiaMultiDevicePeerFlow(
                        players = start.players,
                        selfPlayerId = current.selfPlayerId,
                        seed = start.seed,
                        room = current,
                        protocol = start.protocol,
                        onBackToHome = onBackToHome,
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
                onLeave = onBackToHome,
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
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
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
            ParlorButton(
                label = stringResource(Res.string.setup_back),
                contentDescription = stringResource(Res.string.setup_back_description),
                onClick = onBack,
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
