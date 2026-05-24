package com.parlor.app.shell.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.home_all_games_label
import com.parlor.app.resources.home_begin_investigation
import com.parlor.app.resources.home_begin_investigation_description
import com.parlor.app.resources.home_coming_soon_state
import com.parlor.app.resources.home_eyebrow
import com.parlor.app.resources.home_featured_state
import com.parlor.app.resources.home_future_game_title
import com.parlor.app.resources.home_host
import com.parlor.app.resources.home_host_description
import com.parlor.app.resources.home_join
import com.parlor.app.resources.home_join_description
import com.parlor.app.resources.home_library_card_action
import com.parlor.app.resources.home_library_card_description
import com.parlor.app.resources.home_library_card_eyebrow
import com.parlor.app.resources.home_library_card_subtitle
import com.parlor.app.resources.home_library_card_title
import com.parlor.app.resources.home_multiplayer_disabled
import com.parlor.app.resources.home_multiplayer_eyebrow
import com.parlor.app.resources.home_resume_section_label
import com.parlor.app.resources.home_resume_tile_description
import com.parlor.app.resources.home_resume_tile_subtitle
import com.parlor.app.resources.home_resume_tile_title
import com.parlor.app.resources.home_tonights_game_label
import com.parlor.app.resources.home_whodunit_subtitle
import com.parlor.app.resources.home_whodunit_tagline
import com.parlor.app.resources.home_whodunit_title
import com.parlor.app.resources.settings_open
import com.parlor.app.resources.settings_title
import com.parlor.core.ids.SessionId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Parlor Home — atmosphere from [HeroBackdrop] (ember bloom, candle flicker,
 * vignette), content shows the *Tonight's Game* card and a small *All Games*
 * grid with future tiles greyed out. A small Settings entry sits in the top
 * row.
 *
 * Phase 6.2: when [unfinishedSessions] is non-empty, a *Continue Where You
 * Left Off* section is rendered above *Tonight's Game*, with one tile per
 * unfinished session. Tapping a tile calls [onResume] with that session id.
 */
@Composable
fun HomeScreen(
    onTileSelected: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    unfinishedSessions: List<SessionId> = emptyList(),
    onResume: (SessionId) -> Unit = {},
    multiplayerEnabled: Boolean = false,
    onHost: () -> Unit = {},
    onJoin: () -> Unit = {},
    onBrowseCases: () -> Unit = {},
) {
    val settingsLabel = stringResource(Res.string.settings_title)
    val settingsDescription = stringResource(Res.string.settings_open)

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                EyebrowLabel(text = stringResource(Res.string.home_eyebrow))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = settingsLabel.uppercase(),
                    style = ParlorTheme.typography.labelSmall,
                    color = ParlorTheme.colors.accentEmber,
                    modifier = Modifier
                        .semantics { contentDescription = settingsDescription }
                        .clickable(onClick = onSettings)
                        .padding(ParlorTheme.spacing.s),
                )
            }
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.xs))

            if (unfinishedSessions.isNotEmpty()) {
                EyebrowLabel(text = stringResource(Res.string.home_resume_section_label))
                ResumeSection(
                    sessions = unfinishedSessions,
                    title = stringResource(Res.string.home_resume_tile_title),
                    subtitle = stringResource(Res.string.home_resume_tile_subtitle),
                    contentDescription = stringResource(Res.string.home_resume_tile_description),
                    onResume = onResume,
                )
            }

            TonightsGameCard(
                eyebrow = stringResource(Res.string.home_library_card_eyebrow),
                title = stringResource(Res.string.home_library_card_title),
                subtitle = stringResource(Res.string.home_whodunit_title),
                tagline = stringResource(Res.string.home_library_card_subtitle),
                beginLabel = stringResource(Res.string.home_library_card_action),
                beginContentDescription = stringResource(Res.string.home_library_card_description),
                onBegin = onBrowseCases,
            )

            // Phase 8 entry point. The section renders regardless of build
            // flavour so testers can see whether multiplayer is wired in —
            // when disabled, the buttons are present but show a clear hint.
            EyebrowLabel(text = stringResource(Res.string.home_multiplayer_eyebrow))
            if (multiplayerEnabled) {
                ParlorButton(
                    label = stringResource(Res.string.home_host),
                    contentDescription = stringResource(Res.string.home_host_description),
                    onClick = onHost,
                    modifier = Modifier.fillMaxWidth(),
                )
                ParlorButton(
                    label = stringResource(Res.string.home_join),
                    contentDescription = stringResource(Res.string.home_join_description),
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(Res.string.home_multiplayer_disabled),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                )
            }

            EyebrowLabel(text = stringResource(Res.string.home_all_games_label))
            AllGamesGrid(
                whodunitTitle = stringResource(Res.string.home_whodunit_title),
                futurePlaceholderTitle = stringResource(Res.string.home_future_game_title),
                featuredState = stringResource(Res.string.home_featured_state),
                comingSoonState = stringResource(Res.string.home_coming_soon_state),
            )
        }
    }
}

@Composable
private fun ResumeSection(
    sessions: List<SessionId>,
    title: String,
    subtitle: String,
    contentDescription: String,
    onResume: (SessionId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
        sessions.forEach { sessionId ->
            ResumeTile(
                title = title,
                subtitle = subtitle,
                contentDescription = contentDescription,
                onTap = { onResume(sessionId) },
            )
        }
    }
}

@Composable
private fun ResumeTile(
    title: String,
    subtitle: String,
    contentDescription: String,
    onTap: () -> Unit,
) {
    ParlorCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onTap),
        elevation = ParlorTheme.elevation.medium,
        cornerRadius = ParlorTheme.radii.card,
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
            Text(
                text = title,
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
            )
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
    eyebrow: String,
    title: String,
    subtitle: String,
    tagline: String,
    beginLabel: String,
    beginContentDescription: String,
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
                text = eyebrow,
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
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
                label = beginLabel,
                contentDescription = beginContentDescription,
                onClick = onBegin,
            )
        }
    }
}

@Composable
private fun AllGamesGrid(
    whodunitTitle: String,
    futurePlaceholderTitle: String,
    featuredState: String,
    comingSoonState: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
        GameTile(title = whodunitTitle, state = featuredState, isActive = true)
        GameTile(title = "$futurePlaceholderTitle 2", state = comingSoonState, isActive = false)
        GameTile(title = "$futurePlaceholderTitle 3", state = comingSoonState, isActive = false)
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
