package com.parlor.app.shell.game.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.room_game_error_body
import com.parlor.app.resources.room_game_incompatible
import com.parlor.app.resources.room_game_leave
import com.parlor.app.resources.room_game_new_room
import com.parlor.app.resources.room_game_retry
import com.parlor.app.resources.room_game_settings
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.NetError
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomGameStatus(
    title: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    onRetry: (() -> Unit)? = null,
    onNewRoom: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    HeroBackdrop(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).parlorSafeContentPadding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = ParlorTheme.colors.textPrimary, style = ParlorTheme.typography.displayMedium,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
            body?.let {
                Text(it, color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            onRetry?.let { RoomButton(stringResource(Res.string.room_game_retry), it, enabled) }
            onNewRoom?.let { RoomButton(stringResource(Res.string.room_game_new_room), it, enabled) }
            onSettings?.let { RoomButton(stringResource(Res.string.room_game_settings), it, enabled) }
            RoomButton(stringResource(Res.string.room_game_leave), onLeave, enabled, secondary = true)
        }
    }
}

@Composable
internal fun RoomButton(label: String, onClick: () -> Unit, enabled: Boolean = true, secondary: Boolean = false) {
    ParlorButton(label = label, contentDescription = label, onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), variant = if (secondary) ParlorButtonVariant.Secondary else ParlorButtonVariant.Primary)
}

@Composable
internal fun roomGameError(error: NetError): String = stringResource(
    if (error == NetError.IncompatibleProtocol) Res.string.room_game_incompatible else Res.string.room_game_error_body,
)
