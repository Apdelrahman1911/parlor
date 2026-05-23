package com.parlor.games.whodunit.ui.screens.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_action_alibi_body_format
import com.parlor.games.whodunit.resources.round_action_alibi_done
import com.parlor.games.whodunit.resources.round_action_alibi_eyebrow
import com.parlor.games.whodunit.resources.round_action_alibi_headline_format
import com.parlor.games.whodunit.resources.round_action_monologue_body_format
import com.parlor.games.whodunit.resources.round_action_monologue_done
import com.parlor.games.whodunit.resources.round_action_monologue_eyebrow
import com.parlor.games.whodunit.resources.round_action_monologue_headline_format
import com.parlor.games.whodunit.resources.round_action_questions_body
import com.parlor.games.whodunit.resources.round_action_questions_done
import com.parlor.games.whodunit.resources.round_action_questions_eyebrow
import com.parlor.games.whodunit.resources.round_action_questions_headline
import com.parlor.games.whodunit.resources.round_action_silent_body
import com.parlor.games.whodunit.resources.round_action_silent_done
import com.parlor.games.whodunit.resources.round_action_silent_eyebrow
import com.parlor.games.whodunit.resources.round_action_silent_headline
import org.jetbrains.compose.resources.stringResource

@Composable
fun AlibiRoundScreen(
    currentPlayerName: String,
    secondsPerPlayer: Int,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructuredPrompt(
        eyebrow = stringResource(Res.string.round_action_alibi_eyebrow),
        headline = stringResource(Res.string.round_action_alibi_headline_format, currentPlayerName),
        body = stringResource(Res.string.round_action_alibi_body_format, secondsPerPlayer),
        buttonLabel = stringResource(Res.string.round_action_alibi_done),
        onContinue = onAdvance,
        modifier = modifier,
    )
}

@Composable
fun DirectedQuestionsScreen(
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructuredPrompt(
        eyebrow = stringResource(Res.string.round_action_questions_eyebrow),
        headline = stringResource(Res.string.round_action_questions_headline),
        body = stringResource(Res.string.round_action_questions_body),
        buttonLabel = stringResource(Res.string.round_action_questions_done),
        onContinue = onAdvance,
        modifier = modifier,
    )
}

@Composable
fun SilentAccusationScreen(
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructuredPrompt(
        eyebrow = stringResource(Res.string.round_action_silent_eyebrow),
        headline = stringResource(Res.string.round_action_silent_headline),
        body = stringResource(Res.string.round_action_silent_body),
        buttonLabel = stringResource(Res.string.round_action_silent_done),
        onContinue = onAdvance,
        modifier = modifier,
    )
}

@Composable
fun MonologueScreen(
    currentPlayerName: String,
    secondsRemaining: Int,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StructuredPrompt(
        eyebrow = stringResource(Res.string.round_action_monologue_eyebrow),
        headline = stringResource(Res.string.round_action_monologue_headline_format, currentPlayerName),
        body = stringResource(Res.string.round_action_monologue_body_format, secondsRemaining),
        buttonLabel = stringResource(Res.string.round_action_monologue_done),
        onContinue = onAdvance,
        modifier = modifier,
    )
}

@Composable
private fun StructuredPrompt(
    eyebrow: String,
    headline: String,
    body: String,
    buttonLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(
                ParlorTheme.spacing.l,
                androidx.compose.ui.Alignment.CenterVertically,
            ),
        ) {
            Text(
                text = eyebrow,
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = headline,
                style = ParlorTheme.typography.displayLarge,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = body,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = buttonLabel,
                contentDescription = buttonLabel,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
