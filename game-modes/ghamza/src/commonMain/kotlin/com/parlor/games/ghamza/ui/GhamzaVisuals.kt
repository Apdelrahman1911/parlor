package com.parlor.games.ghamza.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme

internal val GhamzaInk = Color(0xFF071D23)
internal val GhamzaMint = Color(0xFFAAF4C6)
internal val GhamzaText = Color(0xFFF0F7EF)

@Composable
internal fun GhamzaFrame(modifier: Modifier, resetKey: String, content: @Composable ColumnScope.() -> Unit) {
    // A new phase, round or help surface starts at its next-action header, not the previous ballot's scroll position.
    val scroll = key(resetKey) { rememberScrollState() }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(GhamzaInk, Color(0xFF133B3C), GhamzaInk))),
        contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawCircle(GhamzaMint.copy(alpha = .045f), size.width * .72f, Offset(size.width, size.height * .24f))
            drawCircle(GhamzaMint.copy(alpha = .07f), size.width * .72f, Offset(size.width, size.height * .24f), style = Stroke(2f))
        }
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(scroll)
            .parlorSafeContentPadding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}

@Composable
internal fun GhamzaTitle(text: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Use a semantic heading tier on compact enlarged-text layouts; never cap the user's font scale.
        val style = if (maxWidth / fontScale < 180.dp) ParlorTheme.typography.headingLarge
            else ParlorTheme.typography.displayMedium
        Text(text, style = style, color = GhamzaText, modifier = Modifier.semantics { heading() })
    }
}

@Composable
internal fun GhamzaBody(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ParlorTheme.typography.bodyLarge, color = GhamzaText.copy(alpha = .84f), modifier = modifier)
}

@Composable
internal fun GhamzaButton(text: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (secondary) Color(0xFF244C50) else GhamzaMint,
            contentColor = if (secondary) GhamzaText else GhamzaInk,
            disabledContainerColor = Color(0xFF294044), disabledContentColor = GhamzaText.copy(alpha = .6f))) {
        Text(text, style = ParlorTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** Code-native eye artwork; no secret role is embedded in its accessibility semantics. */
@Composable
internal fun GhamzaEye(open: Boolean, modifier: Modifier = Modifier) {
    val openness by animateFloatAsState(if (open) 1f else 0f, tween(if (ParlorTheme.reducedMotion) 0 else 280))
    val artworkSize = if (LocalDensity.current.fontScale >= 1.5f) 72.dp else 144.dp
    Canvas(modifier.size(artworkSize).clearAndSetSemantics {}) {
        val path = Path().apply {
            moveTo(size.width * .1f, center.y)
            quadraticTo(center.x, size.height * .08f, size.width * .9f, center.y)
            quadraticTo(center.x, size.height * (.08f + .84f * openness), size.width * .1f, center.y)
        }
        drawPath(path, GhamzaMint, style = Stroke(4.dp.toPx()))
        if (openness > 0f) {
            drawCircle(GhamzaMint.copy(alpha = .16f * openness), size.width * .19f * openness)
            drawCircle(GhamzaMint.copy(alpha = openness), size.width * .11f * openness)
            drawCircle(GhamzaText.copy(alpha = openness), size.width * .035f * openness,
                center + Offset(size.width * .025f, -size.height * .025f))
        }
    }
}
