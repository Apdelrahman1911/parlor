package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.parlor.app.ResumeDeletionState
import com.parlor.app.resources.Res
import com.parlor.app.resources.resume_delete_title
import com.parlor.app.resources.resume_delete_local_body
import com.parlor.app.resources.resume_delete_multiplayer_body
import com.parlor.app.resources.resume_delete_confirm
import com.parlor.app.resources.resume_delete_cancel
import com.parlor.app.resources.resume_delete_failed
import com.parlor.app.resources.resume_delete_busy
import com.parlor.app.ResumeDeletionTarget
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

/** Shared confirmation is scrollable at accessibility font sizes and never exposes private state. */
@Composable
internal fun ResumeDeletionDialog(
    state: ResumeDeletionState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val request = state.request ?: return
    AlertDialog(
        onDismissRequest = { if (!state.busy) onCancel() },
        title = { Text(stringResource(Res.string.resume_delete_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                Text(request.label)
                Text(stringResource(
                    if (request.target is ResumeDeletionTarget.Local) Res.string.resume_delete_local_body
                    else Res.string.resume_delete_multiplayer_body,
                ))
                if (state.failed || state.busy) {
                    Text(
                        text = stringResource(
                            if (state.failed) Res.string.resume_delete_failed else Res.string.resume_delete_busy,
                        ),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("resume-delete-status"),
                    )
                }
            }
        },
        confirmButton = {
            ParlorButton(
                label = stringResource(Res.string.resume_delete_confirm),
                contentDescription = stringResource(Res.string.resume_delete_confirm),
                variant = ParlorButtonVariant.Destructive,
                onClick = onConfirm,
                enabled = !state.busy,
                modifier = Modifier.testTag("resume-delete-confirm"),
            )
        },
        dismissButton = {
            ParlorButton(
                label = stringResource(Res.string.resume_delete_cancel),
                contentDescription = stringResource(Res.string.resume_delete_cancel),
                onClick = onCancel,
                enabled = !state.busy,
                variant = ParlorButtonVariant.Ghost,
                modifier = Modifier.testTag("resume-delete-cancel"),
            )
        },
    )
}
