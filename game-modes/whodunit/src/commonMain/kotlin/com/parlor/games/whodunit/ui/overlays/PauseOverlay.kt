package com.parlor.games.whodunit.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorScrim
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.pause_body
import com.parlor.games.whodunit.resources.pause_end_game
import com.parlor.games.whodunit.resources.pause_end_game_description
import com.parlor.games.whodunit.resources.pause_resume
import com.parlor.games.whodunit.resources.pause_resume_description
import com.parlor.games.whodunit.resources.pause_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onEndGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        ParlorScrim(alpha = 0.85f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.high,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xxl,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l)) {
                    Text(
                        text = stringResource(Res.string.pause_title),
                        style = ParlorTheme.typography.displayLarge,
                        color = ParlorTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(Res.string.pause_body),
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ParlorButton(
                        label = stringResource(Res.string.pause_resume),
                        contentDescription = stringResource(Res.string.pause_resume_description),
                        onClick = onResume,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ParlorButton(
                        label = stringResource(Res.string.pause_end_game),
                        contentDescription = stringResource(Res.string.pause_end_game_description),
                        onClick = onEndGame,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
