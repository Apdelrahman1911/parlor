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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.home_coming_soon_state
import com.parlor.app.resources.home_continue_label
import com.parlor.app.resources.home_eyebrow
import com.parlor.app.resources.home_future_game_title
import com.parlor.app.resources.home_games_label
import com.parlor.app.resources.home_resume_tile_description
import com.parlor.app.resources.home_resume_tile_subtitle
import com.parlor.app.resources.home_resume_tile_title
import com.parlor.app.resources.home_resume_multiplayer_description
import com.parlor.app.resources.home_resume_multiplayer_subtitle
import com.parlor.app.resources.home_resume_multiplayer_title
import com.parlor.app.resources.home_settings_description
import com.parlor.app.resources.home_subtitle
import com.parlor.app.resources.settings_title
import com.parlor.app.shell.game.GameCatalogPresentation
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Home — game-first. One scrolling surface, no tabs.
 *
 * Layout, top to bottom:
 *  1. **Top bar.** PARLOR eyebrow + one-line orientation subtitle, with a
 *     SETTINGS text-link on the trailing edge.
 *  2. **Continue** (only when [unfinishedSessions] is non-empty). Tappable
 *     tiles that drop the user back into an in-progress investigation.
 *  3. **Games.** Hero cards supplied by every installed game binding,
 *     followed by dimmed "Coming soon" placeholders. Tapping a game card
 *     invokes [onGameSelected]; the parent routes to the game setup screen
 *     (Solo / Pass-and-Play / Host / Join).
 *
 * Multiplayer (host/join) is **not** surfaced here anymore — it's a choice
 * the user makes *after* picking a game, alongside Solo and Pass-and-Play.
 * That keeps the home page about the games themselves and lets the setup
 * screen own the "how" decision.
 */
@Composable
internal fun HomeScreen(
    games: List<GameShellBinding>,
    onGameSelected: (GameId) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    unfinishedSessions: List<SessionId> = emptyList(),
    onResume: (SessionId) -> Unit = {},
    hasResumableMultiplayer: Boolean = false,
    onResumeMultiplayer: () -> Unit = {},
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ParlorTheme.spacing.xl,
                    end = ParlorTheme.spacing.xl,
                    top = ParlorTheme.spacing.xl,
                    bottom = ParlorTheme.spacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
        ) {
            HomeTopBar(onSettings = onSettings)

            if (unfinishedSessions.isNotEmpty() || hasResumableMultiplayer) {
                ContinueSection(
                    sessions = unfinishedSessions,
                    onResume = onResume,
                    hasResumableMultiplayer = hasResumableMultiplayer,
                    onResumeMultiplayer = onResumeMultiplayer,
                )
            }

            GamesSection(
                games = games,
                onGameSelected = onGameSelected,
            )
        }
    }
}

// ============================================================================ Top bar ==

@Composable
private fun HomeTopBar(onSettings: () -> Unit) {
    val settingsLabel = stringResource(Res.string.settings_title)
    val settingsDescription = stringResource(Res.string.home_settings_description)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            EyebrowLabel(text = stringResource(Res.string.home_eyebrow))
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.xs))
            Text(
                text = stringResource(Res.string.home_subtitle),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )
        }
        Text(
            text = settingsLabel.uppercase(),
            style = ParlorTheme.typography.labelSmall,
            color = ParlorTheme.colors.accentEmber,
            modifier = Modifier
                .heightIn(min = ParlorTheme.spacing.xxl)
                .semantics { contentDescription = settingsDescription }
                .clickable(role = Role.Button, onClick = onSettings)
                .padding(ParlorTheme.spacing.s),
        )
    }
}

// =========================================================================== Continue ==

@Composable
private fun ContinueSection(
    sessions: List<SessionId>,
    onResume: (SessionId) -> Unit,
    hasResumableMultiplayer: Boolean,
    onResumeMultiplayer: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
        EyebrowLabel(text = stringResource(Res.string.home_continue_label), accent = false)
        sessions.forEach { id ->
            ResumeTile(
                title = stringResource(Res.string.home_resume_tile_title),
                subtitle = stringResource(Res.string.home_resume_tile_subtitle),
                contentDescription = stringResource(Res.string.home_resume_tile_description),
                onTap = { onResume(id) },
            )
        }
        if (hasResumableMultiplayer) {
            ResumeTile(
                title = stringResource(Res.string.home_resume_multiplayer_title),
                subtitle = stringResource(Res.string.home_resume_multiplayer_subtitle),
                contentDescription = stringResource(
                    Res.string.home_resume_multiplayer_description,
                ),
                onTap = onResumeMultiplayer,
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
            .clickable(role = Role.Button, onClick = onTap),
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

// =============================================================================== Games ==

@Composable
private fun GamesSection(
    games: List<GameShellBinding>,
    onGameSelected: (GameId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
        EyebrowLabel(text = stringResource(Res.string.home_games_label), accent = false)
        for (game in games) {
            GameHeroCard(
                presentation = game.catalogPresentation(),
                onOpen = { onGameSelected(game.definition.id) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            ComingSoonTile(
                title = "${stringResource(Res.string.home_future_game_title)} ${games.size + 1}",
                state = stringResource(Res.string.home_coming_soon_state),
                modifier = Modifier.weight(1f),
            )
            ComingSoonTile(
                title = "${stringResource(Res.string.home_future_game_title)} ${games.size + 2}",
                state = stringResource(Res.string.home_coming_soon_state),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GameHeroCard(
    presentation: GameCatalogPresentation,
    onOpen: () -> Unit,
) {
    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
        hero = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                text = presentation.title,
                style = ParlorTheme.typography.displayHero,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = presentation.subtitle,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.accentEmber,
            )
            Text(
                text = presentation.tagline,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
            ParlorButton(
                label = presentation.openLabel,
                contentDescription = presentation.openContentDescription,
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ComingSoonTile(
    title: String,
    state: String,
    modifier: Modifier = Modifier,
) {
    ParlorCard(
        modifier = modifier,
        cornerRadius = ParlorTheme.radii.card,
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = ParlorTheme.typography.headingMedium,
                    color = ParlorTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(ParlorTheme.spacing.xs))
                Text(
                    text = state.uppercase(),
                    style = ParlorTheme.typography.labelSmall,
                    color = ParlorTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
