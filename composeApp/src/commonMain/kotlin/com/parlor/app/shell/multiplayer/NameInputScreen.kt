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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.resources.Res
import com.parlor.app.resources.name_back
import com.parlor.app.resources.name_back_description
import com.parlor.app.resources.name_confirm_host
import com.parlor.app.resources.name_confirm_host_description
import com.parlor.app.resources.name_confirm_peer
import com.parlor.app.resources.name_confirm_peer_description
import com.parlor.app.resources.name_eyebrow_host
import com.parlor.app.resources.name_eyebrow_peer
import com.parlor.app.resources.name_field
import com.parlor.app.resources.name_help
import com.parlor.app.resources.name_title_host
import com.parlor.app.resources.name_title_peer
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Asks the player to enter the name shown to the room. Reused by both
 * Host and Join flows — the [isHost] flag chooses the right eyebrow and
 * confirm label. Names default to a culture-neutral "Player" placeholder
 * if the user leaves the field blank; non-empty values trim whitespace.
 *
 * Mobile-first: scrollable container, full-width input, full-width primary
 * button, and a secondary back row so the layout fits comfortably on a
 * 360 dp wide phone.
 */
@Composable
fun NameInputScreen(
    isHost: Boolean,
    initial: String,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initial) }
    val sanitized = name.trim().ifBlank { "Player" }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.l)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            Text(
                text = if (isHost) stringResource(Res.string.name_eyebrow_host).uppercase()
                else stringResource(Res.string.name_eyebrow_peer).uppercase(),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
            )
            Text(
                text = if (isHost) stringResource(Res.string.name_title_host)
                else stringResource(Res.string.name_title_peer),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.name_help),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )

            Spacer(Modifier.height(ParlorTheme.spacing.s))

            OutlinedTextField(
                value = name,
                onValueChange = { input -> name = input.take(32) },
                label = { Text(stringResource(Res.string.name_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(ParlorTheme.spacing.m))

            ParlorButton(
                label = if (isHost) stringResource(Res.string.name_confirm_host)
                else stringResource(Res.string.name_confirm_peer),
                contentDescription = if (isHost) {
                    stringResource(Res.string.name_confirm_host_description)
                } else {
                    stringResource(Res.string.name_confirm_peer_description)
                },
                onClick = { onConfirm(sanitized) },
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.name_back),
                contentDescription = stringResource(Res.string.name_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
