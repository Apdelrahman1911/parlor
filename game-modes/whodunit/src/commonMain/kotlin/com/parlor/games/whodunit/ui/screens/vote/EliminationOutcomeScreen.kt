package com.parlor.games.whodunit.ui.screens.vote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.elim_innocent_body
import com.parlor.games.whodunit.resources.elim_innocent_continue
import com.parlor.games.whodunit.resources.elim_innocent_continue_description
import com.parlor.games.whodunit.resources.elim_innocent_eyebrow
import com.parlor.games.whodunit.resources.elim_innocent_peer_hint
import com.parlor.games.whodunit.resources.elim_innocent_title_format
import org.jetbrains.compose.resources.stringResource

/**
 * Elimination Mode: shown after the room votes off a non-killer and the game
 * continues. Per design doc §13 the app must surface *"[Name] was innocent.
 * The killer is still among you."* — without this screen the round flips to
 * the next clue silently, which makes the wrong-elimination cost invisible
 * to the table.
 *
 * The host taps Continue (submits `AcknowledgeRevealCard`); peers see the
 * same content with a waiting hint in place of the button.
 */
@Composable
fun EliminationInnocentOutcomeScreen(
    eliminatedPlayerName: String,
    onContinue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.elim_innocent_eyebrow))
            Text(
                text = stringResource(Res.string.elim_innocent_title_format, eliminatedPlayerName),
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.elim_innocent_body),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(ParlorTheme.spacing.l))
            if (onContinue != null) {
                ParlorButton(
                    label = stringResource(Res.string.elim_innocent_continue),
                    contentDescription = stringResource(Res.string.elim_innocent_continue_description),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(Res.string.elim_innocent_peer_hint),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
