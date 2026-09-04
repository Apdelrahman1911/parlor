package com.parlor.app.shell.playmode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.parlor.app.resources.Res
import com.parlor.app.resources.playmode_passandplay_body
import com.parlor.app.resources.playmode_passandplay_choose_description
import com.parlor.app.resources.playmode_passandplay_meta
import com.parlor.app.resources.playmode_passandplay_meta_exact
import com.parlor.app.resources.playmode_passandplay_title
import com.parlor.app.resources.playmode_solo_body
import com.parlor.app.resources.playmode_solo_choose_description
import com.parlor.app.resources.playmode_solo_meta
import com.parlor.app.resources.playmode_solo_title
import com.parlor.app.resources.setup_back_description
import com.parlor.app.resources.setup_each_device_label
import com.parlor.app.resources.setup_eyebrow
import com.parlor.app.resources.setup_host_body
import com.parlor.app.resources.setup_host_choose_description
import com.parlor.app.resources.setup_host_meta
import com.parlor.app.resources.setup_host_title
import com.parlor.app.resources.setup_join_body
import com.parlor.app.resources.setup_join_choose_description
import com.parlor.app.resources.setup_join_meta
import com.parlor.app.resources.setup_join_title
import com.parlor.app.resources.setup_lan_note
import com.parlor.app.resources.setup_mode_unavailable
import com.parlor.app.resources.setup_multiplayer_disabled
import com.parlor.app.resources.setup_one_device_label
import com.parlor.app.resources.setup_solo_requires_exact
import com.parlor.app.resources.setup_solo_requires_range
import com.parlor.app.resources.setup_solo_unavailable_description_format
import com.parlor.app.resources.setup_solo_unavailable_title
import com.parlor.app.resources.setup_subtitle
import com.parlor.app.resources.setup_title
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellCapabilities
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.session.PlayMode
import org.jetbrains.compose.resources.stringResource

internal data class PlayModePickerAvailability(
    val solo: Boolean,
    val passAndPlay: Boolean,
    val host: Boolean,
    val join: Boolean,
)

internal data class PlayModePickerModel(
    val availability: PlayModePickerAvailability,
    val supportedPlayerCounts: IntRange,
) {
    init {
        require(!supportedPlayerCounts.isEmpty()) {
            "Play-mode player bounds must not be empty"
        }
    }
}

internal fun GameShellCapabilities.toPlayModePickerAvailability() = PlayModePickerAvailability(
    solo = supports(GameEntryMode.Solo),
    passAndPlay = supports(GameEntryMode.PassAndPlay),
    host = supports(GameEntryMode.Host),
    join = supports(GameEntryMode.Join),
)

internal fun GameShellCapabilities.toPlayModePickerModel(
    supportedPlayerCounts: IntRange,
) = PlayModePickerModel(
    availability = toPlayModePickerAvailability(),
    supportedPlayerCounts = supportedPlayerCounts,
)

