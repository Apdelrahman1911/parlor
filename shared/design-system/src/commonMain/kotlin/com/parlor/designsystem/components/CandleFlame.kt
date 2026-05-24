package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Loading indicator — editorial direction.
 *
 * Originally a Canvas-drawn flickering candle flame (cozy-noir Wave 6E).
 * Replaced by a thin coral circular progress for the modern dark
 * editorial direction. The public API of the composable is preserved
 * so existing callers keep compiling.
 */
@Composable
fun CandleFlame(
    modifier: Modifier = Modifier,
    size: Dp = ParlorTheme.iconSize.xl,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = ParlorTheme.colors.accentEmber,
            trackColor = ParlorTheme.colors.borderSubtle,
            strokeWidth = ParlorTheme.borders.strong,
            modifier = Modifier.size(size),
        )
    }
}
