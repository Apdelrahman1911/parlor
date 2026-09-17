package com.parlor.games.ghamza.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.resources.Res
import com.parlor.games.ghamza.resources.gh_report
import org.jetbrains.compose.resources.stringResource

/** Only the public report ledger animates; outgoing private roles never remain in composition. */
@Composable
internal fun GhamzaReportFeed(state: GhamzaState) {
    val duration = if (ParlorTheme.reducedMotion) 0 else 200
    AnimatedContent(state.public.recentReports.takeLast(3),
        transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) }) {
        reports ->
        if (reports.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(GhamzaMint.copy(alpha = .12f), RoundedCornerShape(18.dp)).padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reports.asReversed().forEach { report ->
                    GhamzaBody(stringResource(Res.string.gh_report,
                        state.players.first { it.id == report.player }.displayName.asBidiArgument(), report.attempt))
                }
            }
        }
    }
}

@Composable
internal fun GhamzaAttemptMarks(remaining: Int, total: Int) {
    Canvas(Modifier.size((total * 18).dp, 28.dp).clearAndSetSemantics {}) {
        repeat(total) { index ->
            val location = Offset((index * 18 + 8).dp.toPx(), center.y)
            if (index < remaining) drawCircle(GhamzaMint, 5.dp.toPx(), location)
            else drawCircle(GhamzaMint.copy(alpha = .45f), 5.dp.toPx(), location, style = Stroke(1.dp.toPx()))
        }
    }
}
