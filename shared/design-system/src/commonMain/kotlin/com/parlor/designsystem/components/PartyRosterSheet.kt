package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Per-member state shown in the roster sheet. The presenter layer maps
 * `(connected, disconnectedPlayers, droppedPlayers)` triples to one of
 * these four buckets.
 */
enum class PartyMemberState { Connected, Away, Dropped, Spectator }

/**
 * One row's data — opaque to the design system; the caller decides
 * what "primary action" looks like (drop / readmit) and passes the
 * already-localized label.
 */
data class PartyRosterEntry(
    val playerId: String,
    val displayName: String,
    val state: PartyMemberState,
    val statePillLabel: String,
    val hostActionLabel: String? = null,
    val onHostAction: (() -> Unit)? = null,
)

/**
 * Bottom-sheet / popover content. The caller wraps this in their
 * platform-appropriate sheet container (Material3 ModalBottomSheet on
 * mobile, a centered Card on desktop). The sheet itself is lazy — it
 * is only composed when the user taps the [PartyRosterChip], so the
 * cost of the per-member rows doesn't sit on every recomposition of
 * the gameplay screen behind it.
 */
@Composable
fun PartyRosterSheet(
    title: String,
    entries: List<PartyRosterEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.elevated))
            .background(colors.surfaceElevated)
            .padding(ParlorTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = ParlorTheme.typography.labelLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                    .background(colors.surfaceInset)
                    .clickable(onClick = onDismiss)
                    .padding(
                        horizontal = ParlorTheme.spacing.s,
                        vertical = ParlorTheme.spacing.xs,
                    ),
            ) {
                Text(
                    text = "×",
                    style = ParlorTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            items(entries, key = { it.playerId }) { entry ->
                MemberRow(entry)
            }
        }
    }
}

@Composable
private fun MemberRow(entry: PartyRosterEntry) {
    val colors = ParlorTheme.colors
    val dotColor: Color = when (entry.state) {
        PartyMemberState.Connected -> colors.semanticSuccess
        PartyMemberState.Away -> colors.accentBrass
        PartyMemberState.Dropped -> colors.semanticDanger
        PartyMemberState.Spectator -> colors.semanticMuted
    }
    Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .background(colors.surfaceInset)
            .padding(ParlorTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .size(ParlorTheme.spacing.s)
                .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                .background(dotColor),
        )
        Text(
            text = entry.displayName,
            style = ParlorTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = androidx.compose.ui.Modifier.weight(1f),
        )
        Text(
            text = entry.statePillLabel,
            style = ParlorTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        val action = entry.onHostAction
        val actionLabel = entry.hostActionLabel
        if (action != null && actionLabel != null) {
            Spacer(androidx.compose.ui.Modifier.size(ParlorTheme.spacing.s))
            Box(
                modifier = androidx.compose.ui.Modifier
                    .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                    .background(colors.surfaceElevated)
                    .clickable(onClick = action)
                    .padding(
                        horizontal = ParlorTheme.spacing.m,
                        vertical = ParlorTheme.spacing.xs,
                    ),
            ) {
                Text(
                    text = actionLabel,
                    style = ParlorTheme.typography.labelSmall,
                    color = colors.accentEmber,
                )
            }
        }
    }
}
