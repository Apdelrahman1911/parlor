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
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorScrim
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.endgame_body
import com.parlor.games.whodunit.resources.endgame_cancel
import com.parlor.games.whodunit.resources.endgame_cancel_description
import com.parlor.games.whodunit.resources.endgame_quietly
import com.parlor.games.whodunit.resources.endgame_quietly_description
import com.parlor.games.whodunit.resources.endgame_reveal_now
import com.parlor.games.whodunit.resources.endgame_reveal_now_description
import com.parlor.games.whodunit.resources.endgame_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun EndGameDialog(
    onRevealNow: () -> Unit,
    onEndQuietly: () -> Unit,
    onCancel: () -> Unit,
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
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                    Text(
                        text = stringResource(Res.string.endgame_title),
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.endgame_body),
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                    )
                    ParlorButton(
                        label = stringResource(Res.string.endgame_reveal_now),
                        contentDescription = stringResource(Res.string.endgame_reveal_now_description),
                        onClick = onRevealNow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ParlorButton(
                        label = stringResource(Res.string.endgame_quietly),
                        contentDescription = stringResource(Res.string.endgame_quietly_description),
                        onClick = onEndQuietly,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ParlorButtonVariant.Secondary,
                    )
                    ParlorButton(
                        label = stringResource(Res.string.endgame_cancel),
                        contentDescription = stringResource(Res.string.endgame_cancel_description),
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ParlorButtonVariant.Ghost,
                    )
                }
            }
        }
    }
}
