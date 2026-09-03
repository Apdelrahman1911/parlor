package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.ContextRibbon
import com.parlor.designsystem.components.ParlorContextTone
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.private_do_not_pass
import com.parlor.games.whodunit.resources.private_screen_label
import org.jetbrains.compose.resources.stringResource

/**
 * Pass-the-phone cover screen. Pure-black body in both light and dark
 * modes so the previous player's dossier is fully occluded before the
 * next player takes the device. A tap dismisses.
 */
@Composable
fun CandlelitCover(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.coverScreen)
            .clickable(role = Role.Button, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xxl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.xl,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContextRibbon(
                label = stringResource(Res.string.private_screen_label),
                detail = stringResource(Res.string.private_do_not_pass),
                tone = ParlorContextTone.Private,
                inverted = true,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = ParlorTheme.typography.displayMedium,
                    color = ParlorTheme.colors.coverScreenTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.coverScreenTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Black hide screen — minimal text, single tap to advance. */
@Composable
fun HideScreen(
    line: String,
    onTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.coverScreen)
            .then(
                if (onTap != null) {
                    Modifier.clickable(role = Role.Button, onClick = onTap)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line,
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.coverScreenTextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(ParlorTheme.spacing.xxl)
                .verticalScroll(rememberScrollState()),
        )
    }
}
