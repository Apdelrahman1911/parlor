package com.parlor.games.whodunit.ui.screens.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.reveal_gate_headline_format
import com.parlor.games.whodunit.resources.reveal_gate_hold_hint
import com.parlor.games.whodunit.resources.reveal_gate_instruction
import com.parlor.games.whodunit.resources.reveal_handoff_subtitle
import com.parlor.games.whodunit.resources.reveal_handoff_title_format
import com.parlor.games.whodunit.resources.reveal_hide_end
import com.parlor.games.whodunit.resources.reveal_hide_next_format
import com.parlor.games.whodunit.resources.reveal_waiting_eyebrow
import com.parlor.games.whodunit.resources.reveal_waiting_subtitle
import com.parlor.games.whodunit.resources.reveal_waiting_title_format
import com.parlor.games.whodunit.ui.components.CandlelitCover
import com.parlor.games.whodunit.ui.components.DossierCard
import com.parlor.games.whodunit.ui.components.HideScreen
import com.parlor.games.whodunit.ui.components.WaxSealReveal
import org.jetbrains.compose.resources.stringResource

@Composable
fun CharacterRevealHandoffScreen(
    playerName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CandlelitCover(
        title = stringResource(Res.string.reveal_handoff_title_format, playerName),
        subtitle = stringResource(Res.string.reveal_handoff_subtitle),
        onDismiss = onContinue,
        modifier = modifier,
    )
}

/**
 * Multi-device-only screen shown to peers when the current
 * [com.parlor.games.whodunit.domain.phase.WhodunitPhase.CharacterReveal] is
 * focused on **another** player. The local device is the one whose
 * `selfPlayerId` differs from `players[phase.playerIndex].id`. Each peer waits
 * on this screen until the host advances `phase.playerIndex` to point at
 * them; at that point the standard Handoff → Gate → Dossier → Hide
 * ceremony takes over locally.
 *
 * No action affordance — peers can't advance another player's reveal. The
 * screen auto-clears when state.phase moves.
 */
@Composable
fun CharacterRevealWaitingScreen(
    activePlayerName: String,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.reveal_waiting_eyebrow).uppercase(),
                style = ParlorTheme.typography.labelSmall,
                color = ParlorTheme.colors.accentEmber,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.reveal_waiting_title_format, activePlayerName),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ParlorTheme.spacing.m),
            )
            Text(
                text = stringResource(Res.string.reveal_waiting_subtitle),
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

@Composable
fun CharacterRevealGateScreen(
    playerName: String,
    onRevealed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround,
        ) {
            Text(
                text = stringResource(Res.string.reveal_gate_headline_format, playerName),
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.reveal_gate_instruction),
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            WaxSealReveal(
                label = stringResource(Res.string.reveal_gate_hold_hint),
                onRevealed = onRevealed,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun DossierRevealScreen(
    character: Character,
    role: PlayerRole,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        DossierCard(
            character = character,
            role = role,
            onDone = onDone,
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.m),
        )
    }
}

@Composable
fun HideAndPassScreen(
    nextPlayerName: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = if (nextPlayerName != null) {
        stringResource(Res.string.reveal_hide_next_format, nextPlayerName)
    } else {
        stringResource(Res.string.reveal_hide_end)
    }
    HideScreen(line = text, onTap = onTap, modifier = modifier)
}
