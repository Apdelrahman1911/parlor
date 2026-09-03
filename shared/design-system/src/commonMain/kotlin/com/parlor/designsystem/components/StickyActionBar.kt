package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Bottom-pinned action bar. Use it as the bottom layer of a [Box] that
 * also holds the screen's main scrollable content — the bar sits on top
 * of the content with a vertical gradient behind it so anything scrolled
 * close to the bottom fades into the bar rather than collides with it.
 *
 * The actions slot is typically one or two [ParlorButton] calls. The bar
 * pads itself for the system navigation bar insets so the CTA stays
 * reachable above the gesture pill on Android 12+.
 *
 * Usage:
 * ```
 * Box(modifier = Modifier.fillMaxSize()) {
 *     Column(
 *         modifier = Modifier.fillMaxSize().padding(bottom = 96.dp).verticalScroll(...),
 *     ) { /* content */ }
 *     StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
 *         ParlorButton(...)
 *     }
 * }
 * ```
 */
@Composable
fun StickyActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = ParlorTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Gradient fade — transparent at the top, opaque canvas at the
        // bottom. Sits above the bar's solid background so scrolled
        // content fades into the CTA region.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ParlorTheme.spacing.xl)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.transparent,
                            colors.surfaceCanvas,
                        ),
                    ),
                ),
        )
        // Keep the action above gesture/navigation areas without shrinking
        // the full-screen backdrop or its scroll viewport.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceCanvas)
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(
                    start = ParlorTheme.spacing.l,
                    end = ParlorTheme.spacing.l,
                    top = ParlorTheme.spacing.m,
                    bottom = ParlorTheme.spacing.m,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            ) {
                content()
            }
        }
    }
}
