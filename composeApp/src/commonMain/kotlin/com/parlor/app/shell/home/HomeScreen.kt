package com.parlor.app.shell.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.app_name
import com.parlor.app.resources.home_continue_label
import com.parlor.app.resources.home_eyebrow
import com.parlor.app.resources.home_game_kicker_format
import com.parlor.app.resources.home_games_count
import com.parlor.app.resources.home_games_label
import com.parlor.app.resources.home_local_lan_meta
import com.parlor.app.resources.home_local_meta
import com.parlor.app.resources.home_players_exact_format
import com.parlor.app.resources.home_players_range_format
import com.parlor.app.resources.home_recovery_checking
import com.parlor.app.resources.home_recovery_retry
import com.parlor.app.resources.home_recovery_retry_description
import com.parlor.app.resources.home_recovery_unavailable_body
import com.parlor.app.resources.home_recovery_unavailable_title
import com.parlor.app.resources.home_resume_multiplayer_description
import com.parlor.app.resources.home_resume_multiplayer_subtitle
import com.parlor.app.resources.home_resume_multiplayer_title
import com.parlor.app.resources.home_resume_tile_description
import com.parlor.app.resources.home_resume_tile_game_description
import com.parlor.app.resources.home_resume_tile_game_title
import com.parlor.app.resources.home_resume_tile_position
import com.parlor.app.resources.home_resume_tile_subtitle
import com.parlor.app.resources.home_resume_tile_title
import com.parlor.app.resources.home_resume_tile_unknown_description
import com.parlor.app.resources.home_saved_on_device
import com.parlor.app.resources.home_subtitle
import com.parlor.app.resources.home_title
import com.parlor.app.shell.game.GameCatalogPresentation
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorMark
import com.parlor.designsystem.components.parlorSafeContentPaddingValues
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorAccent
import com.parlor.designsystem.theme.ParlorAccentScope
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

