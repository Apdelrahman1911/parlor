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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.host_cancel
import com.parlor.app.resources.host_cancel_description
import com.parlor.app.resources.host_log_eyebrow
import com.parlor.app.resources.host_log_empty
import com.parlor.app.resources.host_log_received_format
import com.parlor.app.resources.host_members_eyebrow
import com.parlor.app.resources.host_members_empty
import com.parlor.app.resources.host_room_code_eyebrow
import com.parlor.app.resources.host_starting
import com.parlor.app.resources.host_title
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 8 host lobby — the smallest UI proving real multi-device wiring.
 *
 * On entry: calls [transport].host(...) once. While in-flight, shows a
 * loading state. Once the room is up the screen displays:
 *  - the 6-character room code in large display type for dictation,
 *  - the live member list (peers join via the [JoinPromptScreen]),
 *  - a log of incoming `PeerMessage.ActionSubmit` payloads decoded via
 *    [WhodunitActionCodec], proving the wire path is real.
 *
 * No game flow is wired through this screen yet — Phase 8.5 will route the
 * host into [com.parlor.games.whodunit.ui.flow.WhodunitGameFlow] once at
 * least one peer is connected.
 */
@Composable
fun HostLobbyScreen(
    transport: RoomTransport,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var room by remember { mutableStateOf<LocalRoom?>(null) }
    var hostError by remember { mutableStateOf<String?>(null) }
    val actionLog = remember { mutableStateOf<List<String>>(emptyList()) }

    // One-shot host call. Re-runs only if the transport identity changes.
    LaunchedEffect(transport) {
        when (val result = transport.host(HostConfig(roomDisplayName = "Parlor"))) {
            is Result.Success -> room = result.data
            is Result.Failure -> hostError = result.error.toString()
        }
    }

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

    // String-resource lookups have to happen in the composable scope; pre-
    // resolve the format strings here so the non-composable LaunchedEffect
    // body can use them.
    val receivedPrefix = stringResource(Res.string.host_log_received_format)

    // Decode incoming peer messages once the room is up. Keyed on room so a
    // re-host (after leave + return) doesn't double-subscribe. The room's
    // `incoming` is `Flow<RoomMessage>`; on the host side every value is a
    // PeerMessage subtype, so filterIsInstance narrows safely.
    LaunchedEffect(room) {
        val active = room ?: return@LaunchedEffect
        active.incoming.filterIsInstance<PeerMessage>().collect { msg ->
            val rendered = when (msg) {
                is PeerMessage.ActionSubmit -> {
                    val action = runCatching { WhodunitActionCodec.decode(msg.payload) }.getOrNull()
                    if (action != null) {
                        receivedPrefix.format("Action: $action")
                    } else {
                        "ActionSubmit (undecodable, ${msg.payload.size} bytes)"
                    }
                }
                is PeerMessage.AdmissionRequest -> "AdmissionRequest"
                is PeerMessage.ClientCommand -> "ClientCommand(${msg.payload.size} bytes)"
                is PeerMessage.SnapshotRequest -> "SnapshotRequest"
                is PeerMessage.SessionHeartbeat -> "SessionHeartbeat"
                is PeerMessage.CommandOutcomeRequest -> "CommandOutcomeRequest"
                is PeerMessage.JoinRequest -> "JoinRequest(${msg.displayName})"
                is PeerMessage.LeaveNotice -> "LeaveNotice"
                is PeerMessage.Heartbeat -> "Heartbeat"
            }
            actionLog.value = actionLog.value + rendered
        }
    }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            Text(
                text = stringResource(Res.string.host_title).uppercase(),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
            )

            val current = room
            when {
                hostError != null -> Text(
                    text = "Could not host: $hostError",
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textPrimary,
                )
                current == null -> Text(
                    text = stringResource(Res.string.host_starting),
                    style = ParlorTheme.typography.displayMedium,
                    color = ParlorTheme.colors.textPrimary,
                )
                else -> HostLobbyContent(
                    room = current,
                    log = actionLog.value,
                )
            }

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))

            ParlorButton(
                label = stringResource(Res.string.host_cancel),
                contentDescription = stringResource(Res.string.host_cancel_description),
                onClick = {
                    scope.launch {
                        room?.runCatching { leave() }
                        onLeave()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HostLobbyContent(room: LocalRoom, log: List<String>) {
    val info by room.info.collectAsState()
    val members by room.members.collectAsState()

    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = ParlorTheme.elevation.dramatic,
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            Text(
                text = stringResource(Res.string.host_room_code_eyebrow).uppercase(),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = info.code,
                style = ParlorTheme.typography.displayHero,
                color = ParlorTheme.colors.accentEmber,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Status: ${info.status}",
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
            )
        }
    }

    Text(
        text = stringResource(Res.string.host_members_eyebrow).uppercase(),
        style = ParlorTheme.typography.labelSmall,
        color = ParlorTheme.colors.textSecondary,
    )
    if (members.isEmpty()) {
        Text(
            text = stringResource(Res.string.host_members_empty),
            style = ParlorTheme.typography.bodyMedium,
            color = ParlorTheme.colors.textTertiary,
        )
    } else {
        members.forEach { member ->
            Text(
                text = "· ${member.displayName}",
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textPrimary,
            )
        }
    }

    Text(
        text = stringResource(Res.string.host_log_eyebrow).uppercase(),
        style = ParlorTheme.typography.labelSmall,
        color = ParlorTheme.colors.textSecondary,
    )
    if (log.isEmpty()) {
        Text(
            text = stringResource(Res.string.host_log_empty),
            style = ParlorTheme.typography.bodyMedium,
            color = ParlorTheme.colors.textTertiary,
        )
    } else {
        log.takeLast(10).forEach { line ->
            Text(
                text = line,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textPrimary,
            )
        }
    }
}

private fun String.format(arg: String): String = replace("%1\$s", arg)
