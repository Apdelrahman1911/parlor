package com.parlor.games.whodunit.ui.screens.setup

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
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.bringIntoViewOnFocus
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_back
import com.parlor.games.whodunit.resources.setup_back_description
import com.parlor.games.whodunit.resources.setup_player_entry_confirm
import com.parlor.games.whodunit.resources.setup_player_entry_confirm_description
import com.parlor.games.whodunit.resources.setup_player_entry_eyebrow
import com.parlor.games.whodunit.resources.setup_player_entry_field_format
import com.parlor.games.whodunit.resources.setup_player_entry_headline
import com.parlor.networking.room.RoomInputPolicy
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerEntryScreen(
    playerCount: Int,
    onConfirm: (List<String>) -> Unit,
    onBack: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.setup_player_entry_eyebrow),
                accent = false,
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
                    onValueChange = { input ->
                        names[index] = RoomInputPolicy.sanitizeDisplayNameInput(input)
                    },
                    isLast = index == names.lastIndex,
                )
            }

            ParlorButton(
                label = stringResource(Res.string.setup_player_entry_confirm),
                contentDescription = stringResource(Res.string.setup_player_entry_confirm_description),
                onClick = {
                    onConfirm(normalizedNames)
                },
                enabled = RoomInputPolicy.areValidDistinctDisplayNames(normalizedNames),
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.setup_back),
                contentDescription = stringResource(Res.string.setup_back_description),
                onClick = onBack,
                variant = ParlorButtonVariant.Ghost,
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
    val label = stringResource(Res.string.setup_player_entry_field_format, index + 1)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
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
