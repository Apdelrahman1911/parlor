package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.theme.ParlorTheme

/**
 * Player Entry — each player types their own name. Used throughout the game
 * ("Pass to Eleanor" not "Pass to Player 3"). Phase 4: simple, premium-feeling
 * input rows. Phase 6 adds unicode-name QA.
 */
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
                text = "ENTER YOUR NAMES",
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = "Pass the phone. Each player types theirs.",
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
                label = "Begin",
                contentDescription = "Confirm player names and continue to the case intro.",
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Player ${index + 1}", style = ParlorTheme.typography.labelMedium) },
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