/** Registry-driven topology picker; presentation never invents a new play mode. */
@Composable
internal fun PlayModePickerScreen(
    onModeSelected: (PlayMode) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    capabilities: GameShellCapabilities,
    supportedPlayerCounts: IntRange,
) {
    val model = capabilities.toPlayModePickerModel(supportedPlayerCounts)
    val availability = model.availability
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            ScreenHeader(
                title = stringResource(Res.string.setup_title),
                eyebrow = stringResource(Res.string.setup_eyebrow),
                subtitle = stringResource(Res.string.setup_subtitle),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.setup_back_description),
            )

            TopologyHeading(stringResource(Res.string.setup_one_device_label))
            if (availability.solo) {
                SoloOptionCard(
                    availability = availability,
                    playerCounts = model.supportedPlayerCounts,
                    onClick = { onModeSelected(PlayMode.Solo) },
                )
            }
            SetupOptionCard(
                icon = SetupIcon.PassAndPlay,
                title = stringResource(Res.string.playmode_passandplay_title),
                body = stringResource(Res.string.playmode_passandplay_body),
                meta = if (
                    model.supportedPlayerCounts.first == model.supportedPlayerCounts.last
                ) {
                    stringResource(
                        Res.string.playmode_passandplay_meta_exact,
                        model.supportedPlayerCounts.first,
                    )
                } else {
                    stringResource(
                        Res.string.playmode_passandplay_meta,
                        model.supportedPlayerCounts.first,
                        model.supportedPlayerCounts.last,
                    )
                },
                contentDescription = stringResource(
                    Res.string.playmode_passandplay_choose_description,
                ),
                onClick = { onModeSelected(PlayMode.PassAndPlay) },
                modifier = Modifier.fillMaxWidth(),
                enabled = availability.passAndPlay,
                disabledHint = stringResource(Res.string.setup_mode_unavailable),
                emphasized = true,
            )
            if (!availability.solo) {
                SoloOptionCard(
                    availability = availability,
                    playerCounts = model.supportedPlayerCounts,
                    onClick = { onModeSelected(PlayMode.Solo) },
                )
            }

            TopologyHeading(stringResource(Res.string.setup_each_device_label))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                verticalAlignment = Alignment.Top,
            ) {
                SetupOptionCard(
                    icon = SetupIcon.Host,
                    title = stringResource(Res.string.setup_host_title),
                    body = stringResource(Res.string.setup_host_body),
                    meta = stringResource(Res.string.setup_host_meta),
                    contentDescription = stringResource(Res.string.setup_host_choose_description),
                    onClick = onHost,
                    modifier = Modifier.weight(1f),
                    enabled = availability.host,
                    disabledHint = stringResource(Res.string.setup_multiplayer_disabled),
                )
                SetupOptionCard(
                    icon = SetupIcon.Join,
                    title = stringResource(Res.string.setup_join_title),
                    body = stringResource(Res.string.setup_join_body),
                    meta = stringResource(Res.string.setup_join_meta),
                    contentDescription = stringResource(Res.string.setup_join_choose_description),
                    onClick = onJoin,
                    modifier = Modifier.weight(1f),
                    enabled = availability.join,
                    disabledHint = stringResource(Res.string.setup_multiplayer_disabled),
                )
            }

            Text(
                text = stringResource(Res.string.setup_lan_note),
                style = ParlorTheme.typography.bodySmall,
                color = ParlorTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SoloOptionCard(
    availability: PlayModePickerAvailability,
    playerCounts: IntRange,
    onClick: () -> Unit,
) {
    val requirement = soloRequirement(playerCounts)
    SetupOptionCard(
        icon = SetupIcon.Solo,
        title = if (availability.solo) {
            stringResource(Res.string.playmode_solo_title)
        } else {
            stringResource(Res.string.setup_solo_unavailable_title)
        },
        body = if (availability.solo) {
            stringResource(Res.string.playmode_solo_body)
        } else {
            requirement
        },
        meta = stringResource(Res.string.playmode_solo_meta),
        contentDescription = if (availability.solo) {
            stringResource(Res.string.playmode_solo_choose_description)
        } else {
            stringResource(Res.string.setup_solo_unavailable_description_format, requirement)
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = availability.solo,
        disabledHint = stringResource(Res.string.setup_mode_unavailable),
        compact = !availability.solo,
    )
}

@Composable
private fun TopologyHeading(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EyebrowLabel(text = text, accent = false)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ParlorTheme.borders.hairline)
                .background(ParlorTheme.colors.borderSubtle),
        )
    }
}

