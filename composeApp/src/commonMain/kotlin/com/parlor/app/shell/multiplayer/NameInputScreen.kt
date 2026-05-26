package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
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
import com.parlor.designsystem.components.ParlorTextField
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.StickyActionBar
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
        Box(modifier = Modifier.fillMaxSize().imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ParlorTheme.spacing.l,
                        end = ParlorTheme.spacing.l,
                        top = ParlorTheme.spacing.l,
                        bottom = ParlorTheme.spacing.xxxl + ParlorTheme.spacing.xxl,
                    ),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            ) {
                ScreenHeader(
                    title = if (isHost) stringResource(Res.string.name_title_host)
                    else stringResource(Res.string.name_title_peer),
                    eyebrow = if (isHost) stringResource(Res.string.name_eyebrow_host)
                    else stringResource(Res.string.name_eyebrow_peer),
                    subtitle = stringResource(Res.string.name_help),
                    onBack = onBack,
                    backContentDescription = stringResource(Res.string.name_back_description),
                )

                ParlorTextField(
                    value = name,
                    onValueChange = { input -> name = input.take(32) },
                    label = stringResource(Res.string.name_field),
                )
            }

            StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
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
            }
        }
    }
}
