package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Bottom tab bar — pure typographic. Each tab is a [ParlorBottomTab]
 * with a [label] (and optional [contentDescription]). The active tab
 * lights up its label in [com.parlor.designsystem.tokens.ParlorColors.accentEmber];
 * the inactive ones sit at [textTertiary].
 *
 * No icons; no underline; no chip. Editorial typographic-only treatment.
 * Adds a hairline rule across the top so the bar separates from the
 * content scroll above it.
 */
data class ParlorBottomTab(
    val label: String,
    val contentDescription: String,
)

@Composable
fun ParlorBottomTabBar(
    tabs: List<ParlorBottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ParlorTheme.borders.hairline)
                .background(colors.borderSubtle),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceCanvas)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(
                    start = ParlorTheme.spacing.l,
                    end = ParlorTheme.spacing.l,
                    top = ParlorTheme.spacing.s,
                    bottom = ParlorTheme.spacing.s,
                ),
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = ParlorTheme.spacing.s)
                        .semantics { contentDescription = tab.contentDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label.uppercase(),
                        style = ParlorTheme.typography.labelSmall,
                        color = if (selected) colors.accentEmber else colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
