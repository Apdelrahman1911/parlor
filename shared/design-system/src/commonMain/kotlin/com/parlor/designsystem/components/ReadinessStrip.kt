package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Single-line "X of N ready" + waiting-on list. Use it on Party Play
 * shared screens (Intro, Briefing, simultaneous Reveal) so the host
 * knows whose ack the readiness invariant is still waiting on.
 *
 * The strip is purely presentational — the caller's controller decides
 * whether the primary CTA is enabled. Both surfaces read the same
 * readiness math via `PartyReadiness`, so the chip text and button
 * state never disagree.
 *
 * @param readyLabel "3 of 5 ready" (already localized + interpolated).
 * @param pendingLabel "Waiting on Alice, Bob" — `null` when complete.
 */
@Composable
fun ReadinessStrip(
    readyLabel: String,
    pendingLabel: String?,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .background(colors.surfaceInset)
            .border(
                width = ParlorTheme.borders.hairline,
                color = colors.borderSubtle,
                shape = RoundedCornerShape(ParlorTheme.radii.card),
            )
            .padding(
                horizontal = ParlorTheme.spacing.m,
                vertical = ParlorTheme.spacing.s,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xxs),
        ) {
            Text(
                text = readyLabel,
                style = ParlorTheme.typography.labelLarge,
                color = colors.textPrimary,
            )
            if (pendingLabel != null) {
                Text(
                    text = pendingLabel,
                    style = ParlorTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
