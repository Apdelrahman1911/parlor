package com.parlor.games.lastlight.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.ui.theme.LastLightColors

private val LocalTableBackdropPresent = staticCompositionLocalOf { false }

/** One edge-to-edge painting for chrome, private handoff, play and results. */
@Composable
internal fun TableBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val backdrop = if (LocalTableBackdropPresent.current) modifier else modifier.tableAtmosphere().testTag("game-table-backdrop")
    Box(backdrop) {
        CompositionLocalProvider(
            LocalTableBackdropPresent provides true,
            LocalContentColor provides LastLightColors.Paper,
        ) { content() }
    }
}

/** Contained panels share the table palette without restarting the full-screen lighting. */
@Composable
internal fun TablePanel(
    modifier: Modifier = Modifier,
    accent: Color = LastLightColors.Divider,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier.clip(shape).drawWithCache {
            val fill = Brush.linearGradient(listOf(LastLightColors.Surface.copy(alpha = 0.92f), LastLightColors.Ink.copy(alpha = 0.7f)))
            onDrawBehind { drawRect(fill) }
        }.border(1.dp, accent, shape).padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Static lighting, not an infinite animation or a clock-driven composition. */
internal fun Modifier.tableAtmosphere(): Modifier = drawWithCache {
    val glow = Brush.radialGradient(
        colors = listOf(LastLightColors.Surface, LastLightColors.Ink),
        center = Offset(size.width * 0.5f, size.height * 0.28f),
        radius = size.maxDimension * 0.7f,
    )
    onDrawBehind {
        drawRect(glow)
        drawOval(
            color = LastLightColors.Citron.copy(alpha = 0.045f),
            topLeft = Offset(size.width * 0.06f, size.height * 0.16f),
            size = Size(size.width * 0.88f, size.height * 0.48f),
            style = Stroke(1.dp.toPx()),
        )
        drawOval(
            color = LastLightColors.Paper.copy(alpha = 0.025f),
            topLeft = Offset(size.width * 0.03f, size.height * 0.14f),
            size = Size(size.width * 0.94f, size.height * 0.52f),
            style = Stroke(1.dp.toPx()),
        )
    }
}

@Composable
internal fun TableRankSeal(rank: CardRank, modifier: Modifier = Modifier) {
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(LastLightColors.Citron.copy(alpha = 0.12f), radius)
            drawCircle(LastLightColors.Citron.copy(alpha = 0.55f), radius - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawCircle(LastLightColors.Citron, radius * 0.78f)
            drawCircle(LastLightColors.Ink.copy(alpha = 0.25f), radius * 0.68f, style = Stroke(1.dp.toPx()))
        }
        RankSymbol(rank, Modifier.fillMaxSize(0.56f))
    }
}

/** Shows only the public size of the latest claim; an opening round has no pretend played cards. */
@Composable
internal fun ClaimPile(count: Int, modifier: Modifier = Modifier) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    BoxWithConstraints(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val cardWidth = maxWidth * 0.42f
        Canvas(Modifier.fillMaxSize()) {
            drawOval(LastLightColors.Ink.copy(alpha = 0.7f))
            drawOval(LastLightColors.Citron.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))
            if (count == 0) {
                drawCircle(LastLightColors.Citron.copy(alpha = 0.5f), radius = 3.dp.toPx())
                drawCircle(LastLightColors.Citron.copy(alpha = 0.15f), radius = 9.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
        }
        repeat(count) { index ->
            val position = index - (count - 1) / 2f
            CardBack(
                Modifier.size(cardWidth, cardWidth * 1.5f)
                    .offset(x = cardWidth * (position * 0.34f), y = 2.dp * kotlin.math.abs(position))
                    .rotate(position * 13f * direction)
                    .shadow(3.dp, RoundedCornerShape(4.dp))
                    .border(1.dp, LastLightColors.Paper.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .testTag("game-claim-back-$index"),
            )
        }
    }
}
