package com.parlor.games.lastlight.ui.flow.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.local_options_title
import com.parlor.games.lastlight.resources.table_leave
import com.parlor.games.lastlight.resources.table_leave_description
import com.parlor.games.lastlight.ui.theme.LastLightColors
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LastLightTableToolbar(onRequestLeave: () -> Unit, onOptions: () -> Unit, enabled: Boolean) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val leaveDescription = stringResource(Res.string.table_leave_description)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp)
            .testTag("game-table-toolbar"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableControl(
            text = stringResource(Res.string.table_leave),
            glyph = TableGlyph.Leave,
            onClick = onRequestLeave,
            enabled = enabled,
            modifier = (if (largeText) Modifier.weight(0.4f).fillMaxHeight() else Modifier).testTag("game-leave")
                .semantics { contentDescription = leaveDescription },
        )
        if (!largeText) Spacer(Modifier.weight(1f))
        TableControl(
            text = stringResource(Res.string.local_options_title),
            glyph = TableGlyph.Options,
            onClick = onOptions,
            enabled = enabled,
            modifier = (if (largeText) Modifier.weight(0.6f).fillMaxHeight() else Modifier).testTag("game-options"),
        )
    }
}

@Composable
private fun TableControl(
    text: String,
    glyph: TableGlyph,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, LastLightColors.Divider.copy(alpha = 0.75f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LastLightColors.Ink.copy(alpha = 0.22f),
            contentColor = LastLightColors.Paper,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (!largeText) {
            TableControlGlyph(glyph, Modifier.size(18.dp), if (glyph == TableGlyph.Leave) LastLightColors.Muted else LastLightColors.Citron)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

internal enum class TableGlyph { Leave, Options, Sound, Haptics, Screen }

/** Small code-native ornaments; only the directional Leave arrow mirrors in RTL. */
@Composable
internal fun TableControlGlyph(glyph: TableGlyph, modifier: Modifier = Modifier, color: Color = LastLightColors.Citron) {
    val mirror = glyph == TableGlyph.Leave && LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.clearAndSetSemantics { }) {
        scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f) {
            val w = size.width
            val h = size.height
            val stroke = size.minDimension * 0.075f
            when (glyph) {
                TableGlyph.Leave -> {
                    val door = Path().apply {
                        moveTo(w * 0.55f, h * 0.16f); lineTo(w * 0.85f, h * 0.16f)
                        lineTo(w * 0.85f, h * 0.84f); lineTo(w * 0.55f, h * 0.84f)
                    }
                    drawPath(door, color, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.65f, h * 0.5f), stroke, StrokeCap.Round)
                    val arrow = Path().apply {
                        moveTo(w * 0.35f, h * 0.3f); lineTo(w * 0.15f, h * 0.5f); lineTo(w * 0.35f, h * 0.7f)
                    }
                    drawPath(arrow, color, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                TableGlyph.Options -> repeat(3) { index ->
                    val y = h * (0.22f + index * 0.28f)
                    val x = w * if (index == 1) 0.65f else 0.35f
                    drawLine(color, Offset(w * 0.12f, y), Offset(w * 0.88f, y), stroke, StrokeCap.Round)
                    drawCircle(LastLightColors.Ink, w * 0.1f, Offset(x, y))
                    drawCircle(color, w * 0.1f, Offset(x, y), style = Stroke(stroke))
                }
                TableGlyph.Sound -> {
                    val speaker = Path().apply {
                        moveTo(w * 0.15f, h * 0.4f); lineTo(w * 0.3f, h * 0.4f)
                        lineTo(w * 0.5f, h * 0.2f); lineTo(w * 0.5f, h * 0.8f)
                        lineTo(w * 0.3f, h * 0.6f); lineTo(w * 0.15f, h * 0.6f); close()
                    }
                    drawPath(speaker, color, style = Stroke(stroke))
                    drawArc(color, -60f, 120f, false, Offset(w * 0.38f, h * 0.18f), Size(w * 0.5f, h * 0.64f), style = Stroke(stroke))
                }
                TableGlyph.Haptics -> {
                    drawRoundRect(
                        color, Offset(w * 0.32f, h * 0.2f), Size(w * 0.36f, h * 0.6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = Stroke(stroke),
                    )
                    drawLine(color, Offset(w * 0.14f, h * 0.35f), Offset(w * 0.14f, h * 0.65f), stroke, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.86f, h * 0.35f), Offset(w * 0.86f, h * 0.65f), stroke, StrokeCap.Round)
                }
                TableGlyph.Screen -> {
                    drawRoundRect(
                        color, Offset(w * 0.25f, h * 0.1f), Size(w * 0.5f, h * 0.8f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = Stroke(stroke),
                    )
                    drawCircle(color, w * 0.1f, Offset(w * 0.5f, h * 0.45f))
                    drawLine(color, Offset(w * 0.42f, h * 0.77f), Offset(w * 0.58f, h * 0.77f), stroke, StrokeCap.Round)
                }
            }
        }
    }
}
