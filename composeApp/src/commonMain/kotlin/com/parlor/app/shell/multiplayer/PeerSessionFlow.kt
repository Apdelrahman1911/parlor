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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.party_offline_banner
import com.parlor.app.resources.party_reconnecting_overlay_leave
import com.parlor.app.resources.party_reconnecting_overlay_leave_description
import com.parlor.app.resources.party_reconnecting_overlay_title
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.OfflineBanner
import com.parlor.designsystem.components.ReconnectingOverlay
import com.parlor.app.resources.error_back
import com.parlor.app.resources.error_back_description
import com.parlor.app.resources.peer_case_load_error_format
import com.parlor.app.resources.peer_connecting_format
import com.parlor.app.resources.peer_error_title
import com.parlor.app.resources.peer_eyebrow
import com.parlor.app.resources.peer_leave
import com.parlor.app.resources.peer_leave_description
import com.parlor.app.resources.peer_loading_case
import com.parlor.app.resources.peer_room_code_label_format
import com.parlor.app.resources.peer_room_header_format
import com.parlor.app.resources.peer_waiting_for_start
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
import com.parlor.games.whodunit.ui.flow.WhodunitMultiplayerPeerFlow
import com.parlor.games.whodunit.ui.flow.multiplayer.WhodunitHostRoomBridge
import com.parlor.app.shell.dataErrorMessage
import com.parlor.app.shell.netErrorMessage
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
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/**
 * End-to-end peer flow: join the room, wait for the host's `SessionStarting`
 * message, load the chosen case, and hand off to [WhodunitMultiplayerPeerFlow].
 * Owns the [LocalRoom] for the lifetime of the session.
 */
@Composable
fun PeerSessionFlow(
    transport: RoomTransport,
    code: String,
    peerName: String,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var room by remember { mutableStateOf<LocalRoom?>(null) }
    // Keep the typed error so the rendering site can localise it. Stringifying
    // here would lose the type and ship "NetError$TransportFailure(reason=...)"
    // straight to the user.
    var joinError by remember { mutableStateOf<NetError?>(null) }
    var sessionStart by remember { mutableStateOf<SessionStartingFromHost?>(null) }

    LaunchedEffect(transport, code) {
        when (val result = transport.join(code, peerName)) {
            is Result.Success -> room = result.data
            is Result.Failure -> joinError = result.error
        }
    }

    LaunchedEffect(room) {
        val active = room ?: return@LaunchedEffect
        // Consume exactly one start frame, then release the Channel-backed
        // inbox to WhodunitPeerRoomBridge. Keeping this collector alive would
        // race the coordinator and nondeterministically steal snapshots.
        val msg = active.incoming.filterIsInstance<HostMessage.SessionStarting>().first()
        val header = msg.header
        val candidate = header?.let {
            SessionProtocol(
                sessionId = it.sessionId,
                gameId = WhodunitIds.GameId,
                gameVersion = WhodunitHostRoomBridge.GAME_VERSION,
            )
        }
        val ids = msg.players.map(Player::id)
        val validPlayerCount = when (msg.modeId) {
            WhodunitIds.ClassicVoteModeId.raw -> msg.players.size in 4..6
            WhodunitIds.EliminationModeId.raw -> msg.players.size in 5..6
            else -> false
        }
        if (
            header == null ||
            candidate == null ||
            header.protocol.major != PARLOR_PROTOCOL_MAJOR ||
            header.sequence != 0L ||
            header.validateFor(candidate) != ProtocolValidation.Valid ||
            msg.caseId.isBlank() ||
            !validPlayerCount ||
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
                runCatching { active.leave() }
            }
        }
    }

    val current = room
    val start = sessionStart

    // Wave 9H-8 connection presenter state. The WhodunitMultiplayerPeerFlow
    // owns the peer bridge that synthesises HostLost / SelfOffline events;
    // when we wire its connectionEvents through here, these flags drive
    // the overlay + banner. For now both default to false — the screens
    // below still render the inner flow, and a follow-up will hook the
    // bridge's SharedFlow into a PartyConnectionPresenter at this scope.
    var hostLost by remember { mutableStateOf(false) }
    var selfOffline by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selfOffline) {
                OfflineBanner(label = stringResource(Res.string.party_offline_banner))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    joinError != null -> PeerErrorState(
                        netErrorMessage(joinError!!),
                        onBack = onBackToLibrary,
                        modifier = Modifier.fillMaxSize(),
                    )
                    current == null -> PeerConnectingState(code, modifier = Modifier.fillMaxSize())
                    start == null -> PeerWaitingForHostStart(
                        current,
                        peerName,
                        modifier = Modifier.fillMaxSize(),
                        onLeave = onBackToLibrary,
                    )
                    else -> PeerSessionWithCase(
                        transport = transport,
                        room = current,
                        start = start,
                        peerName = peerName,
                        onBackToLibrary = onBackToLibrary,
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
                onLeave = onBackToLibrary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PeerSessionWithCase(
    transport: RoomTransport,
    room: LocalRoom,
    start: SessionStartingFromHost,
    peerName: String,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    onHostLostChanged: (Boolean) -> Unit = {},
    onSelfOfflineChanged: (Boolean) -> Unit = {},
) {
    val repository: CaseRepository = koinInject()
    val payloadValidator: PayloadValidator<WhodunitCase> = koinInject(qualifier = named("whodunit"))

    val caseResult by produceState<Result<ValidatedCase<WhodunitCase>, DataError>?>(
        initialValue = null,
        key1 = start.caseId,
    ) {
        value = repository.loadCase(CaseId(start.caseId), payloadValidator)
    }

    when (val r = caseResult) {
        null -> PeerLoadingCase(modifier)
        is Result.Failure -> PeerErrorState(
            stringResource(Res.string.peer_case_load_error_format).replace("%1\$s", dataErrorMessage(r.error)),
            onBack = onBackToLibrary,
            modifier = modifier,
        )
        is Result.Success -> {
            val case = r.data
            val selfId = room.selfPlayerId
            WhodunitMultiplayerPeerFlow(
                case = case,
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
    }
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
private fun PeerLoadingCase(modifier: Modifier = Modifier) {
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
                text = stringResource(Res.string.peer_loading_case),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PeerErrorState(error: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
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
            ParlorButton(
                label = stringResource(Res.string.error_back),
                contentDescription = stringResource(Res.string.error_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}

/** Local snapshot of a SessionStarting message — keeps the Compose state simple. */
private data class SessionStartingFromHost(
    val caseId: String,
    val modeId: String,
    val players: List<Player>,
    val seed: Long,
    val protocol: SessionProtocol,
)
