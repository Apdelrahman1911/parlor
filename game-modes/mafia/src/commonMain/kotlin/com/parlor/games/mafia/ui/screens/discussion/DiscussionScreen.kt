package com.parlor.games.mafia.ui.screens.discussion

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.discussion_alive_card
import com.parlor.games.mafia.resources.discussion_dead_card
import com.parlor.games.mafia.resources.discussion_eyebrow_format
import com.parlor.games.mafia.resources.discussion_headline
import com.parlor.games.mafia.resources.discussion_open_vote
import com.parlor.games.mafia.resources.discussion_open_vote_description
import org.jetbrains.compose.resources.stringResource

/**
 * Public discussion screen. Per the no-chat constraint, this only shows
 * a timer (or "untimed") and the list of living players. Conversation is
 * verbal/in-person.
 */
@Composable
fun DiscussionScreen(
    day: Int,
    aliveNames: List<String>,
    deadNames: List<String>,
    timerLabel: String?,
    isHost: Boolean,
    onOpenVote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.discussion_eyebrow_format, day),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.discussion_headline),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (timerLabel != null) {
                Text(
                    text = timerLabel,
                    style = ParlorTheme.typography.timerLarge,
                    color = ParlorTheme.colors.accentEmber,
                    textAlign = TextAlign.Center,
                )
            }

            PlayerListCard(title = stringResource(Res.string.discussion_alive_card), names = aliveNames)
            if (deadNames.isNotEmpty()) {
                PlayerListCard(
                    title = stringResource(Res.string.discussion_dead_card),
                    names = deadNames,
                    dim = true,
                )
            }

            if (isHost) {
                ParlorButton(
                    label = stringResource(Res.string.discussion_open_vote),
                    contentDescription = stringResource(Res.string.discussion_open_vote_description),
                    onClick = onOpenVote,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlayerListCard(title: String, names: List<String>, dim: Boolean = false) {
    ParlorCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
        ) {
            EyebrowLabel(text = title, accent = false)
            names.forEach { name ->
                Text(
                    text = name,
                    style = ParlorTheme.typography.bodyLarge,
                    color = if (dim) ParlorTheme.colors.textSecondary else ParlorTheme.colors.textPrimary,
                )
            }
        }
    }
}
