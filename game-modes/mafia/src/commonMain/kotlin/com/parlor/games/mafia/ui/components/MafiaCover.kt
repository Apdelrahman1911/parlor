package com.parlor.games.mafia.ui.components

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
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

@Composable
fun MafiaCandlelitCover(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.coverScreen)
            .clickable { onDismiss() },
    ) {
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
                color = ParlorTheme.colors.coverScreenTextPrimary,
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.padding(top = ParlorTheme.spacing.m)) {
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

@Composable
fun MafiaHideScreen(
    line: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.coverScreen)
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line,
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.coverScreenTextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(ParlorTheme.spacing.xxl),
        )
    }
}
