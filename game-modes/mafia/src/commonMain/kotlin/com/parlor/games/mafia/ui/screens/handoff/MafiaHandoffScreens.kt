package com.parlor.games.mafia.ui.screens.handoff

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
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.handoff_hide_screen
import com.parlor.games.mafia.resources.handoff_keep_hidden
import com.parlor.games.mafia.resources.handoff_pass_to_format
import com.parlor.games.mafia.resources.handoff_reveal
import com.parlor.games.mafia.resources.handoff_reveal_description_format
import com.parlor.games.mafia.resources.handoff_tap_when_alone
import com.parlor.games.mafia.resources.waiting_active_player_taking_turn_format
import com.parlor.games.mafia.resources.waiting_night_eyebrow
import com.parlor.games.mafia.resources.waiting_wait_quietly
import com.parlor.games.mafia.ui.components.MafiaCandlelitCover
import com.parlor.games.mafia.ui.components.MafiaHideScreen
import org.jetbrains.compose.resources.stringResource

/**
 * Pass-the-phone cover: shows the next player's name and asks the device
 * to be handed over. Tapping dismisses and reveals the [MafiaRoleRevealGateScreen].
 */
@Composable
fun MafiaHideAndPassScreen(
    nextPlayerName: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = if (nextPlayerName != null) {
        stringResource(Res.string.handoff_pass_to_format, nextPlayerName)
    } else {
        stringResource(Res.string.handoff_hide_screen)
    }
    MafiaHideScreen(line = line, onTap = onTap, modifier = modifier)
}

/**
 * Greets the named player and prompts them to confirm they are holding the
 * device alone before revealing private content.
 */
@Composable
fun MafiaRoleRevealHandoffScreen(
    playerName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MafiaCandlelitCover(
        title = playerName,
        subtitle = stringResource(Res.string.handoff_tap_when_alone),
        onDismiss = onContinue,
        modifier = modifier,
    )
}

/**
 * Final confirmation before private content is shown. Mafia uses a plain
 * "Reveal" button rather than the press-and-hold wax seal Whodunit uses,
 * keeping the night-themed flow simpler.
 */
@Composable
fun MafiaRoleRevealGateScreen(
    playerName: String,
    eyebrow: String,
    onRevealed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround,
        ) {
            EyebrowLabel(text = eyebrow, textAlign = TextAlign.Center)
            Text(
                text = playerName,
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.handoff_keep_hidden),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            ParlorButton(
                label = stringResource(Res.string.handoff_reveal),
                onClick = onRevealed,
                contentDescription = stringResource(Res.string.handoff_reveal_description_format, playerName),
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Primary,
            )
        }
    }
}

/**
 * Multi-device waiting screen — peers see this when the active private
 * segment belongs to another player. No action affordance: cleared when
 * the host advances state.
 */
@Composable
fun MafiaPrivateWaitingScreen(
    activePlayerName: String,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EyebrowLabel(text = stringResource(Res.string.waiting_night_eyebrow), textAlign = TextAlign.Center)
            Text(
                text = stringResource(Res.string.waiting_active_player_taking_turn_format, activePlayerName),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ParlorTheme.spacing.m),
            )
            Text(
                text = stringResource(Res.string.waiting_wait_quietly),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ParlorTheme.spacing.s),
            )
        }
    }
}
