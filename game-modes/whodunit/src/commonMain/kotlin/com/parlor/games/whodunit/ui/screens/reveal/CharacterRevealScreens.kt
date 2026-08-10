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
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.core.ids.CharacterId
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
 * Multi-device waiting surface shown after this device has confirmed its own
 * dossier while another active player is still completing the simultaneous
 * reveal ceremony. There is no action affordance: the screen clears from the
 * next authoritative snapshot once all required confirmations arrive.
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
            EyebrowLabel(
                text = stringResource(Res.string.reveal_waiting_eyebrow),
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
    allCharacters: List<Character> = emptyList(),
    deflectionTargets: List<CharacterId> = emptyList(),
) {
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        DossierCard(
            character = character,
            role = role,
            onDone = onDone,
            modifier = Modifier
                .fillMaxSize()
                .padding(ParlorTheme.spacing.m),
            allCharacters = allCharacters,
            deflectionTargets = deflectionTargets,
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
