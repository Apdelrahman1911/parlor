package com.parlor.games.lastlight.ui.flow.multidevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.game_waiting_player
import com.parlor.games.lastlight.resources.md_host_start_retry
import com.parlor.games.lastlight.resources.md_host_start_retry_description
import com.parlor.games.lastlight.resources.md_return_home
import com.parlor.games.lastlight.resources.md_return_home_description
import com.parlor.games.lastlight.resources.table_leave
import com.parlor.games.lastlight.resources.table_leave_description
import com.parlor.games.lastlight.resources.table_recovery_title
import com.parlor.games.lastlight.resources.table_recovery_privacy
import com.parlor.games.lastlight.resources.table_recovery_connection
import com.parlor.games.lastlight.resources.table_recovery_host_format
import com.parlor.games.lastlight.resources.table_recovery_player_format
import com.parlor.games.lastlight.resources.table_recovery_sync
import com.parlor.games.lastlight.resources.table_recovery_sync_title
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightVisibilityReader
import com.parlor.games.lastlight.ui.flow.common.TableControlGlyph
import com.parlor.games.lastlight.ui.flow.common.TableGlyph
import com.parlor.games.lastlight.ui.game.TableBackdrop
import com.parlor.games.lastlight.ui.game.TablePanel
import com.parlor.games.lastlight.ui.theme.DeckButton
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.games.lastlight.ui.theme.LastLightTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LastLightNetworkStatus(
    title: String,
    body: String?,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    onNewRoom: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
    recovering: Boolean = false,
) {
    val visibility = LocalLastLightProcessVisibility.current
    val visibilityReader = LocalLastLightVisibilityReader.current
    val canInteract = {
        val current = visibilityReader?.invoke() ?: visibility
        actionsEnabled && current.isForeground && current.concealmentEpoch == visibility.concealmentEpoch
    }
    val leaveDescription = stringResource(if (recovering) Res.string.table_leave_description else Res.string.md_return_home_description)
    val newRoomDescription = stringResource(Res.string.md_host_start_retry_description)
    LastLightTheme {
        TableBackdrop(modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxSize().align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .parlorSafeContentPadding(20.dp).testTag("game-network-status").semantics { paneTitle = title },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TablePanel(modifier = Modifier.fillMaxWidth(), accent = LastLightColors.Outline) {
                    if (recovering) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TableControlGlyph(TableGlyph.Screen, Modifier.size(40.dp))
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = LastLightColors.Paper,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { heading(); liveRegion = LiveRegionMode.Polite },
                    )
                    body?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = LastLightColors.Paper,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (recovering) {
                        Text(
                            stringResource(Res.string.table_recovery_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = LastLightColors.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    onNewRoom?.let {
                        DeckButton(
                            text = stringResource(Res.string.md_host_start_retry),
                            onClick = { if (canInteract()) it() },
                            enabled = actionsEnabled && visibility.isForeground,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = newRoomDescription },
                        )
                    }
                    DeckButton(
                        text = stringResource(if (recovering) Res.string.table_leave else Res.string.md_return_home),
                        onClick = { if (canInteract()) onLeave() },
                        enabled = actionsEnabled && visibility.isForeground,
                        modifier = Modifier.fillMaxWidth().testTag("game-recovery-leave")
                            .semantics { contentDescription = leaveDescription },
                        secondary = true,
                    )
                }
            }
        }
    }
}

/** Replaces the table, rather than covering retained private pixels/focusable controls. */
@Composable
internal fun LastLightConnectionRecovery(
    state: LastLightRecoveryState,
    onRequestLeave: () -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
) {
    val title = when (state) {
        LastLightRecoveryState.Reconnecting -> stringResource(Res.string.table_recovery_title)
        LastLightRecoveryState.Syncing -> stringResource(Res.string.table_recovery_sync_title)
        is LastLightRecoveryState.ReconnectingToHost -> stringResource(Res.string.table_recovery_host_format, state.displayName)
        is LastLightRecoveryState.WaitingForPlayer -> stringResource(Res.string.game_waiting_player, state.displayName)
    }
    val detail = when (state) {
        LastLightRecoveryState.Reconnecting,
        is LastLightRecoveryState.ReconnectingToHost -> stringResource(Res.string.table_recovery_connection)
        LastLightRecoveryState.Syncing -> stringResource(Res.string.table_recovery_sync)
        is LastLightRecoveryState.WaitingForPlayer -> stringResource(Res.string.table_recovery_player_format, state.displayName)
    }
    Box(modifier.fillMaxSize().testTag("game-connection-recovery")) {
        LastLightNetworkStatus(
            title = title,
            body = detail,
            onLeave = onRequestLeave,
            actionsEnabled = actionsEnabled,
            recovering = true,
        )
    }
}
