package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_intro_continue
import com.parlor.games.whodunit.resources.setup_intro_continue_description
import com.parlor.games.whodunit.resources.setup_intro_read_aloud_eyebrow
import com.parlor.games.whodunit.resources.setup_intro_what_is_true_eyebrow
import org.jetbrains.compose.resources.stringResource

@Composable
fun PublicIntroScreen(
    title: String,
    intro: String,
    bedrockClues: List<String>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            Text(
                text = stringResource(Res.string.setup_intro_read_aloud_eyebrow),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = title,
                style = ParlorTheme.typography.displayHero,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = intro,
                style = ParlorTheme.typography.narration,
                color = ParlorTheme.colors.textNarration,
            )
            Text(
                text = stringResource(Res.string.setup_intro_what_is_true_eyebrow),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
            )
            bedrockClues.forEach { clue ->
                Text(
                    text = "·  $clue",
                    style = ParlorTheme.typography.bodyLarge,
                    color = ParlorTheme.colors.textPrimary,
                )
            }
            ParlorButton(
                label = stringResource(Res.string.setup_intro_continue),
                contentDescription = stringResource(Res.string.setup_intro_continue_description),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
