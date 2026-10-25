package com.parlor.games.wordimpostor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme

internal val WordInk = Color(0xFF16162E)
internal val WordPanelColor = Color(0xFF292747)
internal val WordPeach = Color(0xFFFFC39E)
internal val WordLilac = Color(0xFFD3C2FF)
internal val WordText = Color(0xFFF9F2F0)

@Composable
internal fun WordFrame(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(WordInk, Color(0xFF3B244F), WordInk))),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawCircle(WordPeach.copy(alpha = .05f), size.width * .8f, Offset(size.width, size.height * .3f))
            drawCircle(WordLilac.copy(alpha = .08f), size.width * .65f, Offset(0f, size.height * .8f), style = Stroke(2f))
        }
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState()).parlorSafeContentPadding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
internal fun WordPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(WordPanelColor, RoundedCornerShape(28.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}

@Composable
internal fun WordTitle(text: String) {
    Text(text, style = ParlorTheme.typography.displayMedium, color = WordText, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun WordBody(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ParlorTheme.typography.bodyLarge, color = WordText.copy(alpha = .88f), modifier = modifier)
}

@Composable
internal fun WordButton(text: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) Color(0xFF454062) else WordPeach,
            contentColor = if (secondary) WordText else WordInk,
            disabledContainerColor = Color(0xFF393548),
            disabledContentColor = WordText.copy(alpha = .65f),
        ),
    ) {
        Text(text, style = ParlorTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** A public phase indicator: never keeps an outgoing secret screen in an animation. */
@Composable
internal fun WordPhaseTrack(step: Int) {
    val duration = if (ParlorTheme.reducedMotion) 0 else 240
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(6) { index ->
            val color by animateColorAsState(if (index <= step) WordPeach else WordPanelColor, tween(duration))
            Box(Modifier.weight(1f).height(4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

/** Code-native stacked secret cards, with a small public-only settle on reveal. */
@Composable
internal fun WordEmblem(open: Boolean, modifier: Modifier = Modifier) {
    val angle by animateFloatAsState(if (open) 9f else -9f, tween(if (ParlorTheme.reducedMotion) 0 else 280))
    Canvas(modifier.size(128.dp).clearAndSetSemantics {}) {
        val card = Size(size.width * .54f, size.height * .72f)
        val origin = center - Offset(card.width / 2, card.height / 2)
        rotate(-angle) {
            drawRoundRect(WordLilac.copy(alpha = .3f), origin - Offset(9.dp.toPx(), 0f), card, CornerRadius(12.dp.toPx()))
        }
        rotate(angle) {
            drawRoundRect(WordPeach, origin, card, CornerRadius(12.dp.toPx()))
            drawRoundRect(WordInk.copy(alpha = .35f), origin + Offset(6.dp.toPx(), 6.dp.toPx()),
                Size(card.width - 12.dp.toPx(), card.height - 12.dp.toPx()), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawCircle(WordInk, 9.dp.toPx(), center - Offset(0f, 7.dp.toPx()), style = Stroke(3.dp.toPx()))
            drawCircle(WordInk, 2.dp.toPx(), center + Offset(0f, 17.dp.toPx()))
        }
    }
}

@Composable
internal fun WordScoreBar(label: String, score: Int, maximum: Int) {
    val progress by animateFloatAsState(score.toFloat() / maximum.coerceAtLeast(1), tween(if (ParlorTheme.reducedMotion) 0 else 500))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WordBody(label)
        Box(Modifier.fillMaxWidth().height(5.dp).background(WordPanelColor, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).background(WordPeach, RoundedCornerShape(3.dp)))
        }
    }
}
