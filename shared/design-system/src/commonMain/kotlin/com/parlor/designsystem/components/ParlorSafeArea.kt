package com.parlor.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Insets screen content by the larger of its visual spacing and the device's
 * system-bar/cutout safe area. Applying this after a scroll modifier keeps the
 * viewport edge to edge while making the inset part of the scrollable content.
 */
@Composable
fun Modifier.parlorSafeContentPadding(
    horizontal: Dp,
    top: Dp = horizontal,
    bottom: Dp = horizontal,
): Modifier = windowInsetsPadding(parlorSafeContentInsets(horizontal, top, bottom))

/** Safe content padding for lazy containers without constraining their viewport. */
@Composable
fun parlorSafeContentPaddingValues(
    horizontal: Dp,
    top: Dp = horizontal,
    bottom: Dp = horizontal,
): PaddingValues = parlorSafeContentInsets(horizontal, top, bottom).asPaddingValues()

@Composable
private fun parlorSafeContentInsets(
    horizontal: Dp,
    top: Dp,
    bottom: Dp,
): WindowInsets = WindowInsets.systemBars
    .union(WindowInsets.displayCutout)
    .union(
        WindowInsets(
            left = horizontal,
            top = top,
            right = horizontal,
            bottom = bottom,
        ),
    )
