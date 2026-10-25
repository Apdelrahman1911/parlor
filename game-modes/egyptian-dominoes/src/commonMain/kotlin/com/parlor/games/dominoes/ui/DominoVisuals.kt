package com.parlor.games.dominoes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme

internal val DominoFelt = Color(0xFF113E35)
internal val DominoInk = Color(0xFF0B241F)
internal val DominoGold = Color(0xFFF1CF8A)
internal val DominoText = Color(0xFFF8EFDA)

@Composable
internal fun DominoFrame(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DominoInk, DominoFelt, DominoInk))),
        contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            // Bounded deterministic felt grain, not an allocated bitmap or an animated noise field.
            repeat(160) { index ->
                val x = ((index * 137) % 997) / 997f * size.width
                val y = ((index * 251) % 991) / 991f * size.height
                drawCircle(DominoText.copy(alpha = .045f), .6.dp.toPx(), Offset(x, y))
            }
            drawRoundRect(Color(0xFF674B2C), style = Stroke(12.dp.toPx()))
            drawRoundRect(DominoGold.copy(alpha = .25f), style = Stroke(2.dp.toPx()))
        }
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize().verticalScroll(rememberScrollState()).parlorSafeContentPadding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun DominoTitle(text: String) {
    Text(text, color = DominoText, style = ParlorTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun DominoBody(text: String, modifier: Modifier = Modifier) {
    Text(text, color = DominoText.copy(alpha = .88f), style = ParlorTheme.typography.bodyLarge, modifier = modifier)
}

@Composable
internal fun DominoButton(text: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) Color(0xFF295247) else DominoGold,
            contentColor = if (secondary) DominoText else DominoInk,
            disabledContainerColor = Color(0xFF29443B), disabledContentColor = DominoText.copy(alpha = .65f),
        ),
    ) {
        Text(text, style = ParlorTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 3.dp))
    }
}
