package com.parlor.games.whodunit.ui.screens.reveal

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.reveal_stage_continue
import com.parlor.games.whodunit.resources.reveal_stage_continue_description
import com.parlor.games.whodunit.resources.reveal_stage_eyebrow
import com.parlor.games.whodunit.resources.reveal_stage_killer_finaltwo_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_innocent_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_tie_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_was_label
import com.parlor.games.whodunit.resources.reveal_stage_no
import com.parlor.games.whodunit.resources.reveal_stage_players_win_subhead
import com.parlor.games.whodunit.resources.reveal_stage_yes
import kotlin.math.min
import org.jetbrains.compose.resources.stringResource

/**
 * The final-reveal moment. Staged in three beats:
 *
 *  1. **Verdict** ("Yes"/"No") fades in immediately at full size.
 *  2. After 150 ms the subhead and verdict-card frame scale in with a
 *     soft spring, accompanied by an ember bloom behind the killer's
 *     name.
 *  3. The narrative paragraph fades in last (700 ms in) so the room
 *     reads the killer's name before the explanation.
 *
 * Reduced-motion users skip the animation and see the final layout
 * immediately.
 */
@Composable
fun RevealStageScreen(
    verdict: Verdict,
    killerDisplayName: String,
    revealNarrative: String,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val reduced = ParlorTheme.reducedMotion
    val verdictLine = when (verdict) {
        is Verdict.PlayersWin -> stringResource(Res.string.reveal_stage_yes)
        is Verdict.KillerWins -> stringResource(Res.string.reveal_stage_no)
    }
    val accentColor = when (verdict) {
        is Verdict.PlayersWin -> colors.semanticSuccess
        is Verdict.KillerWins -> colors.semanticDanger
    }
    val subhead = when (verdict) {
        is Verdict.PlayersWin -> stringResource(Res.string.reveal_stage_players_win_subhead)
        is Verdict.KillerWins -> when (verdict.cause) {
            KillerWinCause.InnocentAccused ->
                stringResource(Res.string.reveal_stage_killer_innocent_subhead)
            KillerWinCause.TieUnresolved ->
                stringResource(Res.string.reveal_stage_killer_tie_subhead)
            KillerWinCause.SurvivedToFinalTwo ->
                stringResource(Res.string.reveal_stage_killer_finaltwo_subhead)
        }
    }

    // Staged-reveal driver. `stage`:
    //   0 = nothing on screen yet (only the verdict word fades in)
    //   1 = card scales in + ember bloom
    //   2 = narrative fades in
    var stage by remember { mutableStateOf(if (reduced) 2 else 0) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        kotlinx.coroutines.delay(180L)
        stage = 1
        kotlinx.coroutines.delay(620L)
        stage = 2
    }

    val cardScale by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0.92f,
        animationSpec = tween(durationMillis = 460, easing = LinearOutSlowInEasing),
        label = "reveal-card-scale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 460),
        label = "reveal-card-alpha",
    )
    val bloomAlpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "reveal-bloom-alpha",
    )
    val narrativeAlpha by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "reveal-narrative-alpha",
    )

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.reveal_stage_eyebrow),
                accent = false,
            )
            Text(
                text = verdictLine,
                style = ParlorTheme.typography.displayHero,
                color = accentColor,
            )
            Text(
                text = subhead,
                style = ParlorTheme.typography.displayMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            // The verdict card with an ember bloom behind the killer name.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(cardScale)
                    .alpha(cardAlpha),
                contentAlignment = Alignment.Center,
            ) {
                // Bloom — sits behind the card, expands beyond its bounds.
                Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    val center = Offset(size.width / 2f, size.height * 0.45f)
                    val r = min(size.width, size.height) * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accentEmber.copy(alpha = 0.45f * bloomAlpha),
                                colors.accentEmberDeep.copy(alpha = 0.20f * bloomAlpha),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = r,
                        ),
                        center = center,
                        radius = r,
                    )
                }
                ParlorCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = ParlorTheme.elevation.dramatic,
                    cornerRadius = ParlorTheme.radii.elevated,
                    contentPadding = ParlorTheme.spacing.xxl,
                    hero = true,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                        EyebrowLabel(text = stringResource(Res.string.reveal_stage_killer_was_label))
                        Text(
                            text = killerDisplayName,
                            style = ParlorTheme.typography.displayLarge,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(ParlorTheme.spacing.s))
                        Text(
                            text = revealNarrative,
                            style = ParlorTheme.typography.narration,
                            color = colors.textNarration,
                            modifier = Modifier.alpha(narrativeAlpha),
                        )
                    }
                }
            }
            ParlorButton(
                label = stringResource(Res.string.reveal_stage_continue),
                contentDescription = stringResource(Res.string.reveal_stage_continue_description),
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
