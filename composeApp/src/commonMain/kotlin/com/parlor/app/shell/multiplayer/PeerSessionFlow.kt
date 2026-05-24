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
import androidx.compose.runtime.DisposableEffect
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
import com.parlor.app.resources.peer_connecting_format
import com.parlor.app.resources.peer_eyebrow
import com.parlor.app.resources.peer_leave
import com.parlor.app.resources.peer_leave_description
import com.parlor.content.repository.CaseRepository
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ModeId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.ui.flow.WhodunitMultiplayerPeerFlow
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
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
    val repository: CaseRepository = koinInject()
    val payloadValidator: PayloadValidator<WhodunitCase> = koinInject(qualifier = named("whodunit"))
    val scope = rememberCoroutineScope()

    var room by remember { mutableStateOf<LocalRoom?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var sessionStart by remember { mutableStateOf<SessionStartingFromHost?>(null) }

    LaunchedEffect(transport, code) {
        when (val result = transport.join(code, peerName)) {
            is Result.Success -> room = result.data
            is Result.Failure -> joinError = result.error.toString()
        }
    }

    LaunchedEffect(room) {
        val active = room ?: return@LaunchedEffect
        active.incoming.filterIsInstance<HostMessage.SessionStarting>().collect { msg ->
            sessionStart = SessionStartingFromHost(
                caseId = msg.caseId,
                modeId = msg.modeId,
                players = msg.players,
                seed = msg.seed,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val active = room
            if (active != null) {
                scope.launch { runCatching { active.leave() } }
            }
        }
    }

    val current = room
    val start = sessionStart

    when {
        joinError != null -> PeerErrorState(joinError!!, onBack = onBackToLibrary, modifier = modifier)
        current == null -> PeerConnectingState(code, modifier = modifier)
        start == null -> PeerWaitingForHostStart(current, peerName, modifier = modifier, onLeave = onBackToLibrary)
        else -> PeerSessionWithCase(
            transport = transport,
            room = current,
            start = start,
            peerName = peerName,
            onBackToLibrary = onBackToLibrary,
            modifier = modifier,
        )
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
            "Couldn't load the case: ${r.error}",
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
                onBackToLibrary = onBackToLibrary,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PeerConnectingState(code: String, modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.peer_connecting_format).replace("%1\$s", code),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(ParlorTheme.spacing.xl),
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
            Text(
                text = stringResource(Res.string.peer_eyebrow).uppercase(),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
            )

            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.dramatic,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.l,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    Text(
                        text = "In ${info.displayName}'s room as $peerName",
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Waiting for the host to start the game…",
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                    )
                    Text(
                        text = "Room: ${info.code}",
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
            )
        }
    }
}

@Composable
private fun PeerLoadingCase(modifier: Modifier = Modifier) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Loading the case…",
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(ParlorTheme.spacing.xl),
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
                text = "Couldn't join the room.",
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
                label = "Back",
                contentDescription = "Return to the home screen.",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
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
)
