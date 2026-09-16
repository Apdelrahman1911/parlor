package com.parlor.games.lastlight.ui.flow.passandplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.bringIntoViewOnFocus
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.local_action_rejected
import com.parlor.games.lastlight.resources.local_back
import com.parlor.games.lastlight.resources.local_handoff_body
import com.parlor.games.lastlight.resources.local_handoff_continue
import com.parlor.games.lastlight.resources.local_handoff_title
import com.parlor.games.lastlight.resources.local_loading
import com.parlor.games.lastlight.resources.local_player_add
import com.parlor.games.lastlight.resources.local_player_label
import com.parlor.games.lastlight.resources.local_player_remove
import com.parlor.games.lastlight.resources.local_recovery_body
import com.parlor.games.lastlight.resources.local_recovery_discard
import com.parlor.games.lastlight.resources.local_recovery_discard_failed
import com.parlor.games.lastlight.resources.local_recovery_retry
import com.parlor.games.lastlight.resources.local_recovery_title
import com.parlor.games.lastlight.resources.local_setup_body
import com.parlor.games.lastlight.resources.local_setup_duplicate
import com.parlor.games.lastlight.resources.local_setup_eyebrow
import com.parlor.games.lastlight.resources.local_setup_start
import com.parlor.games.lastlight.resources.local_setup_title
import com.parlor.games.lastlight.ui.theme.DeckButton
import com.parlor.games.lastlight.ui.theme.LastLightColors
import com.parlor.networking.room.RoomInputPolicy
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LastLightLocalSetupScreen(
    names: List<String>,
    onNamesChanged: (List<String>) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val normalized = names.map(RoomInputPolicy::normalizeDisplayName)
    val duplicate = normalized.filter(String::isNotBlank).let { it.size != it.toSet().size }
    LocalPage(modifier) {
        EyebrowLabel(stringResource(Res.string.local_setup_eyebrow))
        LocalHeading(stringResource(Res.string.local_setup_title))
        Text(
            stringResource(Res.string.local_setup_body),
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textSecondary,
        )
        names.forEachIndexed { index, name ->
            PlayerNameField(
                index = index,
                value = name,
                isLast = index == names.lastIndex,
                enabled = actionsEnabled,
                onValueChange = { next -> onNamesChanged(names.toMutableList().also { it[index] = next }) },
            )
        }
        if (duplicate) {
            Text(stringResource(Res.string.local_setup_duplicate), color = ParlorTheme.colors.textSecondary)
        }
        if (names.size < 6) {
            LocalButton(Res.string.local_player_add, { onNamesChanged(names + "") }, actionsEnabled, secondary = true)
        }
        if (names.size > 2) {
            LocalButton(Res.string.local_player_remove, { onNamesChanged(names.dropLast(1)) }, actionsEnabled, secondary = true)
        }
        LocalButton(
            Res.string.local_setup_start,
            onStart,
            actionsEnabled && names.size in 2..6 && RoomInputPolicy.areValidDistinctDisplayNames(normalized),
        )
        LocalButton(Res.string.local_back, onBack, actionsEnabled, secondary = true)
    }
}

@Composable
private fun PlayerNameField(
    index: Int,
    value: String,
    isLast: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(RoomInputPolicy.sanitizeDisplayNameInput(it)) },
        label = { Text(stringResource(Res.string.local_player_label, index + 1)) },
        enabled = enabled,
        singleLine = true,
        textStyle = ParlorTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { focusManager.clearFocus(); keyboardController?.hide() },
        ),
        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
    )
}

@Composable
internal fun LastLightHandoffScreen(
    playerName: String,
    enabled: Boolean,
    rejected: Boolean,
    onTakeDevice: () -> Unit,
) {
    val title = stringResource(Res.string.local_handoff_title, playerName)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .semantics { paneTitle = title },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = LastLightColors.Paper,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(Res.string.local_handoff_body),
            style = MaterialTheme.typography.bodyLarge,
            color = LastLightColors.Muted,
            textAlign = TextAlign.Center,
        )
        if (rejected) {
            Text(
                stringResource(Res.string.local_action_rejected),
                color = LastLightColors.Error,
                textAlign = TextAlign.Center,
            )
        }
        DeckButton(
            text = stringResource(Res.string.local_handoff_continue, playerName),
            onClick = onTakeDevice,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun LastLightRecoveryScreen(
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    actionsEnabled: Boolean,
    discardFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    LocalPage(modifier) {
        LocalHeading(stringResource(Res.string.local_recovery_title))
        Text(
            stringResource(Res.string.local_recovery_body),
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textSecondary,
        )
        if (discardFailed) {
            Text(stringResource(Res.string.local_recovery_discard_failed), color = ParlorTheme.colors.textSecondary)
        }
        LocalButton(Res.string.local_recovery_retry, onRetry, actionsEnabled)
        LocalButton(Res.string.local_recovery_discard, onDiscard, actionsEnabled, secondary = true)
        LocalButton(Res.string.local_back, onBack, actionsEnabled, secondary = true)
    }
}

@Composable
internal fun LastLightLoadingScreen(modifier: Modifier = Modifier) {
    LocalPage(modifier) { Text(stringResource(Res.string.local_loading), color = ParlorTheme.colors.textSecondary) }
}

@Composable
private fun LocalPage(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    HeroBackdrop(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun LocalHeading(text: String) {
    Text(
        text,
        style = ParlorTheme.typography.displayMedium,
        color = ParlorTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun LocalButton(
    resource: org.jetbrains.compose.resources.StringResource,
    onClick: () -> Unit,
    enabled: Boolean,
    secondary: Boolean = false,
) {
    val label = stringResource(resource)
    ParlorButton(
        label = label,
        contentDescription = label,
        onClick = onClick,
        enabled = enabled,
        variant = if (secondary) ParlorButtonVariant.Ghost else ParlorButtonVariant.Primary,
        modifier = Modifier.fillMaxWidth(),
    )
}
