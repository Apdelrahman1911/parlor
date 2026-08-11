package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.session_exit_affordance
import parlor.shared.design_system.generated.resources.session_exit_affordance_description
import parlor.shared.design_system.generated.resources.session_exit_host_body
import parlor.shared.design_system.generated.resources.session_exit_host_confirm
import parlor.shared.design_system.generated.resources.session_exit_host_confirm_description
import parlor.shared.design_system.generated.resources.session_exit_host_title
import parlor.shared.design_system.generated.resources.session_exit_local_body
import parlor.shared.design_system.generated.resources.session_exit_local_confirm
import parlor.shared.design_system.generated.resources.session_exit_local_confirm_description
import parlor.shared.design_system.generated.resources.session_exit_local_title
import parlor.shared.design_system.generated.resources.session_exit_peer_body
import parlor.shared.design_system.generated.resources.session_exit_peer_confirm
import parlor.shared.design_system.generated.resources.session_exit_peer_confirm_description
import parlor.shared.design_system.generated.resources.session_exit_peer_title
import parlor.shared.design_system.generated.resources.session_exit_stay
import parlor.shared.design_system.generated.resources.session_exit_stay_description

enum class SessionExitKind { Local, Host, Peer }

enum class SessionExitBackAction { ExitImmediately, Confirm }

/**
 * Pure shell policy used by every game. Local sessions only call this once a
 * controller exists, so Back must preserve the save transaction. Multiplayer
 * setup can exit immediately; after start, destructive membership/room
 * cleanup requires an explicit confirmation.
 */
fun sessionExitBackAction(
    kind: SessionExitKind,
    gameHasStarted: Boolean,
): SessionExitBackAction = when {
    kind == SessionExitKind.Local -> SessionExitBackAction.Confirm
    gameHasStarted -> SessionExitBackAction.Confirm
    else -> SessionExitBackAction.ExitImmediately
}

/** Visible, 52dp-minimum escape route for game surfaces with no native Back. */
@Composable
fun SessionExitAffordance(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParlorButton(
        label = stringResource(Res.string.session_exit_affordance),
        contentDescription = stringResource(Res.string.session_exit_affordance_description),
        onClick = onClick,
        modifier = modifier,
        variant = ParlorButtonVariant.Secondary,
    )
}

/**
 * Opaque session-exit confirmation. Callers render this *instead of* the game
 * surface so private role/action semantics and pixels are not left exposed
 * behind a modal. The caller owns the save/revoke/terminate transaction.
 */
@Composable
fun SessionExitConfirmation(
    kind: SessionExitKind,
    onStay: () -> Unit,
    onExit: () -> Unit,
    exitInFlight: Boolean,
    destructive: Boolean,
    modifier: Modifier = Modifier,
) {
    val copy = kind.resources()
    val title = stringResource(copy.title)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ParlorTheme.colors.coverScreen)
            .semantics { paneTitle = title }
            .verticalScroll(rememberScrollState())
            .padding(ParlorTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(
            ParlorTheme.spacing.l,
            Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = ParlorTheme.typography.displayMedium,
            color = ParlorTheme.colors.coverScreenTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(copy.body),
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.coverScreenTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        ParlorButton(
            label = stringResource(Res.string.session_exit_stay),
            contentDescription = stringResource(Res.string.session_exit_stay_description),
            onClick = onStay,
            enabled = !exitInFlight,
            modifier = Modifier.fillMaxWidth(),
            variant = ParlorButtonVariant.Secondary,
        )
        ParlorButton(
            label = stringResource(copy.confirm),
            contentDescription = stringResource(copy.confirmDescription),
            onClick = onExit,
            enabled = !exitInFlight,
            loading = exitInFlight,
            modifier = Modifier.fillMaxWidth(),
            variant = if (destructive) {
                ParlorButtonVariant.Destructive
            } else {
                ParlorButtonVariant.Primary
            },
        )
    }
}

private data class SessionExitResources(
    val title: StringResource,
    val body: StringResource,
    val confirm: StringResource,
    val confirmDescription: StringResource,
)

private fun SessionExitKind.resources(): SessionExitResources = when (this) {
    SessionExitKind.Local -> SessionExitResources(
        title = Res.string.session_exit_local_title,
        body = Res.string.session_exit_local_body,
        confirm = Res.string.session_exit_local_confirm,
        confirmDescription = Res.string.session_exit_local_confirm_description,
    )
    SessionExitKind.Host -> SessionExitResources(
        title = Res.string.session_exit_host_title,
        body = Res.string.session_exit_host_body,
        confirm = Res.string.session_exit_host_confirm,
        confirmDescription = Res.string.session_exit_host_confirm_description,
    )
    SessionExitKind.Peer -> SessionExitResources(
        title = Res.string.session_exit_peer_title,
        body = Res.string.session_exit_peer_body,
        confirm = Res.string.session_exit_peer_confirm,
        confirmDescription = Res.string.session_exit_peer_confirm_description,
    )
}