@Composable
private fun SetupOptionCard(
    icon: SetupIcon,
    title: String,
    body: String,
    meta: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledHint: String,
    emphasized: Boolean = false,
    compact: Boolean = false,
) {
    ParlorCard(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        cornerRadius = ParlorTheme.radii.card,
        contentPadding = ParlorTheme.spacing.m,
        hero = emphasized && enabled,
    ) {
        if (compact) {
            CompactOptionContent(
                icon = icon,
                title = title,
                body = body,
                enabled = enabled,
            )
        } else {
            StandardOptionContent(
                icon = icon,
                title = title,
                body = body,
                meta = if (enabled) meta else disabledHint,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun StandardOptionContent(
    icon: SetupIcon,
    title: String,
    body: String,
    meta: String,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
        ModeGlyph(icon = icon, enabled = enabled)
        OptionTitle(title = title, enabled = enabled)
        OptionBody(body = body, enabled = enabled)
        Text(
            text = meta,
            style = ParlorTheme.typography.labelMedium,
            color = if (enabled) {
                ParlorTheme.colors.accentEmber
            } else {
                ParlorTheme.colors.textTertiary
            },
        )
    }
}

@Composable
private fun CompactOptionContent(
    icon: SetupIcon,
    title: String,
    body: String,
    enabled: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeGlyph(icon = icon, enabled = enabled)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
        ) {
            OptionTitle(title = title, enabled = enabled)
            OptionBody(body = body, enabled = enabled)
        }
    }
}

@Composable
private fun OptionTitle(title: String, enabled: Boolean) {
    Text(
        text = title,
        style = ParlorTheme.typography.headingMedium,
        color = if (enabled) {
            ParlorTheme.colors.textPrimary
        } else {
            ParlorTheme.colors.textTertiary
        },
    )
}

@Composable
private fun OptionBody(body: String, enabled: Boolean) {
    Text(
        text = body,
        style = ParlorTheme.typography.bodySmall,
        color = if (enabled) {
            ParlorTheme.colors.textSecondary
        } else {
            ParlorTheme.colors.textTertiary
        },
    )
}

@Composable
private fun ModeGlyph(icon: SetupIcon, enabled: Boolean) {
    val iconColor = if (enabled) {
        ParlorTheme.colors.accentEmber
    } else {
        ParlorTheme.colors.textTertiary
    }
    Box(
        modifier = Modifier
            .size(ParlorTheme.spacing.xxl)
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .background(
                if (enabled) {
                    ParlorTheme.colors.accentEmber.copy(alpha = MODE_GLYPH_ALPHA)
                } else {
                    ParlorTheme.colors.surfaceHigher
                },
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ParlorTheme.iconSize.l)) {
            val stroke = Stroke(width = size.minDimension * ICON_STROKE_RATIO)
            when (icon) {
                SetupIcon.Solo -> drawSoloGlyph(iconColor, stroke)
                SetupIcon.PassAndPlay -> drawPassAndPlayGlyph(iconColor, stroke)
                SetupIcon.Host -> drawHostGlyph(iconColor, stroke)
                SetupIcon.Join -> drawJoinGlyph(iconColor, stroke)
            }
        }
    }
}

private fun DrawScope.drawSoloGlyph(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * SOLO_PHONE_LEFT, size.height * PHONE_TOP),
        size = Size(size.width * SOLO_PHONE_WIDTH, size.height * PHONE_HEIGHT),
        cornerRadius = CornerRadius(size.width * PHONE_CORNER_RADIUS),
        style = stroke,
    )
    drawCircle(color, radius = size.width * PHONE_HOME_RADIUS)
}

private fun DrawScope.drawPassAndPlayGlyph(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * PASS_PHONE_LEFT, size.height * PHONE_TOP),
        size = Size(size.width * PASS_PHONE_WIDTH, size.height * PHONE_HEIGHT),
        cornerRadius = CornerRadius(size.width * PHONE_CORNER_RADIUS),
        style = stroke,
    )
    drawCircle(
        color = color,
        radius = size.width * PRIMARY_PERSON_RADIUS,
        center = Offset(size.width * PRIMARY_PERSON_X, size.height * PRIMARY_PERSON_Y),
    )
    drawCircle(
        color = color,
        radius = size.width * SECONDARY_PERSON_RADIUS,
        center = Offset(size.width * SECONDARY_PERSON_X, size.height * SECONDARY_PERSON_Y),
    )
}

private fun DrawScope.drawHostGlyph(color: Color, stroke: Stroke) {
    drawCircle(
        color = color,
        radius = size.width * HOST_NODE_RADIUS,
        center = Offset(size.width * HOST_NODE_X, size.height * HOST_NODE_Y),
    )
    drawArc(
        color = color,
        startAngle = HOST_ARC_START_ANGLE,
        sweepAngle = HOST_ARC_SWEEP_ANGLE,
        useCenter = false,
        topLeft = Offset(size.width * HOST_INNER_ARC_LEFT, size.height * HOST_INNER_ARC_TOP),
        size = Size(size.width * HOST_INNER_ARC_WIDTH, size.height * HOST_INNER_ARC_HEIGHT),
        style = stroke,
    )
    drawArc(
        color = color,
        startAngle = HOST_ARC_START_ANGLE,
        sweepAngle = HOST_ARC_SWEEP_ANGLE,
        useCenter = false,
        topLeft = Offset(size.width * HOST_OUTER_ARC_LEFT, size.height * HOST_OUTER_ARC_TOP),
        size = Size(size.width * HOST_OUTER_ARC_WIDTH, size.height * HOST_OUTER_ARC_HEIGHT),
        style = stroke,
    )
}

