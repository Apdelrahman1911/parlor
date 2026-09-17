// Adapted from PartyDeck Standard, commit df649e6c896203bdf93130f6497e229757d5da30.
package com.parlor.games.lastlight.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.games.lastlight.ui.theme.SectionLabel
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.PlayerId
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.game_claim_facedown
import com.parlor.games.lastlight.resources.game_claim_sentence
import com.parlor.games.lastlight.resources.game_hide_previous_reveal
import com.parlor.games.lastlight.resources.game_latest_claim
import com.parlor.games.lastlight.resources.game_open_instruction
import com.parlor.games.lastlight.resources.game_open_instruction_compact
import com.parlor.games.lastlight.resources.game_open_round
import com.parlor.games.lastlight.resources.game_player_fallback
import com.parlor.games.lastlight.resources.game_player_numbered
import com.parlor.games.lastlight.resources.game_players_turn
import com.parlor.games.lastlight.resources.game_previous_reveal
import com.parlor.games.lastlight.resources.game_rank_table
import com.parlor.games.lastlight.resources.game_round_label
import com.parlor.games.lastlight.resources.game_watching_table
import com.parlor.games.lastlight.resources.game_your_turn
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun playerName(view: GameView, playerId: PlayerId?): String {
    val seat = view.players.indexOfFirst { it.id == playerId }
    if (seat < 0) return stringResource(Res.string.game_player_fallback)
    val name = view.players[seat].displayName
    return if (view.players.count { it.displayName.equals(name, ignoreCase = true) } > 1) {
        stringResource(Res.string.game_player_numbered, name, seat + 1)
    } else name
}

@Composable
internal fun PublicGameTable(
    view: GameView,
    largeText: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    primaryContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        TurnHeading(view, Modifier.padding(horizontal = 20.dp), compact)
        Spacer(Modifier.height(if (compact) 4.dp else 16.dp))
        // Keep every hand above the play area on phones. At large text sizes, prioritize
        // the reveal/action controls before the individually scrollable full-name seats.
        if (primaryContent != null) {
            LatestClaim(view, largeText, Modifier.padding(horizontal = 20.dp), compact)
            Spacer(Modifier.height(24.dp))
            primaryContent()
            Spacer(Modifier.height(16.dp))
            PublicPlayerHands(view, largeText, compact, Modifier.padding(horizontal = 20.dp))
        } else {
            PublicPlayerHands(view, largeText, compact, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
            LatestClaim(view, largeText, Modifier.padding(horizontal = 20.dp), compact)
        }
        view.roundOutcome?.takeIf { it.roundNumber < view.roundNumber }?.let { outcome ->
            var showHistory by rememberSaveable(outcome.roundNumber) { mutableStateOf(false) }
            TextButton(
                onClick = { showHistory = !showHistory },
                modifier = Modifier.padding(horizontal = 20.dp).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(
                        if (showHistory) Res.string.game_hide_previous_reveal else Res.string.game_previous_reveal,
                        outcome.roundNumber,
                    ),
                    color = LastLightColors.Muted,
                )
            }
            if (showHistory) {
                ChallengeResult(
                    view,
                    outcome,
                    largeText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    announceVerdict = false,
                )
            }
        }
    }
}

@Composable
private fun TurnHeading(view: GameView, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ownTurn = view.viewerId != null && view.turnPlayerId == view.viewerId
    val turn = when {
        ownTurn -> stringResource(Res.string.game_your_turn)
        view.turnPlayerId != null -> stringResource(Res.string.game_players_turn, playerName(view, view.turnPlayerId))
        else -> stringResource(Res.string.game_watching_table)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = turn,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = if (ownTurn) LastLightColors.Citron else LastLightColors.Muted,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (compact) SectionLabel(
                    stringResource(Res.string.game_round_label, view.roundNumber),
                    Modifier.background(LastLightColors.Surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
            Text(
                text = stringResource(Res.string.game_rank_table, rankName(view.tableRank)),
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                color = LastLightColors.Paper,
                modifier = Modifier.semantics { heading() },
            )
            if (!compact) SectionLabel(stringResource(Res.string.game_round_label, view.roundNumber))
        }
        TableRankSeal(view.tableRank, Modifier.size(if (compact) 52.dp else 72.dp))
    }
}

@Composable
private fun LatestClaim(
    view: GameView,
    largeText: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val claim = view.latestClaim
    val roomyArtwork = !compact || view.players.size <= 3
    Row(
        modifier = modifier.fillMaxWidth().testTag("game-latest-claim")
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(LastLightColors.Surface, LastLightColors.Ink)))
            .border(1.dp, LastLightColors.Divider, RoundedCornerShape(20.dp))
            .padding(if (compact) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact || largeText) 12.dp else 18.dp),
    ) {
        if (!largeText) ClaimPile(
            claim?.cardCount ?: 0,
            Modifier.size(width = if (roomyArtwork) 102.dp else 56.dp, height = if (roomyArtwork) 96.dp else 48.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (claim == null) {
                if (roomyArtwork) Text(
                    text = stringResource(Res.string.game_open_round),
                    style = MaterialTheme.typography.headlineSmall,
                    color = LastLightColors.Paper,
                )
                Text(
                    stringResource(if (compact) Res.string.game_open_instruction_compact else Res.string.game_open_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = LastLightColors.Muted,
                )
            } else {
                if (roomyArtwork) SectionLabel(stringResource(Res.string.game_latest_claim))
                Text(
                    text = stringResource(
                        Res.string.game_claim_sentence,
                        playerName(view, claim.playerId),
                        rankClaim(view.tableRank, claim.cardCount),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = LastLightColors.Paper,
                )
                if (!compact) {
                    Text(
                        stringResource(Res.string.game_claim_facedown),
                        style = MaterialTheme.typography.bodySmall,
                        color = LastLightColors.Muted,
                    )
                }
            }
        }
    }
}
