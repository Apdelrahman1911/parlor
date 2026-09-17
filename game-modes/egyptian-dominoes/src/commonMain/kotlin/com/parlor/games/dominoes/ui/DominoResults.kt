package com.parlor.games.dominoes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.domain.DominoScoring
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_award
import com.parlor.games.dominoes.resources.dom_blocked
import com.parlor.games.dominoes.resources.dom_match_result
import com.parlor.games.dominoes.resources.dom_next
import com.parlor.games.dominoes.resources.dom_remaining
import com.parlor.games.dominoes.resources.dom_rematch
import com.parlor.games.dominoes.resources.dom_round_winner
import com.parlor.games.dominoes.resources.dom_score
import com.parlor.games.dominoes.resources.dom_shutout
import com.parlor.games.dominoes.resources.dom_tie
import com.parlor.games.dominoes.resources.dom_wait_host
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DominoResults(state: DominoState, host: Boolean, enabled: Boolean, onAction: (DominoAction) -> Unit) {
    val result = checkNotNull(state.public.result)
    val match = state.phase == DominoPhase.MatchResult
    val appear = remember(state.public.token) { Animatable(0f) }
    val reducedMotion = ParlorTheme.reducedMotion
    LaunchedEffect(appear, reducedMotion) { appear.animateTo(1f, tween(if (reducedMotion) 0 else 700)) }
    Column(Modifier.fillMaxWidth().background(DominoInk, RoundedCornerShape(26.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(92.dp).clearAndSetSemantics {}) {
                val radius = size.minDimension * .36f
                drawCircle(DominoGold.copy(alpha = .25f), radius, style = Stroke(1.dp.toPx()))
                repeat(16) { index ->
                    rotate(index * 22.5f) {
                        val r = radius * (1f + .2f * appear.value)
                        drawLine(DominoGold.copy(alpha = appear.value), center + Offset(0f, r),
                            center + Offset(0f, r + 5.dp.toPx()), 2.dp.toPx())
                    }
                }
            }
            Text(stringResource(Res.string.dom_award, result.points), color = DominoGold, style = ParlorTheme.typography.headingLarge)
        }
        if (match) {
            DominoTitle(stringResource(Res.string.dom_match_result))
            DominoTitle(state.players.filter { it.id in state.public.matchWinners }.joinToString(" · ") { it.displayName.asBidiArgument() })
        } else {
            DominoTitle(result.winner?.let { stringResource(Res.string.dom_round_winner, dominoSideName(state, it)) }
                ?: stringResource(Res.string.dom_tie))
        }
        if (result.blocked) DominoBody(stringResource(Res.string.dom_blocked))
        if (match && DominoScoring.isShutout(state.public.settings, state.public.scores)) {
            DominoBody(stringResource(Res.string.dom_shutout))
        }
        DominoScoring.sides(state.players, state.public.settings).entries.sortedByDescending {
            state.public.scores.getValue(it.key)
        }.forEach { (side, members) ->
            val score = state.public.scores.getValue(side)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DominoBody(dominoSideName(state, side), Modifier.weight(1f))
                    Text(stringResource(Res.string.dom_score, score), color = DominoGold, style = ParlorTheme.typography.labelLarge)
                }
                members.forEach { id ->
                    DominoBody(stringResource(Res.string.dom_remaining, state.name(id), result.remainingPips.getValue(id)))
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(DominoFelt, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth((score.toFloat() / state.public.settings.target).coerceIn(0f, 1f))
                        .height(4.dp).background(DominoGold, RoundedCornerShape(2.dp)))
                }
            }
        }
        if (host) DominoButton(stringResource(if (match) Res.string.dom_rematch else Res.string.dom_next), enabled) {
            onAction(if (match) DominoAction.Rematch(state.public.token) else DominoAction.NextRound(state.public.token))
        } else DominoBody(stringResource(Res.string.dom_wait_host))
    }
}
