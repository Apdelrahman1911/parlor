package com.parlor.games.whodunit.ui.screens.reveal

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The final-reveal moment, editorial direction.
 *
 * Staged as three timed beats:
 *
 *  1. **Verdict word** ("Yes"/"No") fades in immediately at full display
 *     size, with no decoration.
 *  2. **Accent line** — a 48dp coral horizontal bar — slides in from
 *     zero width to its full width, drawing the eye to the verdict card.
 *  3. **Verdict card** holding the killer's name fades in.
 *  4. **Narrative** fades in last so the room reads the killer's name
 *     first.
 *
 * Reduced-motion users see the final layout immediately.
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
    // Capture the verdict in a local so the smart cast in the nested `when`
    // is anchored to one stable read. The previous code branched three times
    // on the parameter; on Kotlin/Native an incremental rebuild occasionally
    // dropped exhaustiveness on the second / third `when` over the same
    // sealed-interface parameter, throwing NoWhenBranchMatchedException on
    // an instance that any `is` check would otherwise match.
    val v: Verdict = verdict
    val playersWin = v is Verdict.PlayersWin
    val verdictLine = if (playersWin) {
        stringResource(Res.string.reveal_stage_yes)
    } else {
        stringResource(Res.string.reveal_stage_no)
    }
    val accentColor = if (playersWin) colors.semanticSuccess else colors.semanticDanger
    val subhead = if (v is Verdict.PlayersWin) {
        stringResource(Res.string.reveal_stage_players_win_subhead)
    } else {
        val killerCause = (v as? Verdict.KillerWins)?.cause
        when (killerCause) {
            KillerWinCause.InnocentAccused ->
                stringResource(Res.string.reveal_stage_killer_innocent_subhead)
            KillerWinCause.TieUnresolved ->
                stringResource(Res.string.reveal_stage_killer_tie_subhead)
            KillerWinCause.SurvivedToFinalTwo ->
                stringResource(Res.string.reveal_stage_killer_finaltwo_subhead)
            null -> stringResource(Res.string.reveal_stage_killer_innocent_subhead)
        }
    }

    // Stage gating:
    //   0 = verdict word only
    //   1 = accent line draws + card fades in
    //   2 = narrative fades in
    var stage by remember { mutableStateOf(if (reduced) 2 else 0) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        delay(220L)
        stage = 1
        delay(540L)
        stage = 2
    }

    val accentLineProgress by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 540, easing = LinearOutSlowInEasing),
        label = "reveal-accent-line",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 460),
        label = "reveal-card-alpha",
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
            EyebrowLabel(text = stringResource(Res.string.reveal_stage_eyebrow), accent = false)
            Text(
                text = verdictLine,
                style = ParlorTheme.typography.displayHero,
                color = accentColor,
                textAlign = TextAlign.Center,
            )
            // Accent line — coral bar that draws in from zero width.
            Box(
                modifier = Modifier
                    .width(ParlorTheme.spacing.xxxl * 1.5f * accentLineProgress)
                    .height(ParlorTheme.spacing.xs)
                    .background(colors.accentEmber),
            )
            Text(
                text = subhead,
                style = ParlorTheme.typography.displayMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(ParlorTheme.spacing.m))

            ParlorCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(cardAlpha),
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xxl,
                hero = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                    EyebrowLabel(text = stringResource(Res.string.reveal_stage_killer_was_label))
                    Text(
                        text = killerDisplayName,
                        style = ParlorTheme.typography.displayHero,
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
            ParlorButton(
                label = stringResource(Res.string.reveal_stage_continue),
                contentDescription = stringResource(Res.string.reveal_stage_continue_description),
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
