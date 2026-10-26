package com.parlor.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.parlor.designsystem.theme.ParlorTheme

/** A Latin room identifier, readable as one code in either UI direction at full text scaling. */
@Composable
fun RoomCodeText(code: String, modifier: Modifier = Modifier) {
    Text(
        text = code,
        modifier = modifier.fillMaxWidth(),
        style = ParlorTheme.typography.timerMedium.copy(textDirection = TextDirection.Ltr),
        color = ParlorTheme.colors.accentEmber,
        textAlign = TextAlign.Center,
    )
}
