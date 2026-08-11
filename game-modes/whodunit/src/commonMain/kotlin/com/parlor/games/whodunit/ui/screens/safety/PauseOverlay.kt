package com.parlor.games.whodunit.ui.screens.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.endgame_body
import com.parlor.games.whodunit.resources.endgame_cancel
import com.parlor.games.whodunit.resources.endgame_cancel_description
import com.parlor.games.whodunit.resources.endgame_end_now
import com.parlor.games.whodunit.resources.endgame_end_now_description
import com.parlor.games.whodunit.resources.endgame_title
import com.parlor.games.whodunit.resources.pause_body
import com.parlor.games.whodunit.resources.pause_end_game
import com.parlor.games.whodunit.resources.pause_end_game_description
import com.parlor.games.whodunit.resources.pause_resume
import com.parlor.games.whodunit.resources.pause_resume_description
import com.parlor.games.whodunit.resources.pause_resume_later
import com.parlor.games.whodunit.resources.pause_resume_later_description
import com.parlor.games.whodunit.resources.pause_title
import org.jetbrains.compose.resources.stringResource

/**
 * Fullscreen pause overlay rendered on top of the current in-game screen.
 *
 * Player-visible actions:
 * - **Resume** — submit `Resume` action; lift the pause.
 * - **Resume Later** — shown only when [onResumeLater] is non-null. Local
 *   sessions flush a real snapshot before returning home; multiplayer does
 *   not offer this action because its host session is not persisted.
 * - **End Game** — open a confirmation that deletes the snapshot and returns
 *   to Home.
 *
 * The caller replaces the in-game screen with this fullscreen modal, so no
 * private gameplay content or stale action remains exposed behind it. Players
 * must choose one of the actions.
 */
@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onResumeLater: (() -> Unit)?,
    onEndNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var endConfirmOpen by remember { mutableStateOf(false) }
    if (endConfirmOpen) {
        EndGameConfirmDialog(
            onConfirmEndNow = {
                endConfirmOpen = false
                onEndNow()
            },
            onCancel = { endConfirmOpen = false },
            modifier = modifier,
        )
        return
    }

    val title = stringResource(Res.string.pause_title)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { paneTitle = title },
    ) {
        // Dim the background. The pause card sits in the center.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ParlorTheme.colors.overlayScrim),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xl,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                ) {
                    EyebrowLabel(
                        text = title,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(Res.string.pause_body),
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
                    ParlorButton(
                        label = stringResource(Res.string.pause_resume),
                        contentDescription = stringResource(Res.string.pause_resume_description),
                        onClick = onResume,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (onResumeLater != null) {
                        ParlorButton(
                            label = stringResource(Res.string.pause_resume_later),
                            contentDescription = stringResource(
                                Res.string.pause_resume_later_description,
                            ),
                            onClick = onResumeLater,
                            modifier = Modifier.fillMaxWidth(),
                            variant = ParlorButtonVariant.Secondary,
                        )
                    }
                    ParlorButton(
                        label = stringResource(Res.string.pause_end_game),
                        contentDescription = stringResource(Res.string.pause_end_game_description),
                        onClick = { endConfirmOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ParlorButtonVariant.Destructive,
                    )
                }
            }
        }
    }
}

@Composable
private fun EndGameConfirmDialog(
    onConfirmEndNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(Res.string.endgame_title)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { paneTitle = title },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ParlorTheme.colors.overlayScrim),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.xl,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                    Text(
                        text = title,
                        style = ParlorTheme.typography.displayMedium,
                        color = ParlorTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { heading() },
                    )
                    Text(
                        text = stringResource(Res.string.endgame_body),
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(ParlorTheme.spacing.s))
                    ParlorButton(
                        label = stringResource(Res.string.endgame_end_now),
                        contentDescription = stringResource(Res.string.endgame_end_now_description),
                        onClick = onConfirmEndNow,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ParlorButtonVariant.Destructive,
                    )
                    ParlorButton(
                        label = stringResource(Res.string.endgame_cancel),
                        contentDescription = stringResource(Res.string.endgame_cancel_description),
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ParlorButtonVariant.Ghost,
                    )
                }
            }
        }
    }
}
