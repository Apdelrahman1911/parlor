package com.parlor.games.lastlight.ui.flow.multidevice

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
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.md_host_start_retry
import com.parlor.games.lastlight.resources.md_host_start_retry_description
import com.parlor.games.lastlight.resources.md_return_home
import com.parlor.games.lastlight.resources.md_return_home_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LastLightNetworkStatus(
    title: String,
    body: String?,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    onNewRoom: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            body?.let {
                Text(
                    it,
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            onNewRoom?.let {
                ParlorButton(
                    label = stringResource(Res.string.md_host_start_retry),
                    contentDescription = stringResource(Res.string.md_host_start_retry_description),
                    onClick = it,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ParlorButton(
                label = stringResource(Res.string.md_return_home),
                contentDescription = stringResource(Res.string.md_return_home_description),
                onClick = onLeave,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Secondary,
            )
        }
    }
}
