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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_briefing_begin
import com.parlor.games.whodunit.resources.setup_briefing_begin_description
import com.parlor.games.whodunit.resources.setup_briefing_card_1
import com.parlor.games.whodunit.resources.setup_briefing_card_2
import com.parlor.games.whodunit.resources.setup_briefing_card_3
import com.parlor.games.whodunit.resources.setup_briefing_card_4
import com.parlor.games.whodunit.resources.setup_briefing_continue
import com.parlor.games.whodunit.resources.setup_briefing_continue_description
import com.parlor.games.whodunit.resources.setup_briefing_eyebrow
import org.jetbrains.compose.resources.stringResource

@Composable
fun RulesBriefingScreen(
    cardIndex: Int,
    onAdvance: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = listOf(
        stringResource(Res.string.setup_briefing_card_1),
        stringResource(Res.string.setup_briefing_card_2),
        stringResource(Res.string.setup_briefing_card_3),
        stringResource(Res.string.setup_briefing_card_4),
    )
    val safeIndex = cardIndex.coerceIn(0, cards.size - 1)
    val isLast = safeIndex == cards.size - 1
    val buttonLabel = if (isLast) {
        stringResource(Res.string.setup_briefing_begin)
    } else {
        stringResource(Res.string.setup_briefing_continue)
    }
    val buttonDescription = if (isLast) {
        stringResource(Res.string.setup_briefing_begin_description)
    } else {
        stringResource(Res.string.setup_briefing_continue_description)
    }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.setup_briefing_eyebrow),
                accent = false,
            )

            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
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
                                .size(ParlorTheme.iconSize.xxs)
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
                    label = buttonLabel,
                    contentDescription = buttonDescription,
                    onClick = { onAdvance(safeIndex + 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
