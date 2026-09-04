package com.parlor.games.mafia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.ContextRibbon
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorContextTone
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.handoff_confirm_description_format
import com.parlor.games.mafia.resources.handoff_confirm_format
import com.parlor.games.mafia.resources.handoff_cover_title
import com.parlor.games.mafia.resources.handoff_keep_hidden
import com.parlor.games.mafia.resources.handoff_pass_to_format
import com.parlor.games.mafia.resources.handoff_player_only_format
import com.parlor.games.mafia.resources.handoff_private_turn
import org.jetbrains.compose.resources.stringResource

/**
 * Hard privacy cover shown before any role or night action is composed. The
 * named player must use the explicit confirmation button; private content is
 * not used in this cover's text, semantics, or drawing.
 */
@Composable
fun MafiaCandlelitCover(
    playerName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.coverScreen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContextRibbon(
                label = stringResource(Res.string.handoff_private_turn),
                detail = stringResource(Res.string.handoff_player_only_format, playerName),
                tone = ParlorContextTone.Private,
                inverted = true,
            )
            PrivacyGlyph()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EyebrowLabel(
                    text = stringResource(Res.string.handoff_pass_to_format, playerName),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.handoff_cover_title),
                    style = ParlorTheme.typography.displayMedium,
                    color = ParlorTheme.colors.coverScreenTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.handoff_keep_hidden),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.coverScreenTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            ParlorButton(
                label = stringResource(Res.string.handoff_confirm_format, playerName),
                contentDescription = stringResource(
                    Res.string.handoff_confirm_description_format,
                    playerName,
                ),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrivacyGlyph() {
    val colors = ParlorTheme.colors
    Box(
        modifier = Modifier
            .size(ParlorTheme.spacing.xxxl + ParlorTheme.spacing.xxl)
            .clip(RoundedCornerShape(ParlorTheme.radii.pill))
            .background(colors.accentEmber.copy(alpha = PRIVACY_GLYPH_BACKGROUND_ALPHA))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ParlorTheme.iconSize.xl)) {
            val strokeWidth = size.minDimension * PRIVACY_GLYPH_STROKE_RATIO
            drawOval(
                color = colors.accentEmber,
                topLeft = Offset(0f, size.height * 0.22f),
                size = Size(size.width, size.height * 0.56f),
                style = Stroke(width = strokeWidth),
            )
            drawCircle(
                color = colors.accentEmber,
                radius = size.minDimension * 0.11f,
            )
            drawLine(
                color = colors.coverScreen,
                start = Offset(size.width * 0.08f, size.height * 0.08f),
                end = Offset(size.width * 0.92f, size.height * 0.92f),
                strokeWidth = strokeWidth * 2.8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.accentEmber,
                start = Offset(size.width * 0.08f, size.height * 0.08f),
                end = Offset(size.width * 0.92f, size.height * 0.92f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun MafiaHideScreen(
    line: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.coverScreen)
            .clickable(role = Role.Button, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(ParlorTheme.spacing.xxl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrivacyGlyph()
            Text(
                text = line,
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.coverScreenTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val PRIVACY_GLYPH_BACKGROUND_ALPHA = 0.12f
private const val PRIVACY_GLYPH_STROKE_RATIO = 0.055f
