// Adapted from PartyDeck Standard, commit df649e6c896203bdf93130f6497e229757d5da30.
package com.parlor.games.lastlight.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.games.lastlight.ui.PendingAction
import com.parlor.games.lastlight.ui.theme.DeckButton
import com.parlor.games.lastlight.ui.theme.LocalReduceMotion
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.games.lastlight.ui.theme.SectionLabel
import com.parlor.games.lastlight.domain.model.CardRank
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.RoundOutcome
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.game_bluff_detail
import com.parlor.games.lastlight.resources.game_bluff_title
import com.parlor.games.lastlight.resources.game_card_matches
import com.parlor.games.lastlight.resources.game_card_mismatch
import com.parlor.games.lastlight.resources.game_challenged_sentence
import com.parlor.games.lastlight.resources.game_claimed_sentence
import com.parlor.games.lastlight.resources.game_dealing
import com.parlor.games.lastlight.resources.game_final_reveal
import com.parlor.games.lastlight.resources.game_fuse_hidden_point
import com.parlor.games.lastlight.resources.game_fuse_label
import com.parlor.games.lastlight.resources.game_fuse_out
import com.parlor.games.lastlight.resources.game_fuse_out_detail
import com.parlor.games.lastlight.resources.game_fuse_safe
import com.parlor.games.lastlight.resources.game_fuse_tests
import com.parlor.games.lastlight.resources.game_hide_final_reveal
import com.parlor.games.lastlight.resources.game_next_round
import com.parlor.games.lastlight.resources.game_result_unavailable
import com.parlor.games.lastlight.resources.game_result_safe_summary
import com.parlor.games.lastlight.resources.game_result_out_summary
import com.parlor.games.lastlight.resources.game_return_lobby
import com.parlor.games.lastlight.resources.game_returning
import com.parlor.games.lastlight.resources.game_revealed_card_description
import com.parlor.games.lastlight.resources.game_revealed_cards
import com.parlor.games.lastlight.resources.game_round_label
import com.parlor.games.lastlight.resources.game_truth_detail
import com.parlor.games.lastlight.resources.game_truth_title
import com.parlor.games.lastlight.resources.game_waiting_host
import com.parlor.games.lastlight.resources.game_wild_match_detail
import com.parlor.games.lastlight.resources.game_winner_eyebrow
import com.parlor.games.lastlight.resources.game_winner_name
import com.parlor.games.lastlight.resources.game_winner_support
import com.parlor.games.lastlight.resources.game_you_won
import org.jetbrains.compose.resources.stringResource
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun RoundResultScreen(
    view: GameView,
    isHost: Boolean,
    canSendAction: Boolean,
    canAdvanceRound: Boolean,
    pendingAction: PendingAction?,
    largeText: Boolean,
    onNextRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp).testTag("game-round-result"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val outcome = view.roundOutcome
        if (outcome != null) {
            ChallengeResult(view, outcome, largeText)
        } else {
            Text(stringResource(Res.string.game_result_unavailable), style = MaterialTheme.typography.headlineSmall)
        }
        if (isHost) {
            DeckButton(
                text = stringResource(
                    if (pendingAction == PendingAction.NEXT_ROUND) Res.string.game_dealing
                    else Res.string.game_next_round,
                ),
                onClick = onNextRound,
                enabled = canAdvanceRound && canSendAction && outcome != null,
                modifier = Modifier.fillMaxWidth().testTag("game-next-round"),
            )
        } else {
            WaitingForHost()
        }
    }
}

