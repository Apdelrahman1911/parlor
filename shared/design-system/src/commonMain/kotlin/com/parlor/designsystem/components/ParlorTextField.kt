package com.parlor.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Themed text input. Replaces every screen's per-screen `OutlinedTextField` +
 * `TextFieldDefaults.colors(...)` blob with one consistent treatment.
 *
 * Colors / typography / shape all come from tokens. Light + dark modes both
 * resolve the right surface and text colour without per-screen branching.
 */
@Composable
fun ParlorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val colors = ParlorTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = ParlorTheme.typography.labelMedium) },
        singleLine = singleLine,
        textStyle = ParlorTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            imeAction = imeAction,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surfaceElevated,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.accentEmber,
            focusedIndicatorColor = colors.accentEmber,
            unfocusedIndicatorColor = colors.borderElevated,
            focusedLabelColor = colors.accentEmber,
            unfocusedLabelColor = colors.textSecondary,
        ),
        modifier = modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
}
