package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Rules briefing carousel — four short cards. Phase 4 uses a step pager. Phase
 * 6 can polish the inter-card motion with theatrical transitions.
 */
@Composable
fun RulesBriefingScreen(
    cardIndex: Int,
    onAdvance: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = listOf(
        "One of you is the killer. Even you don't know who, until you see your character.",
        "You may lie. You should lie. Especially if you're guilty.",
        "Each round, the app reveals a new clue. Discuss. Accuse. Suspect.",
        "Vote according to your chosen mode. Get it wrong, the killer wins.",
    )
    val safeIndex = cardIndex.coerceIn(0, cards.size - 1)

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "HOW TO PLAY",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )

            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = ParlorTheme.elevation.high,
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xxl,
            ) {
                Text(
                    text = cards[safeIndex],
                    style = ParlorTheme.typography.displayMedium,
                    color = ParlorTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    repeat(cards.size) { i ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == safeIndex) ParlorTheme.colors.accentEmber
                                    else ParlorTheme.colors.borderElevated,
                                ),
                        )
                    }
                }
                Spacer(modifier = Modifier.size(ParlorTheme.spacing.s))
                ParlorButton(
                    label = if (safeIndex == cards.size - 1) "Begin the Investigation" else "Continue",
                    contentDescription = if (safeIndex == cards.size - 1) {
                        "Begin the investigation."
                    } else {
                        "Advance to the next briefing card."
                    },
                    onClick = { onAdvance(safeIndex + 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
