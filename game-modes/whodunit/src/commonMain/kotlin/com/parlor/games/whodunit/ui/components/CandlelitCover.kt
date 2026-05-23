package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.AmbientBackdrop
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Full-screen candlelit cover. Used as:
 * - the hand-off prompt before a player reveals their dossier
 * - the hide-and-pass screen after a player taps "I'm Done"
 *
 * The screen fully occludes prior content — a tap dismisses and advances.
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
            .background(Color.Black)
            .clickable { onDismiss() },
    ) {
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), bloomIntensity = 0.06f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ParlorTheme.spacing.xxl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = ParlorTheme.typography.displayMedium,
                    color = ParlorTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Box(modifier = Modifier.padding(top = ParlorTheme.spacing.m)) {
                    Text(
                        text = subtitle,
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Full-black hide screen — minimal text, single tap to advance. */
@Composable
fun HideScreen(
    line: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line,
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(ParlorTheme.spacing.xxl),
        )
    }
}
