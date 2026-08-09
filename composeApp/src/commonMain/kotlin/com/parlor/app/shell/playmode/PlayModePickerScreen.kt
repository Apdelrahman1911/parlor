package com.parlor.app.shell.playmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellCapabilities
import com.parlor.app.resources.Res
import com.parlor.app.resources.playmode_passandplay_body
import com.parlor.app.resources.playmode_passandplay_choose
import com.parlor.app.resources.playmode_passandplay_choose_description
import com.parlor.app.resources.playmode_passandplay_meta
import com.parlor.app.resources.playmode_passandplay_title
import com.parlor.app.resources.playmode_solo_body
import com.parlor.app.resources.playmode_solo_choose
import com.parlor.app.resources.playmode_solo_choose_description
import com.parlor.app.resources.playmode_solo_meta
import com.parlor.app.resources.playmode_solo_title
import com.parlor.app.resources.setup_back_description
import com.parlor.app.resources.setup_eyebrow
import com.parlor.app.resources.setup_host_body
import com.parlor.app.resources.setup_host_choose
import com.parlor.app.resources.setup_host_choose_description
import com.parlor.app.resources.setup_host_meta
import com.parlor.app.resources.setup_host_title
import com.parlor.app.resources.setup_join_body
import com.parlor.app.resources.setup_join_choose
import com.parlor.app.resources.setup_join_choose_description
import com.parlor.app.resources.setup_join_meta
import com.parlor.app.resources.setup_join_title
import com.parlor.app.resources.setup_multiplayer_disabled
import com.parlor.app.resources.setup_subtitle
import com.parlor.app.resources.setup_title
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.session.PlayMode
import org.jetbrains.compose.resources.stringResource

internal data class PlayModePickerAvailability(
    val solo: Boolean,
    val passAndPlay: Boolean,
    val host: Boolean,
    val join: Boolean,
)

internal fun GameShellCapabilities.toPlayModePickerAvailability() = PlayModePickerAvailability(
    solo = supports(GameEntryMode.Solo),
    passAndPlay = supports(GameEntryMode.PassAndPlay),
    host = supports(GameEntryMode.Host),
    join = supports(GameEntryMode.Join),
)

/**
 * Game setup — the single decision point after Home. Renders four cards so
 * a new user can see every option at once and pick a single tap:
 *
 *  - **Solo** — one phone, one person; skips hand-off ceremony.
 *  - **Pass and Play** — one phone, multiple players around a table.
 *  - **Host a Room** — multi-device; this device opens the room.
 *  - **Join a Room** — multi-device; this device joins a room code.
 *
 * Solo/Pass-and-Play emit a [PlayMode] via [onModeSelected] and the registered
 * game binding chooses its next setup step. Host/Join are routed via
 * [onHost]/[onJoin] because each binding owns its multiplayer setup flow.
 * Every card is enabled from the registered game's independent shell
 * capability. Keeping Host and Join separate is important for transports and
 * fixtures that intentionally support only one side of the topology.
 * Unsupported cards stay visible but disabled so players understand what is
 * unavailable in this build.
 *
 * The screen replaces the old per-device-mode picker that lived between
 * the case picker and the game; the case picker now comes *after* this
 * decision in the single-device branches.
 */
@Composable
internal fun PlayModePickerScreen(
    onModeSelected: (PlayMode) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    capabilities: GameShellCapabilities,
) {
    val availability = capabilities.toPlayModePickerAvailability()
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            ScreenHeader(
                title = stringResource(Res.string.setup_title),
                eyebrow = stringResource(Res.string.setup_eyebrow),
                subtitle = stringResource(Res.string.setup_subtitle),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.setup_back_description),
            )

            SetupCard(
                title = stringResource(Res.string.playmode_solo_title),
                body = stringResource(Res.string.playmode_solo_body),
                meta = stringResource(Res.string.playmode_solo_meta),
                buttonLabel = stringResource(Res.string.playmode_solo_choose),
                buttonDescription = stringResource(Res.string.playmode_solo_choose_description),
                onClick = { onModeSelected(PlayMode.Solo) },
                modifier = Modifier.fillMaxWidth(),
                enabled = availability.solo,
            )

            SetupCard(
                title = stringResource(Res.string.playmode_passandplay_title),
                body = stringResource(Res.string.playmode_passandplay_body),
                meta = stringResource(Res.string.playmode_passandplay_meta),
                buttonLabel = stringResource(Res.string.playmode_passandplay_choose),
                buttonDescription = stringResource(Res.string.playmode_passandplay_choose_description),
                onClick = { onModeSelected(PlayMode.PassAndPlay) },
                modifier = Modifier.fillMaxWidth(),
                enabled = availability.passAndPlay,
            )

            SetupCard(
                title = stringResource(Res.string.setup_host_title),
                body = stringResource(Res.string.setup_host_body),
                meta = stringResource(Res.string.setup_host_meta),
                buttonLabel = stringResource(Res.string.setup_host_choose),
                buttonDescription = stringResource(Res.string.setup_host_choose_description),
                onClick = onHost,
                modifier = Modifier.fillMaxWidth(),
                enabled = availability.host,
                disabledHint = if (availability.host) {
                    null
                } else {
                    stringResource(Res.string.setup_multiplayer_disabled)
                },
            )

            SetupCard(
                title = stringResource(Res.string.setup_join_title),
                body = stringResource(Res.string.setup_join_body),
                meta = stringResource(Res.string.setup_join_meta),
                buttonLabel = stringResource(Res.string.setup_join_choose),
                buttonDescription = stringResource(Res.string.setup_join_choose_description),
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                enabled = availability.join,
                // Host card already explains; keep the Join card terse.
                disabledHint = null,
                buttonVariant = ParlorButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    body: String,
    meta: String,
    buttonLabel: String,
    buttonDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledHint: String? = null,
    buttonVariant: ParlorButtonVariant = ParlorButtonVariant.Primary,
) {
    ParlorCard(
        modifier = modifier.fillMaxWidth(),
        elevation = ParlorTheme.elevation.dramatic,
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = ParlorTheme.spacing.xl,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                text = title,
                style = ParlorTheme.typography.displayLarge,
                color = if (enabled) ParlorTheme.colors.textPrimary else ParlorTheme.colors.textTertiary,
            )
            Text(
                text = body,
                style = ParlorTheme.typography.bodyLarge,
                color = if (enabled) ParlorTheme.colors.textSecondary else ParlorTheme.colors.textTertiary,
            )
            Text(
                text = meta,
                style = ParlorTheme.typography.labelMedium,
                color = if (enabled) ParlorTheme.colors.accentEmber else ParlorTheme.colors.textTertiary,
            )
            if (disabledHint != null) {
                Text(
                    text = disabledHint,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textTertiary,
                )
            }
            ParlorButton(
                label = buttonLabel,
                contentDescription = buttonDescription,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                variant = buttonVariant,
            )
        }
    }
}
