package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_player_entry_confirm
import com.parlor.games.whodunit.resources.setup_player_entry_confirm_description
import com.parlor.games.whodunit.resources.setup_player_entry_eyebrow
import com.parlor.games.whodunit.resources.setup_player_entry_field_format
import com.parlor.games.whodunit.resources.setup_player_entry_headline
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerEntryScreen(
    playerCount: Int,
    onConfirm: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = remember(playerCount) {
        mutableStateListOf<String>().apply { repeat(playerCount) { add("") } }
    }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.setup_player_entry_eyebrow),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(Res.string.setup_player_entry_headline),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )

            names.forEachIndexed { index, current ->
                NameField(
                    index = index,
                    value = current,
                    onValueChange = { names[index] = it },
                )
            }

            ParlorButton(
                label = stringResource(Res.string.setup_player_entry_confirm),
                contentDescription = stringResource(Res.string.setup_player_entry_confirm_description),
                onClick = { onConfirm(names.toList()) },
                enabled = names.all { it.isNotBlank() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NameField(
    index: Int,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val label = stringResource(Res.string.setup_player_entry_field_format, index + 1)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = ParlorTheme.typography.labelMedium) },
        singleLine = true,
        textStyle = ParlorTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ParlorTheme.colors.surfaceElevated,
            unfocusedContainerColor = ParlorTheme.colors.surfaceElevated,
            focusedTextColor = ParlorTheme.colors.textPrimary,
            unfocusedTextColor = ParlorTheme.colors.textPrimary,
            cursorColor = ParlorTheme.colors.accentEmber,
            focusedIndicatorColor = ParlorTheme.colors.accentEmber,
            unfocusedIndicatorColor = ParlorTheme.colors.borderElevated,
            focusedLabelColor = ParlorTheme.colors.accentEmber,
            unfocusedLabelColor = ParlorTheme.colors.textSecondary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
