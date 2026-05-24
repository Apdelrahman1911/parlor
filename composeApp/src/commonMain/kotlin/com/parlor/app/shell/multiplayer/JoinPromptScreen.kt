package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.parlor.app.resources.Res
import com.parlor.app.resources.join_cancel
import com.parlor.app.resources.join_cancel_description
import com.parlor.app.resources.join_code_field
import com.parlor.app.resources.join_code_help
import com.parlor.app.resources.join_confirm
import com.parlor.app.resources.join_confirm_description
import com.parlor.app.resources.join_eyebrow
import com.parlor.app.resources.join_title
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.theme.ParlorTheme
import androidx.compose.foundation.text.KeyboardOptions
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 8 join prompt — minimal code-entry screen. Submits the entered code
 * to [onConfirm], which kicks off `RoomTransport.join(code, displayName)`
 * and transitions to [PeerLobbyScreen] on success.
 */
@Composable
fun JoinPromptScreen(
    onConfirm: (code: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            EyebrowLabel(text = stringResource(Res.string.join_eyebrow))
            Text(
                text = stringResource(Res.string.join_title),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.join_code_help),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.m))

            OutlinedTextField(
                value = code,
                onValueChange = { input -> code = input.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                label = { Text(stringResource(Res.string.join_code_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))

            ParlorButton(
                label = stringResource(Res.string.join_confirm),
                contentDescription = stringResource(Res.string.join_confirm_description),
                onClick = { onConfirm(code) },
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.join_cancel),
                contentDescription = stringResource(Res.string.join_cancel_description),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        }
    }
}