@Composable
internal fun ChallengeResult(
    view: GameView,
    outcome: RoundOutcome,
    largeText: Boolean,
    modifier: Modifier = Modifier,
    announceVerdict: Boolean = true,
) {
    val claimant = playerName(view, outcome.claimantId)
    val challenger = playerName(view, outcome.challengerId)
    val penalized = playerName(view, outcome.penalizedPlayerId)
    val verdict = stringResource(if (outcome.truthful) Res.string.game_truth_title else Res.string.game_bluff_title)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionLabel(stringResource(Res.string.game_round_label, outcome.roundNumber))
            Text(
                verdict,
                // Keep long translated words intact without clamping the user's font scale.
                style = if (largeText) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                color = if (outcome.truthful) LastLightColors.Citron else LastLightColors.Copper,
                modifier = Modifier.semantics {
                    heading()
                    if (announceVerdict) liveRegion = LiveRegionMode.Polite
                },
            )
            Text(
                text = stringResource(
                    if (outcome.burnedOut) Res.string.game_result_out_summary else Res.string.game_result_safe_summary,
                    penalized,
                    outcome.penaltyAttempt,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = LastLightColors.Paper,
            )
            Text(
                stringResource(Res.string.game_challenged_sentence, challenger, claimant),
                style = MaterialTheme.typography.bodyMedium,
                color = LastLightColors.Paper,
            )
            Text(
                stringResource(
                    Res.string.game_claimed_sentence,
                    claimant,
                    rankClaim(outcome.tableRank, outcome.revealedCards.size),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = LastLightColors.Muted,
            )
        }

        RevealedCards(outcome, largeText)

        Text(
            stringResource(
                if (outcome.truthful) Res.string.game_truth_detail else Res.string.game_bluff_detail,
                penalized,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = LastLightColors.Paper,
        )
        FuseResult(outcome, penalized)
    }
}

@Composable
private fun RevealedCards(outcome: RoundOutcome, largeText: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel(stringResource(Res.string.game_revealed_cards))
        if (largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                outcome.revealedCards.forEach { card ->
                    val matches = card.rank == outcome.tableRank || card.rank == CardRank.WILD
                    val status = revealStatus(card.rank, matches)
                    val description = stringResource(Res.string.game_revealed_card_description, rankName(card.rank), status)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = LastLightColors.Paper,
                        contentColor = LastLightColors.Ink,
                        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RankSymbol(card.rank, Modifier.size(42.dp))
                            Column(Modifier.weight(1f)) {
                                Text(rankName(card.rank), style = MaterialTheme.typography.titleSmall)
                                Text(status, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                outcome.revealedCards.forEach { card ->
                    val matches = card.rank == outcome.tableRank || card.rank == CardRank.WILD
                    val status = revealStatus(card.rank, matches)
                    val description = stringResource(Res.string.game_revealed_card_description, rankName(card.rank), status)
                    Column(
                        modifier = Modifier.width(80.dp).clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CardFace(card.rank, Modifier.size(width = 80.dp, height = 120.dp))
                        Text(
                            status,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (matches) LastLightColors.Citron else LastLightColors.Copper,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun revealStatus(rank: CardRank, matches: Boolean): String = stringResource(
    when {
        rank == CardRank.WILD -> Res.string.game_wild_match_detail
        matches -> Res.string.game_card_matches
        else -> Res.string.game_card_mismatch
    },
)

@Composable
private fun FuseResult(outcome: RoundOutcome, penalized: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = LastLightColors.Surface,
        border = BorderStroke(1.dp, LastLightColors.Divider),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(stringResource(Res.string.game_fuse_label))
            FuseLights(outcome.penaltyAttempt, outcome.burnedOut, compact = false)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(
                        if (outcome.burnedOut) Res.string.game_fuse_out else Res.string.game_fuse_safe,
                        penalized,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (outcome.burnedOut) LastLightColors.Copper else LastLightColors.Citron,
                )
                Text(
                    text = stringResource(Res.string.game_fuse_tests, outcome.penaltyAttempt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LastLightColors.Paper,
                )
                if (outcome.burnedOut) {
                    Text(
                        stringResource(Res.string.game_fuse_out_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = LastLightColors.Muted,
                    )
                }
                Text(
                    stringResource(Res.string.game_fuse_hidden_point),
                    style = MaterialTheme.typography.bodySmall,
                    color = LastLightColors.Muted,
                )
            }
        }
    }
}

@Composable
internal fun MatchResultScreen(
    view: GameView,
    isHost: Boolean,
    canSendAction: Boolean,
    canReturnToLobby: Boolean,
    pendingAction: PendingAction?,
    largeText: Boolean,
    onReturnToLobby: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showReveal by rememberSaveable(view.winnerId, view.roundNumber) { mutableStateOf(false) }
    val continuation: @Composable () -> Unit = {
        if (isHost) {
            DeckButton(
                text = stringResource(
                    if (pendingAction == PendingAction.RETURN_TO_LOBBY) Res.string.game_returning
                    else Res.string.game_return_lobby,
                ),
                onClick = onReturnToLobby,
                enabled = canReturnToLobby && canSendAction,
                modifier = Modifier.fillMaxWidth().testTag("game-rematch"),
            )
        } else {
            WaitingForHost()
        }
        view.roundOutcome?.let { outcome ->
            TextButton(
                onClick = { showReveal = !showReveal },
                modifier = Modifier.heightIn(min = 48.dp).testTag("game-final-reveal"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(stringResource(if (showReveal) Res.string.game_hide_final_reveal else Res.string.game_final_reveal))
            }
            if (showReveal) ChallengeResult(view, outcome, largeText, Modifier.fillMaxWidth(), announceVerdict = false)
        }
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag("game-winner"),
        contentAlignment = Alignment.TopCenter,
    ) {
        val shortLandscape = maxWidth >= 680.dp && maxHeight < 480.dp && !largeText
        if (shortLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.width(176.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionLabel(stringResource(Res.string.game_winner_eyebrow), color = LastLightColors.Citron)
                    WinnerEmblem(view.winnerId, diameter = 112.dp)
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WinnerSummary(view, largeText = false, shortLandscape = true)
                    continuation()
                }
            }
        } else {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = if (largeText) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (largeText) 16.dp else 20.dp),
            ) {
                SectionLabel(stringResource(Res.string.game_winner_eyebrow), color = LastLightColors.Citron)
                if (!largeText) WinnerEmblem(view.winnerId)
                WinnerSummary(view, largeText, shortLandscape = false)
                if (!largeText) Spacer(Modifier.height(2.dp))
                continuation()
            }
        }
    }
}

@Composable
private fun WinnerSummary(view: GameView, largeText: Boolean, shortLandscape: Boolean) {
    val textAlign = if (shortLandscape) TextAlign.Start else TextAlign.Center
    Column(
        horizontalAlignment = if (shortLandscape) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (shortLandscape) 8.dp else 12.dp),
    ) {
        Text(
            stringResource(Res.string.game_winner_name, playerName(view, view.winnerId)),
            // Use a readable base style while preserving the user's complete system font scale.
            style = if (largeText) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
            color = LastLightColors.Paper,
            textAlign = textAlign,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        if (!largeText) {
            Text(
                stringResource(
                    if (view.winnerId == view.viewerId) Res.string.game_you_won else Res.string.game_winner_support,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LastLightColors.Muted,
                textAlign = textAlign,
            )
        }
        SectionLabel(stringResource(Res.string.game_round_label, view.roundNumber))
    }
}

@Composable
private fun WaitingForHost() {
    Text(
        stringResource(Res.string.game_waiting_host),
        style = MaterialTheme.typography.bodyMedium,
        color = LastLightColors.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}

@Composable
private fun WinnerEmblem(winnerId: String?, diameter: Dp = 152.dp) {
    val reduceMotion = LocalReduceMotion.current
    var entered by remember(winnerId) { mutableStateOf(false) }
    LaunchedEffect(winnerId) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 240),
        label = "winner-ornament",
    )
    Box(
        modifier = Modifier.size(diameter).clearAndSetSemantics { }
            .graphicsLayer { alpha = if (reduceMotion) 1f else 0.7f + entrance * 0.3f },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            repeat(12) { index ->
                val angle = index * kotlin.math.PI / 6
                val inner = size.minDimension * 0.43f
                val outer = size.minDimension * 0.48f
                drawLine(
                    color = if (index % 3 == 0) LastLightColors.Copper else LastLightColors.Citron,
                    start = center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
                    end = center + Offset((cos(angle) * outer).toFloat(), (sin(angle) * outer).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Box(
            Modifier.size(diameter * (112f / 152f)).background(LastLightColors.Citron, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            RankSymbol(CardRank.STAR, Modifier.size(diameter * 0.5f))
        }
    }
}
