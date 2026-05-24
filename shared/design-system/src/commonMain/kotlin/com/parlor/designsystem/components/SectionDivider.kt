package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Hairline section divider. Use to break sections inside long scroll views
 * where pure spacing isn't enough hierarchy.
 *
 * Pass [label] to surface a section eyebrow above the rule. With a label,
 * spacing tightens slightly so the label visually pairs with the divider.
 */
@Composable
fun SectionDivider(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            EyebrowLabel(text = label, accent = false)
            Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ParlorTheme.borders.hairline)
                .background(ParlorTheme.colors.borderSubtle),
        )
    }
}
