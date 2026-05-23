package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme

/**
 * The Parlor Home screen. The atmosphere comes from [HeroBackdrop] (ember bloom,
 * candle flicker, vignette). The content shows the *Tonight's Game* card and a
 * small placeholder grid for *All Games* (other tiles greyed and labeled
 * "Coming soon" — telegraphs the platform vision).
 *
 * Phase 3 wires real case data from [com.parlor.content.repository.CaseRepository].
 */
@Composable
fun HomeScreen(
    onTileSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
        ) {
            EyebrowLabel("Parlor")
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.xs))

            // Featured "Tonight's Game" card.
            TonightsGameCard(
                title = "Whodunit",
                subtitle = "The Last Dinner",
                tagline = "A country-manor murder mystery. 4–6 players. 25–35 min.",
                onBegin = { onTileSelected("whodunit") },
            )

            EyebrowLabel("All Games")
            AllGamesGrid()
        }
    }
}

@Composable
private fun EyebrowLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = ParlorTheme.typography.labelSmall,
        color = ParlorTheme.colors.textSecondary,
    )
}

@Composable
private fun TonightsGameCard(
    title: String,
    subtitle: String,
    tagline: String,
    onBegin: () -> Unit,
) {
    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = ParlorTheme.elevation.dramatic,
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayHero,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.accentEmber,
            )
            Text(
                text = tagline,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
            ParlorButton(
                label = "Begin Investigation",
                contentDescription = "Begin investigating The Last Dinner.",
                onClick = onBegin,
            )
        }
    }
}

@Composable
private fun AllGamesGrid() {
    Row(horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
        GameTile(title = "Whodunit", state = "Featured", isActive = true)
        GameTile(title = "Game #2", state = "Coming soon", isActive = false)
        GameTile(title = "Game #3", state = "Coming soon", isActive = false)
    }
}

@Composable
private fun GameTile(title: String, state: String, isActive: Boolean) {
    ParlorCard(
        modifier = Modifier.fillMaxWidth(0.32f),
        elevation = if (isActive) ParlorTheme.elevation.medium else ParlorTheme.elevation.low,
        cornerRadius = ParlorTheme.radii.card,
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = ParlorTheme.typography.headingLarge,
                    color = if (isActive) ParlorTheme.colors.textPrimary else ParlorTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(ParlorTheme.spacing.xs))
                Text(
                    text = state.uppercase(),
                    style = ParlorTheme.typography.labelSmall,
                    color = if (isActive) ParlorTheme.colors.accentEmber else ParlorTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
