package com.parlor.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme

@Composable
fun EyebrowLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = true,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text.uppercase(),
        style = ParlorTheme.typography.labelSmall,
        color = if (accent) ParlorTheme.colors.accentEmber else ParlorTheme.colors.textSecondary,
        textAlign = textAlign,
        modifier = modifier,
    )
}
