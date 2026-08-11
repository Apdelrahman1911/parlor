package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Loading indicator — editorial direction.
 *
 * Thin themed circular progress indicator. The historical name remains as a
 * source-compatible component API for existing callers.
 */
@Composable
fun CandleFlame(
    modifier: Modifier = Modifier,
    size: Dp = ParlorTheme.iconSize.xl,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        ParlorActivityIndicator(
            modifier = Modifier.size(size),
            color = ParlorTheme.colors.accentEmber,
            trackColor = ParlorTheme.colors.borderSubtle,
            strokeWidth = ParlorTheme.borders.strong,
        )
    }
}
