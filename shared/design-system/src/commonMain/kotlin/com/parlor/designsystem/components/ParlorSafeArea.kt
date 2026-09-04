package com.parlor.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Insets screen content by its visual spacing in addition to the device's
 * system-bar/cutout safe area. Applying this after a scroll modifier keeps the
 * viewport edge to edge while making the inset part of the scrollable content.
 */
@Composable
fun Modifier.parlorSafeContentPadding(
    horizontal: Dp,
    top: Dp = horizontal,
    bottom: Dp = horizontal,
): Modifier {
    val minimumTop = LocalParlorSafeContentMinimumTop.current
    return windowInsetsPadding(
        parlorSafeContentInsets(horizontal, maxOf(top, minimumTop), bottom),
    )
}

/** Safe content padding for lazy containers without constraining their viewport. */
@Composable
fun parlorSafeContentPaddingValues(
    horizontal: Dp,
    top: Dp = horizontal,
    bottom: Dp = horizontal,
): PaddingValues {
    val minimumTop = LocalParlorSafeContentMinimumTop.current
    return parlorSafeContentInsets(
        horizontal = horizontal,
        top = maxOf(top, minimumTop),
        bottom = bottom,
    ).asPaddingValues()
}

private val LocalParlorSafeContentMinimumTop = staticCompositionLocalOf { 0.dp }

/** Keeps safe-area-aware content below floating chrome without shrinking its backdrop. */
@Composable
internal fun ProvideParlorSafeContentMinimumTop(
    minimumTop: Dp,
    content: @Composable () -> Unit,
) {
    val inheritedMinimum = LocalParlorSafeContentMinimumTop.current
    CompositionLocalProvider(
        LocalParlorSafeContentMinimumTop provides maxOf(inheritedMinimum, minimumTop),
        content = content,
    )
}

@Composable
private fun parlorSafeContentInsets(
    horizontal: Dp,
    top: Dp,
    bottom: Dp,
): WindowInsets = addVisualSpacing(
    safeArea = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    horizontal = horizontal,
    top = top,
    bottom = bottom,
)

internal fun addVisualSpacing(
    safeArea: WindowInsets,
    horizontal: Dp,
    top: Dp,
    bottom: Dp,
): WindowInsets = safeArea.add(
    WindowInsets(
        left = horizontal,
        top = top,
        right = horizontal,
        bottom = bottom,
    ),
)
