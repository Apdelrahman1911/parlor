// Adapted from PartyDeck Standard, commit df649e6c896203bdf93130f6497e229757d5da30.
package com.parlor.games.lastlight.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.PlayerView
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.game_card_count
import com.parlor.games.lastlight.resources.game_fuse_tests
import com.parlor.games.lastlight.resources.game_fuse_risk_short
import com.parlor.games.lastlight.resources.game_hands_heading
import com.parlor.games.lastlight.resources.game_out
import com.parlor.games.lastlight.resources.game_player_you
import com.parlor.games.lastlight.resources.game_seat_claim_pending
import com.parlor.games.lastlight.resources.game_seat_hand_status
import com.parlor.games.lastlight.resources.game_seat_next_deal
import com.parlor.games.lastlight.resources.game_seat_no_cards
import com.parlor.games.lastlight.resources.game_seat_numbered_name
import com.parlor.games.lastlight.resources.game_seat_summary
import com.parlor.games.lastlight.resources.game_to_play
import com.parlor.games.lastlight.resources.game_you
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.games.lastlight.ui.theme.SectionLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** All 2–6 seats stay in table order; no collapsed list or offscreen horizontal seats. */
@Composable
internal fun PublicPlayerHands(
    view: GameView,
    largeText: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("game-player-hands")) {
        val minimumSeatWidth = if (compact) 88.dp else 116.dp
        val columns = if (largeText) 1 else ((maxWidth + 8.dp) / (minimumSeatWidth + 8.dp))
            .toInt().coerceIn(1, minOf(3, view.players.size.coerceAtLeast(1)))
        val compactCards = compact && (maxWidth - 8.dp * (columns - 1)) / columns < 112.dp
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(stringResource(Res.string.game_hands_heading), Modifier.semantics { heading() })
                    Box(Modifier.weight(1f).height(1.dp).background(LastLightColors.Divider.copy(alpha = 0.6f)))
                }
            }
            view.players.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { player ->
                        key(player.id) {
                            PublicSeat(view, player, largeText, compact, compactCards, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                stringResource(Res.string.game_fuse_risk_short),
                modifier = Modifier.fillMaxWidth().testTag("game-fuse-risk"),
                style = MaterialTheme.typography.bodySmall,
                color = LastLightColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PublicSeat(
    view: GameView,
    player: PlayerView,
    largeText: Boolean,
    compact: Boolean,
    compactCards: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = player.id == view.turnPlayerId
    val self = player.id == view.viewerId
    val status = when {
        player.eliminated -> stringResource(Res.string.game_out)
        player.handCount == 0 && view.latestClaim?.playerId == player.id -> stringResource(Res.string.game_seat_claim_pending)
        player.handCount == 0 -> stringResource(Res.string.game_seat_next_deal)
        active -> stringResource(Res.string.game_to_play)
        else -> null
    }
    val uniqueName = playerName(view, player.id)
    val spokenName = if (self) stringResource(Res.string.game_player_you, uniqueName) else uniqueName
    val visibleName = when {
        self -> stringResource(Res.string.game_you)
        view.players.count { it.displayName.equals(player.displayName, ignoreCase = true) } > 1 -> stringResource(
            Res.string.game_seat_numbered_name,
            view.players.indexOfFirst { it.id == player.id } + 1,
            player.displayName,
        )
        else -> player.displayName
    }
    val count = pluralStringResource(Res.plurals.game_card_count, player.handCount, player.handCount)
    val handStatus = if (status == null) count else stringResource(Res.string.game_seat_hand_status, count, status)
    val fuse = stringResource(Res.string.game_fuse_tests, player.penaltyAttempts)
    val description = stringResource(Res.string.game_seat_summary, spokenName, handStatus, fuse)
    Column(
        modifier = modifier.testTag("game-player-${player.id}")
            .clearAndSetSemantics { contentDescription = description }
            .background(
                Brush.verticalGradient(
                    listOf(if (active) LastLightColors.Citron.copy(alpha = 0.09f) else Color.Transparent, Color.Transparent),
                ),
                RoundedCornerShape(16.dp),
            )
            .padding(vertical = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 5.dp),
    ) {
        PublicHandFan(player, active, compactCards)
        SeatNameplate(visibleName, player, active, largeText)
        if (status != null && (!compact || !active)) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (active) LastLightColors.Citron else LastLightColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SeatNameplate(name: String, player: PlayerView, active: Boolean, largeText: Boolean) {
    val foreground = when {
        active -> LastLightColors.Ink
        player.eliminated -> LastLightColors.Muted
        else -> LastLightColors.Paper
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (active) LastLightColors.Citron else LastLightColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (active) LastLightColors.Citron else LastLightColors.Divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FuseCylinder(player.penaltyAttempts, player.eliminated, Modifier.size(22.dp))
            Text(
                text = name,
                modifier = Modifier.weight(1f).testTag("game-player-name-${player.id}"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
                textAlign = TextAlign.Center,
                maxLines = if (largeText) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One identical back per public card (validated 0–5), never ranks, IDs, or an opponent's private slice. */
@Composable
private fun PublicHandFan(player: PlayerView, active: Boolean, compact: Boolean) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val width = if (compact) 22.dp else 32.dp
    val step = if (compact) 13.dp else 16.dp
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 64.dp)
            .testTag("game-hand-fan-${player.id}"),
        contentAlignment = Alignment.Center,
    ) {
        if (player.handCount == 0) {
            Text(
                text = stringResource(Res.string.game_seat_no_cards),
                modifier = Modifier.padding(horizontal = 4.dp).testTag("game-hand-empty-${player.id}"),
                style = MaterialTheme.typography.labelMedium,
                color = LastLightColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
        repeat(player.handCount) { index ->
            val position = index - (player.handCount - 1) / 2f
            CardBack(
                Modifier.size(width, width * 1.5f)
                    .offset(x = step * position, y = (if (compact) 2.dp else 3.dp) * (kotlin.math.abs(position) - 1f))
                    .rotate(position * 7f * direction)
                    .shadow(2.dp, RoundedCornerShape(3.dp))
                    .border(
                        1.dp,
                        if (active) LastLightColors.Citron else LastLightColors.Paper.copy(alpha = 0.8f),
                        RoundedCornerShape(3.dp),
                    )
                    .testTag("game-hand-back-${player.id}-$index"),
            )
        }
    }
}