private fun DrawScope.drawJoinGlyph(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * JOIN_LARGE_DEVICE_LEFT, size.height * JOIN_LARGE_DEVICE_TOP),
        size = Size(size.width * JOIN_LARGE_DEVICE_WIDTH, size.height * JOIN_LARGE_DEVICE_HEIGHT),
        cornerRadius = CornerRadius(size.width * JOIN_LARGE_DEVICE_CORNER_RADIUS),
        style = stroke,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * JOIN_SMALL_DEVICE_LEFT, size.height * JOIN_SMALL_DEVICE_TOP),
        size = Size(size.width * JOIN_SMALL_DEVICE_WIDTH, size.height * JOIN_SMALL_DEVICE_HEIGHT),
        cornerRadius = CornerRadius(size.width * JOIN_SMALL_DEVICE_CORNER_RADIUS),
        style = stroke,
    )
}

@Composable
private fun soloRequirement(playerCounts: IntRange): String =
    if (playerCounts.first == playerCounts.last) {
        stringResource(
            Res.string.setup_solo_requires_exact,
            playerCounts.first,
        )
    } else {
        stringResource(
            Res.string.setup_solo_requires_range,
            playerCounts.first,
            playerCounts.last,
        )
    }

private const val MODE_GLYPH_ALPHA = 0.12f
private const val ICON_STROKE_RATIO = 0.07f
private const val PHONE_TOP = 0.08f
private const val PHONE_HEIGHT = 0.84f
private const val PHONE_CORNER_RADIUS = 0.10f
private const val PHONE_HOME_RADIUS = 0.05f
private const val SOLO_PHONE_LEFT = 0.22f
private const val SOLO_PHONE_WIDTH = 0.56f
private const val PASS_PHONE_LEFT = 0.30f
private const val PASS_PHONE_WIDTH = 0.48f
private const val PRIMARY_PERSON_RADIUS = 0.10f
private const val PRIMARY_PERSON_X = 0.23f
private const val PRIMARY_PERSON_Y = 0.42f
private const val SECONDARY_PERSON_RADIUS = 0.08f
private const val SECONDARY_PERSON_X = 0.18f
private const val SECONDARY_PERSON_Y = 0.66f
private const val HOST_NODE_RADIUS = 0.07f
private const val HOST_NODE_X = 0.50f
private const val HOST_NODE_Y = 0.76f
private const val HOST_ARC_START_ANGLE = 220f
private const val HOST_ARC_SWEEP_ANGLE = 100f
private const val HOST_INNER_ARC_LEFT = 0.28f
private const val HOST_INNER_ARC_TOP = 0.44f
private const val HOST_INNER_ARC_WIDTH = 0.44f
private const val HOST_INNER_ARC_HEIGHT = 0.34f
private const val HOST_OUTER_ARC_LEFT = 0.12f
private const val HOST_OUTER_ARC_TOP = 0.18f
private const val HOST_OUTER_ARC_WIDTH = 0.76f
private const val HOST_OUTER_ARC_HEIGHT = 0.60f
private const val JOIN_LARGE_DEVICE_LEFT = 0.08f
private const val JOIN_LARGE_DEVICE_TOP = 0.18f
private const val JOIN_LARGE_DEVICE_WIDTH = 0.52f
private const val JOIN_LARGE_DEVICE_HEIGHT = 0.68f
private const val JOIN_LARGE_DEVICE_CORNER_RADIUS = 0.09f
private const val JOIN_SMALL_DEVICE_LEFT = 0.62f
private const val JOIN_SMALL_DEVICE_TOP = 0.30f
private const val JOIN_SMALL_DEVICE_WIDTH = 0.30f
private const val JOIN_SMALL_DEVICE_HEIGHT = 0.48f
private const val JOIN_SMALL_DEVICE_CORNER_RADIUS = 0.07f

private enum class SetupIcon { Solo, PassAndPlay, Host, Join }
