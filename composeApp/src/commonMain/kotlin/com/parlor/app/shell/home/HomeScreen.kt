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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.parlor.app.resources.home_play_section_host_subtitle
import com.parlor.app.resources.home_play_section_host_title
import com.parlor.app.resources.home_play_section_join_subtitle
import com.parlor.app.resources.home_play_section_join_title
import com.parlor.app.resources.home_play_section_resume_eyebrow
import com.parlor.app.resources.home_play_section_session_eyebrow
import com.parlor.app.resources.home_resume_tile_description
import com.parlor.app.resources.home_resume_tile_subtitle
import com.parlor.app.resources.home_resume_tile_title
import com.parlor.app.resources.home_tab_library
import com.parlor.app.resources.home_tab_library_description
import com.parlor.app.resources.home_tab_play
import com.parlor.app.resources.home_tab_play_description
import com.parlor.app.resources.home_whodunit_title
import com.parlor.app.resources.settings_open
import com.parlor.app.resources.settings_title
import com.parlor.core.ids.SessionId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorBottomTab
import com.parlor.designsystem.components.ParlorBottomTabBar
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Home — split into two tabs for clarity.
 *
 *  - **Library** (default): "Tonight's Game" hero card + All Games grid.
 *    For browsing and starting solo or hosted sessions.
 *  - **Play**: resume in-progress session (if any) + Host card + Join
 *    card. For acting on a real-time session.
 *
 * Settings remains a top-right text action above the tab content.
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
    val libraryLabel = stringResource(Res.string.home_tab_library)
    val libraryDescription = stringResource(Res.string.home_tab_library_description)
    val playLabel = stringResource(Res.string.home_tab_play)
    val playDescription = stringResource(Res.string.home_tab_play_description)

    var selectedTab by rememberSaveable { mutableStateOf(0) }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar: eyebrow + settings.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = ParlorTheme.spacing.xl,
                            end = ParlorTheme.spacing.xl,
                            top = ParlorTheme.spacing.xl,
                        ),
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

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> LibraryTab(
                            onBrowseCases = onBrowseCases,
                            onTileSelected = onTileSelected,
                        )
                        else -> PlayTab(
                            multiplayerEnabled = multiplayerEnabled,
                            unfinishedSessions = unfinishedSessions,
                            onResume = onResume,
                            onHost = onHost,
                            onJoin = onJoin,
                        )
                    }
                }

                ParlorBottomTabBar(
                    tabs = listOf(
                        ParlorBottomTab(label = libraryLabel, contentDescription = libraryDescription),
                        ParlorBottomTab(label = playLabel, contentDescription = playDescription),
                    ),
                    selectedIndex = selectedTab,
                    onTabSelected = { selectedTab = it },
                )
            }
        }
    }
}

// =================================================================== Library tab ==

@Composable
private fun LibraryTab(
    onBrowseCases: () -> Unit,
    onTileSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = ParlorTheme.spacing.xl,
                end = ParlorTheme.spacing.xl,
                top = ParlorTheme.spacing.l,
                bottom = ParlorTheme.spacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
    ) {
        TonightsGameCard(
            eyebrow = stringResource(Res.string.home_library_card_eyebrow),
            title = stringResource(Res.string.home_library_card_title),
            subtitle = stringResource(Res.string.home_whodunit_title),
            tagline = stringResource(Res.string.home_library_card_subtitle),
            beginLabel = stringResource(Res.string.home_library_card_action),
            beginContentDescription = stringResource(Res.string.home_library_card_description),
            onBegin = onBrowseCases,
        )

        EyebrowLabel(text = stringResource(Res.string.home_all_games_label), accent = false)
        AllGamesGrid(
            whodunitTitle = stringResource(Res.string.home_whodunit_title),
            futurePlaceholderTitle = stringResource(Res.string.home_future_game_title),
            featuredState = stringResource(Res.string.home_featured_state),
            comingSoonState = stringResource(Res.string.home_coming_soon_state),
        )
    }
}

// =================================================================== Play tab ==

@Composable
private fun PlayTab(
    multiplayerEnabled: Boolean,
    unfinishedSessions: List<SessionId>,
    onResume: (SessionId) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = ParlorTheme.spacing.xl,
                end = ParlorTheme.spacing.xl,
                top = ParlorTheme.spacing.l,
                bottom = ParlorTheme.spacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
    ) {
        if (unfinishedSessions.isNotEmpty()) {
            EyebrowLabel(text = stringResource(Res.string.home_play_section_resume_eyebrow), accent = false)
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                unfinishedSessions.forEach { sessionId ->
                    ResumeTile(
                        title = stringResource(Res.string.home_resume_tile_title),
                        subtitle = stringResource(Res.string.home_resume_tile_subtitle),
                        contentDescription = stringResource(Res.string.home_resume_tile_description),
                        onTap = { onResume(sessionId) },
                    )
                }
            }
        }

        EyebrowLabel(text = stringResource(Res.string.home_play_section_session_eyebrow), accent = false)

        SessionActionCard(
            title = stringResource(Res.string.home_play_section_host_title),
            subtitle = stringResource(Res.string.home_play_section_host_subtitle),
            actionLabel = stringResource(Res.string.home_host),
            actionDescription = stringResource(Res.string.home_host_description),
            onAction = onHost,
            enabled = multiplayerEnabled,
            disabledHint = stringResource(Res.string.home_multiplayer_disabled).takeUnless { multiplayerEnabled },
        )

        SessionActionCard(
            title = stringResource(Res.string.home_play_section_join_title),
            subtitle = stringResource(Res.string.home_play_section_join_subtitle),
            actionLabel = stringResource(Res.string.home_join),
            actionDescription = stringResource(Res.string.home_join_description),
            onAction = onJoin,
            enabled = multiplayerEnabled,
            disabledHint = null, // host card already explains the state
            secondary = true,
        )
    }
}

// =================================================================== Shared cards ==

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
private fun SessionActionCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    actionDescription: String,
    onAction: () -> Unit,
    enabled: Boolean,
    disabledHint: String?,
    secondary: Boolean = false,
) {
    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
        hero = !secondary && enabled,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
            )
            if (disabledHint != null) {
                Text(
                    text = disabledHint,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                )
            }
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
            ParlorButton(
                label = actionLabel,
                contentDescription = actionDescription,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                variant = if (secondary) ParlorButtonVariant.Secondary else ParlorButtonVariant.Primary,
                enabled = enabled,
            )
        }
    }
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
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
        hero = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            EyebrowLabel(text = eyebrow, accent = false)
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
                modifier = Modifier.fillMaxWidth(),
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
        GameTile(title = whodunitTitle, state = featuredState, isActive = true, modifier = Modifier.weight(1f))
        GameTile(title = "$futurePlaceholderTitle 2", state = comingSoonState, isActive = false, modifier = Modifier.weight(1f))
        GameTile(title = "$futurePlaceholderTitle 3", state = comingSoonState, isActive = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GameTile(
    title: String,
    state: String,
    isActive: Boolean,
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
