package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorTextField
import com.parlor.designsystem.components.ScreenHeader
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
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            ScreenHeader(
                title = stringResource(Res.string.join_title),
                eyebrow = stringResource(Res.string.join_eyebrow),
                subtitle = stringResource(Res.string.join_code_help),
                onBack = onCancel,
                backContentDescription = stringResource(Res.string.join_cancel_description),
            )

            ParlorTextField(
                value = code,
                onValueChange = { input -> code = input.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                label = stringResource(Res.string.join_code_field),
                capitalization = KeyboardCapitalization.Characters,
            )

            Spacer(modifier = Modifier.height(ParlorTheme.spacing.l))

            ParlorButton(
                label = stringResource(Res.string.join_confirm),
                contentDescription = stringResource(Res.string.join_confirm_description),
                onClick = { onConfirm(code) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
