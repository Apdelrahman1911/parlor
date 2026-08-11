package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_player_count_case_cap_format
import com.parlor.games.whodunit.resources.setup_player_count_eyebrow
import com.parlor.games.whodunit.resources.setup_player_count_headline
import org.jetbrains.compose.resources.stringResource

/**
 * Player Count — renders the module's full range. Per ARCHITECTURE.md §1.5,
 * the display strategy (show-and-disable vs hide-unsupported) is a product
 * decision. Default is **hide-unsupported** for the launch UX of *The Last
 * Dinner* (cleaner; only 4–6 are shown).
 */
@Composable
fun PlayerCountScreen(
    moduleRange: IntRange,
    caseSupportedRange: IntRange,
    displayStrategy: PlayerCountDisplayStrategy = PlayerCountDisplayStrategy.HideUnsupported,
    onCountSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveRange = when (displayStrategy) {
        PlayerCountDisplayStrategy.HideUnsupported -> caseSupportedRange
        PlayerCountDisplayStrategy.ShowAndDisable -> moduleRange
    }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.setup_player_count_eyebrow),
                accent = false,
            )
            Text(
                text = stringResource(Res.string.setup_player_count_headline),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                effectiveRange.forEach { count ->
                    val isSupportedByCase = count in caseSupportedRange
                    PlayerCountSlot(
                        count = count,
                        enabled = isSupportedByCase,
                        onClick = { if (isSupportedByCase) onCountSelected(count) },
                    )
                }
            }

            if (displayStrategy == PlayerCountDisplayStrategy.ShowAndDisable &&
                caseSupportedRange.last < moduleRange.last
            ) {
                Text(
                    text = stringResource(
                        Res.string.setup_player_count_case_cap_format,
                        caseSupportedRange.last,
                    ),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PlayerCountSlot(
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = ParlorTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ParlorTheme.iconSize.hero)
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .background(
                if (enabled) colors.surfaceElevated else colors.surfaceInset,
            )
            .border(
                width = ParlorTheme.borders.hairline,
                color = if (enabled) colors.borderElevated else colors.semanticMuted,
                shape = RoundedCornerShape(ParlorTheme.radii.card),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = count.toString(),
            style = ParlorTheme.typography.displayLarge,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}

enum class PlayerCountDisplayStrategy {
    HideUnsupported,
    ShowAndDisable,
}
