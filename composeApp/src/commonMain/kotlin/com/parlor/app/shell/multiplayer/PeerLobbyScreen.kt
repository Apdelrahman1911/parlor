package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import com.parlor.app.resources.Res
import com.parlor.app.resources.peer_connecting_format
import com.parlor.app.resources.peer_eyebrow
import com.parlor.app.resources.peer_leave
import com.parlor.app.resources.peer_leave_description
import com.parlor.app.resources.peer_send_pause
import com.parlor.app.resources.peer_send_pause_description
import com.parlor.app.resources.peer_sent_log_format
import com.parlor.app.resources.peer_title_format
import com.parlor.core.result.Result
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.action.WhodunitActionCodec
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 8 peer-side lobby. Calls [transport].join(code, displayName) once on
 * entry, then exposes:
 *  - the connected room info,
 *  - a `Send Pause` button that encodes [WhodunitAction.Pause] via
 *    [WhodunitActionCodec] and submits it as `PeerMessage.ActionSubmit`,
 *    closing the loop with the host's [HostLobbyScreen].
 *
 * This is intentionally minimal — proves bidirectional message flow over
 * the real `RoomTransport`. Phase 8.5 mirrors the host's public state and
 * routes the peer into a passive [com.parlor.games.whodunit.ui.flow.WhodunitGameFlow]
 * view.
 */
@Composable
fun PeerLobbyScreen(
    transport: RoomTransport,
    code: String,
    displayName: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var room by remember { mutableStateOf<LocalRoom?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }
    val sentLog = remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(transport, code, displayName) {
        when (val result = transport.join(code, displayName)) {
            is Result.Success -> room = result.data
            is Result.Failure -> joinError = result.error.toString()
        }
    }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            Text(
                text = stringResource(Res.string.peer_eyebrow).uppercase(),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
            )

            val current = room
            when {
                joinError != null -> Text(
                    text = "Could not join: $joinError",
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textPrimary,
                )
                current == null -> Text(
                    text = stringResource(Res.string.peer_connecting_format).format(code),
                    style = ParlorTheme.typography.displayMedium,
                    color = ParlorTheme.colors.textPrimary,
                )
                else -> {
                    // Pre-resolve composable string outside the non-composable
                    // launch{} body.
                    val sentFormat = stringResource(Res.string.peer_sent_log_format)
                    PeerLobbyContent(
                        room = current,
                        sentLog = sentLog.value,
                        onSendPause = {
                            scope.launch {
                                val bytes = WhodunitActionCodec.encode(WhodunitAction.Pause)
                                current.sendToHost(
                                    PeerMessage.ActionSubmit(
                                        sender = current.selfPlayerId,
                                        payload = bytes,
                                    ),
                                )
                                sentLog.value = sentLog.value + sentFormat.format("Pause")
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))

            ParlorButton(
                label = stringResource(Res.string.peer_leave),
                contentDescription = stringResource(Res.string.peer_leave_description),
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
private fun PeerLobbyContent(
    room: LocalRoom,
    sentLog: List<String>,
    onSendPause: () -> Unit,
) {
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
                text = stringResource(Res.string.peer_title_format).format(info.displayName),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = "Room: ${info.code}  ·  Status: ${info.status}",
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
            )
            Text(
                text = "Members: ${members.joinToString { it.displayName }}",
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
            )
        }
    }

    ParlorButton(
        label = stringResource(Res.string.peer_send_pause),
        contentDescription = stringResource(Res.string.peer_send_pause_description),
        onClick = onSendPause,
        modifier = Modifier.fillMaxWidth(),
    )

    if (sentLog.isNotEmpty()) {
        sentLog.takeLast(10).forEach { line ->
            Text(
                text = line,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textTertiary,
            )
        }
    }
}

private fun String.format(arg: String): String = replace("%1\$s", arg)
