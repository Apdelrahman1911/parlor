package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.theme.ParlorTheme

/** Short, persistent onboarding at the point of use; never blocks the primary action. */
@Composable
fun QuickStartSteps(title: String, steps: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
    ) {
        Text(
            text = title,
            style = ParlorTheme.typography.headingMedium,
            color = ParlorTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        steps.forEachIndexed { index, step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .sizeIn(minWidth = ParlorTheme.spacing.xl, minHeight = ParlorTheme.spacing.xl)
                        .clip(RoundedCornerShape(ParlorTheme.radii.subtle))
                        .background(ParlorTheme.colors.surfaceHigher)
                        .padding(ParlorTheme.spacing.xs),
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = ParlorTheme.typography.labelMedium,
                        color = ParlorTheme.colors.accentEmber,
                    )
                }
                Text(
                    text = step,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
