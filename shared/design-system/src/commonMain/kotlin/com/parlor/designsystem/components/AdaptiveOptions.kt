package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Two equally important choices. Phone and enlarged-text layouts stack in reading order;
 * wider layouts share a row. Children must remain intrinsically sized and must not truncate text.
 * This avoids shrinking type or forcing long Arabic labels into half-width phone cards.
 */
@Composable
fun AdaptiveOptions(
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth / fontScale < SideBySideMinimumWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                first()
                second()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

private val SideBySideMinimumWidth = 560.dp
