package com.parlor.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    textStyle: TextStyle = ParlorTheme.typography.bodyLarge,
) {
    val colors = ParlorTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = ParlorTheme.typography.labelMedium) },
        singleLine = singleLine,
        textStyle = textStyle,
        shape = RoundedCornerShape(ParlorTheme.radii.card),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surfaceElevated,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.accentEmber,
            focusedBorderColor = colors.accentEmber,
            unfocusedBorderColor = colors.borderElevated,
            focusedLabelColor = colors.accentEmber,
            unfocusedLabelColor = colors.textSecondary,
        ),
        modifier = modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
}
