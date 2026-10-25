package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.app.resources.Res
import com.parlor.app.resources.catalog_filter_all
import com.parlor.app.resources.catalog_filter_local
import com.parlor.app.resources.catalog_filter_nearby
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellCapabilities
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

internal enum class HomeCatalogFilter { All, Local, Nearby }

internal fun GameShellCapabilities.supportsLocalPlay(): Boolean =
    supports(GameEntryMode.Solo) || supports(GameEntryMode.PassAndPlay)

internal fun GameShellCapabilities.supportsNearbyPlay(): Boolean =
    supports(GameEntryMode.Host) || supports(GameEntryMode.Join)

internal fun HomeCatalogFilter.accepts(capabilities: GameShellCapabilities): Boolean = when (this) {
    HomeCatalogFilter.All -> true
    HomeCatalogFilter.Local -> capabilities.supportsLocalPlay()
    HomeCatalogFilter.Nearby -> capabilities.supportsNearbyPlay()
}

@Composable
internal fun CatalogFilters(selected: HomeCatalogFilter, onSelected: (HomeCatalogFilter) -> Unit) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        HomeCatalogFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                modifier = Modifier.heightIn(min = ParlorTheme.spacing.xxl),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ParlorTheme.colors.surfaceElevated,
                    labelColor = ParlorTheme.colors.textSecondary,
                    selectedContainerColor = ParlorTheme.colors.surfaceHero,
                    selectedLabelColor = ParlorTheme.colors.textPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter == selected,
                    borderColor = ParlorTheme.colors.borderSubtle,
                    selectedBorderColor = ParlorTheme.colors.accentEmber,
                ),
                label = {
                    Text(stringResource(when (filter) {
                        HomeCatalogFilter.All -> Res.string.catalog_filter_all
                        HomeCatalogFilter.Local -> Res.string.catalog_filter_local
                        HomeCatalogFilter.Nearby -> Res.string.catalog_filter_nearby
                    }))
                },
            )
        }
    }
}