/** Game-first library. Recovery stays secondary and exposes metadata only. */
@Composable
internal fun HomeScreen(
    games: List<GameShellBinding>,
    onGameSelected: (GameId) -> Unit,
    modifier: Modifier = Modifier,
    unfinishedSessions: List<LocalRecoveryEntry> = emptyList(),
    onResume: (SessionId) -> Unit = {},
    hasResumableMultiplayer: Boolean = false,
    onResumeMultiplayer: () -> Unit = {},
    recoveryLoading: Boolean = false,
    recoveryUnavailable: Boolean = false,
    onRetryRecovery: () -> Unit = {},
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = parlorSafeContentPaddingValues(
                horizontal = ParlorTheme.spacing.l,
                top = ParlorTheme.spacing.l,
                bottom = ParlorTheme.spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            item(key = "home-top-bar") { HomeTopBar() }
            item(key = "home-lead") { HomeLead() }

            when {
                recoveryLoading -> item(key = "recovery-loading") { RecoveryLoadingCard() }
                recoveryUnavailable -> item(key = "recovery-unavailable") {
                    RecoveryUnavailableCard(onRetryRecovery)
                }
            }

            if (unfinishedSessions.isNotEmpty() || hasResumableMultiplayer) {
                item(key = "continue-label") {
                    SectionHeading(
                        label = stringResource(Res.string.home_continue_label),
                        detail = stringResource(Res.string.home_saved_on_device),
                    )
                }
                itemsIndexed(
                    items = unfinishedSessions,
                    key = { _, entry -> "local:${entry.sessionId.raw}" },
                ) { index, entry ->
                    LocalResumeTile(
                        entry = entry,
                        position = index + 1,
                        total = unfinishedSessions.size,
                        games = games,
                        onResume = onResume,
                    )
                }
                if (hasResumableMultiplayer) {
                    item(key = "multiplayer-resume") {
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

            item(key = "games-label") {
                SectionHeading(
                    label = stringResource(Res.string.home_games_label),
                    detail = stringResource(Res.string.home_games_count, games.size),
                )
            }
            items(
                items = games,
                key = { game -> "game:${game.definition.id.raw}" },
            ) { game ->
                val presentation = game.catalogPresentation()
                GameHeroCard(
                    presentation = presentation,
                    supportedPlayerCounts = game.multiplayerContract
                        ?.supportedPlayerCounts
                        ?: game.definition.supportedPlayerCounts,
                    supportsLan = game.capabilities.supports(GameEntryMode.Host) ||
                        game.capabilities.supports(GameEntryMode.Join),
                    position = games.indexOf(game) + 1,
                    onOpen = { onGameSelected(game.definition.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ParlorMark(modifier = Modifier.size(ParlorTheme.spacing.xxl))
            Text(
                text = stringResource(Res.string.app_name),
                style = ParlorTheme.typography.headingMedium,
                color = ParlorTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun HomeLead() {
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
        EyebrowLabel(text = stringResource(Res.string.home_eyebrow))
        Text(
            text = stringResource(Res.string.home_title),
            style = ParlorTheme.typography.displayLarge,
            color = ParlorTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.home_subtitle),
            style = ParlorTheme.typography.bodyMedium,
            color = ParlorTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SectionHeading(label: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EyebrowLabel(
            text = label,
            accent = false,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = ParlorTheme.typography.bodySmall,
            color = ParlorTheme.colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecoveryLoadingCard() {
    ParlorCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Text(
            text = stringResource(Res.string.home_recovery_checking),
            style = ParlorTheme.typography.bodyMedium,
            color = ParlorTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun RecoveryUnavailableCard(onRetry: () -> Unit) {
    ParlorCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                text = stringResource(Res.string.home_recovery_unavailable_title),
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.home_recovery_unavailable_body),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
            )
            ParlorButton(
                label = stringResource(Res.string.home_recovery_retry),
                contentDescription = stringResource(Res.string.home_recovery_retry_description),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LocalResumeTile(
    entry: LocalRecoveryEntry,
    position: Int,
    total: Int,
    games: List<GameShellBinding>,
    onResume: (SessionId) -> Unit,
) {
    val binding = entry.gameId?.let { gameId ->
        games.firstOrNull { game -> game.definition.id == gameId }
    }
    val gameTitle = binding?.catalogPresentation()?.title
    val title = if (gameTitle == null) {
        stringResource(Res.string.home_resume_tile_title)
    } else {
        stringResource(Res.string.home_resume_tile_game_title, gameTitle)
    }
    val contentDescription = if (gameTitle == null) {
        if (total == 1) {
            stringResource(Res.string.home_resume_tile_description)
        } else {
            stringResource(Res.string.home_resume_tile_unknown_description, position, total)
        }
    } else {
        stringResource(
            Res.string.home_resume_tile_game_description,
            gameTitle,
            position,
            total,
        )
    }
    ResumeTile(
        title = title,
        subtitle = if (total == 1) {
            stringResource(Res.string.home_resume_tile_subtitle)
        } else {
            stringResource(Res.string.home_resume_tile_position, position, total)
        },
        contentDescription = contentDescription,
        onTap = { onResume(entry.sessionId) },
    )
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
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(ParlorTheme.spacing.xs, ParlorTheme.spacing.xxl)
                    .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                    .background(ParlorTheme.colors.accentEmber),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
            ) {
                Text(
                    text = title,
                    style = ParlorTheme.typography.headingMedium,
                    color = ParlorTheme.colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = ParlorTheme.typography.bodySmall,
                    color = ParlorTheme.colors.textSecondary,
                )
            }
            DirectionalArrow()
        }
    }
}

@Composable
private fun GameHeroCard(
    presentation: GameCatalogPresentation,
    supportedPlayerCounts: IntRange,
    supportsLan: Boolean,
    position: Int,
    onOpen: () -> Unit,
) {
    ParlorAccentScope(presentation.accent) {
        ParlorCard(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = presentation.openContentDescription }
                .clickable(role = Role.Button, onClick = onOpen),
            cornerRadius = ParlorTheme.radii.elevated,
            contentPadding = ParlorTheme.spacing.l,
            hero = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    EyebrowLabel(
                        text = stringResource(
                            Res.string.home_game_kicker_format,
                            presentation.kicker,
                            position,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    GameCardArt(presentation.accent)
                }
                Text(
                    text = presentation.title,
                    style = ParlorTheme.typography.displayLarge,
                    color = ParlorTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = presentation.subtitle,
                    style = ParlorTheme.typography.headingMedium,
                    color = ParlorTheme.colors.accentEmber,
                )
                Text(
                    text = presentation.tagline,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                ) {
                    MetaPill(
                        text = playerRangeLabel(supportedPlayerCounts),
                        modifier = Modifier.weight(1f),
                    )
                    MetaPill(
                        text = stringResource(
                            if (supportsLan) {
                                Res.string.home_local_lan_meta
                            } else {
                                Res.string.home_local_meta
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = presentation.openLabel,
                        style = ParlorTheme.typography.labelLarge,
                        color = ParlorTheme.colors.accentEmber,
                    )
                    DirectionalArrow(accent = true)
                }
            }
        }
    }
}

@Composable
private fun playerRangeLabel(range: IntRange): String =
    if (range.first == range.last) {
        stringResource(Res.string.home_players_exact_format, range.first)
    } else {
        stringResource(Res.string.home_players_range_format, range.first, range.last)
    }

@Composable
private fun MetaPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = ParlorTheme.typography.bodySmall,
        color = ParlorTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
            .background(ParlorTheme.colors.surfaceHigher)
            .padding(horizontal = ParlorTheme.spacing.s, vertical = ParlorTheme.spacing.xs),
    )
}

@Composable
private fun DirectionalArrow(accent: Boolean = false) {
    Box(
        modifier = Modifier
            .size(ParlorTheme.spacing.xxl)
            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
            .background(
                if (accent) {
                    ParlorTheme.colors.accentEmber.copy(alpha = CARD_ACCENT_ALPHA)
                } else {
                    ParlorTheme.colors.surfaceHigher
                },
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ParlorIcons.Forward,
            contentDescription = null,
            tint = if (accent) {
                ParlorTheme.colors.accentEmber
            } else {
                ParlorTheme.colors.textSecondary
            },
            modifier = Modifier.size(ParlorTheme.iconSize.m),
        )
    }
}

@Composable
private fun GameCardArt(accent: ParlorAccent) {
    val colors = ParlorTheme.colors
    Canvas(
        modifier = Modifier
            .size(ParlorTheme.spacing.xxxl)
            .clearAndSetSemantics { },
    ) {
        drawCircle(colors.accentEmber.copy(alpha = CARD_ART_HALO_ALPHA))
        if (accent == ParlorAccent.Crimson) {
            drawCircle(
                color = colors.accentEmber,
                radius = size.minDimension * 0.28f,
            )
            drawCircle(
                color = colors.surfaceHero,
                radius = size.minDimension * 0.24f,
                center = center.copy(
                    x = center.x + size.width * 0.12f,
                    y = center.y - size.height * 0.08f,
                ),
            )
        } else {
            val stroke = size.minDimension * CARD_ART_STROKE_RATIO
            drawCircle(
                color = colors.accentEmber,
                radius = size.minDimension * 0.27f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            drawLine(
                color = colors.accentEmber,
                start = center.copy(y = center.y - size.height * 0.22f),
                end = center.copy(y = center.y + size.height * 0.22f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.accentEmber,
                start = center.copy(x = center.x - size.width * 0.22f),
                end = center.copy(x = center.x + size.width * 0.22f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val CARD_ACCENT_ALPHA = 0.14f
private const val CARD_ART_HALO_ALPHA = 0.10f
private const val CARD_ART_STROKE_RATIO = 0.035f
