package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Bottom tab bar. Each tab pairs a reviewed vector icon with a localized
 * label and content description. The active tab uses
 * [com.parlor.designsystem.tokens.ParlorColors.accentEmber]; inactive tabs
 * use [com.parlor.designsystem.tokens.ParlorColors.textTertiary].
 *
 * No underline or chip; the icon-and-label treatment stays compact.
 * Adds a hairline rule across the top so the bar separates from the
 * content scroll above it.
 */
data class ParlorBottomTab(
    val label: String,
    val contentDescription: String,
    val icon: ImageVector,
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
                .selectableGroup()
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = ParlorTheme.spacing.xxl)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onTabSelected(index) },
                        )
                        .padding(vertical = ParlorTheme.spacing.s)
                        .semantics {
                            contentDescription = tab.contentDescription
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
                ) {
                    val contentColor = if (selected) colors.accentEmber else colors.textTertiary
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(ParlorTheme.iconSize.m),
                    )
                    Text(
                        text = tab.label.uppercase(),
                        style = ParlorTheme.typography.labelSmall,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
