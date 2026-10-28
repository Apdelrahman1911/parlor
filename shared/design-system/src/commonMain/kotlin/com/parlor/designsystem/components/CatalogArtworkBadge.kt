package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme

/** Static artwork only: the enclosing card owns its localized label and action. */
@Composable
fun CatalogArtworkBadge(artwork: ParlorArtwork, modifier: Modifier = Modifier) {
    val colors = ParlorTheme.colors
    Box(
        modifier = modifier
            .size(ParlorTheme.spacing.xxxl)
            .background(colors.accentEmber.copy(alpha = ARTWORK_HALO_ALPHA), CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = artwork.imageVector,
            contentDescription = null,
            tint = colors.accentEmber,
            modifier = Modifier.size(ParlorTheme.iconSize.xl),
        )
    }
}

private const val ARTWORK_HALO_ALPHA = 0.10f
