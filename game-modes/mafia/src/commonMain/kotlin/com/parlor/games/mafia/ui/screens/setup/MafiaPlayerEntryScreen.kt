package com.parlor.games.mafia.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.bringIntoViewOnFocus
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.player_entry_continue
import com.parlor.games.mafia.resources.player_entry_continue_description
import com.parlor.games.mafia.resources.player_entry_field_format
import com.parlor.games.mafia.resources.player_entry_headline
import com.parlor.games.mafia.resources.setup_eyebrow
import com.parlor.networking.room.RoomInputPolicy
import org.jetbrains.compose.resources.stringResource

@Composable
fun MafiaPlayerEntryScreen(
    playerCount: Int,
    onConfirm: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = remember(playerCount) {
        mutableStateListOf<String>().apply { repeat(playerCount) { add("") } }
    }
    val normalizedNames = names.map(RoomInputPolicy::normalizeDisplayName)

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(text = stringResource(Res.string.setup_eyebrow), accent = false)
            Text(
                text = stringResource(Res.string.player_entry_headline),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )

            names.forEachIndexed { index, current ->
                NameField(
                    index = index,
                    value = current,
                    onValueChange = { names[index] = it },
                    isLast = index == names.lastIndex,
                )
            }

            ParlorButton(
                label = stringResource(Res.string.player_entry_continue),
                contentDescription = stringResource(Res.string.player_entry_continue_description),
                onClick = { onConfirm(normalizedNames) },
                enabled = normalizedNames.all(RoomInputPolicy::isValidDisplayName) &&
                    normalizedNames.toSet().size == normalizedNames.size,
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
    isLast: Boolean,
) {
    val label = stringResource(Res.string.player_entry_field_format, index + 1)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(RoomInputPolicy.sanitizeDisplayNameInput(input))
        },
        label = { Text(label, style = ParlorTheme.typography.labelMedium) },
        singleLine = true,
        textStyle = ParlorTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        ),
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
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
}
