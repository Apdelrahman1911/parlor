package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.join_cancel
import com.parlor.app.resources.join_cancel_description
import com.parlor.app.resources.join_code_field
import com.parlor.app.resources.join_code_help
import com.parlor.app.resources.join_confirm
import com.parlor.app.resources.join_confirm_description
import com.parlor.app.resources.join_eyebrow
import com.parlor.app.resources.join_local_body
import com.parlor.app.resources.join_local_label
import com.parlor.app.resources.join_title
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorTextField
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.StickyActionBar
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.RoomInputPolicy
import org.jetbrains.compose.resources.stringResource

/**
 * Room-code entry shared by the shipping game-specific peer session flows.
 * [onConfirm] hands the validated code to the app shell, which starts the
 * authoritative join/resume and acknowledged-session-start workflow.
 */
@Composable
fun JoinPromptScreen(
    onConfirm: (code: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ParlorTheme.spacing.l,
                        end = ParlorTheme.spacing.l,
                        top = ParlorTheme.spacing.l,
                        bottom = ParlorTheme.spacing.xxxl + ParlorTheme.spacing.xxl,
                    ),
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
                    onValueChange = { input -> code = RoomInputPolicy.normalizeRoomCode(input) },
                    label = stringResource(Res.string.join_code_field),
                    capitalization = KeyboardCapitalization.Characters,
                    textStyle = ParlorTheme.typography.timerMedium.copy(
                        textAlign = TextAlign.Center,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                    ),
                )

                ParlorCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = ParlorTheme.spacing.m,
                    bordered = false,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
                        EyebrowLabel(
                            text = stringResource(Res.string.join_local_label),
                            accent = false,
                        )
                        Text(
                            text = stringResource(Res.string.join_local_body),
                            style = ParlorTheme.typography.bodySmall,
                            color = ParlorTheme.colors.textSecondary,
                        )
                    }
                }
            }

            StickyActionBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                ParlorButton(
                    label = stringResource(Res.string.join_confirm),
                    contentDescription = stringResource(Res.string.join_confirm_description),
                    onClick = { onConfirm(code) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = RoomInputPolicy.isValidRoomCode(code),
                )
            }
        }
    }
}
