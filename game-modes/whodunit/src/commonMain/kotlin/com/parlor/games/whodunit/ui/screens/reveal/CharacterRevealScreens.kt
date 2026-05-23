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
import com.parlor.games.whodunit.ui.components.CandlelitCover
import com.parlor.games.whodunit.ui.components.DossierCard
import com.parlor.games.whodunit.ui.components.HideScreen
import com.parlor.games.whodunit.ui.components.WaxSealReveal

/**
 * Handoff cover — "Pass the phone to [Name]. Tap when ready."
 */
@Composable
fun CharacterRevealHandoffScreen(
    playerName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CandlelitCover(
        title = "Pass to $playerName.",
        subtitle = "When you're alone with the phone, tap to continue.",
        onDismiss = onContinue,
        modifier = modifier,
    )
}

/**
 * Wax-seal gate — the player presses and holds to reveal.
 */
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
                text = "$playerName,",
                style = ParlorTheme.typography.displayMedium,
                color = ParlorTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "find a quiet angle. Press and hold the seal.",
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            WaxSealReveal(
                label = "Hold for 1.5 seconds.",
                onRevealed = onRevealed,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Dossier reveal — Must Read + Optional Details. Card is the full screen, set
 * against the candlelit backdrop.
 */
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

/** Hide and pass — full-black with one line. */
@Composable
fun HideAndPassScreen(
    nextPlayerName: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = if (nextPlayerName != null) {
        "Hide the phone. Pass to $nextPlayerName when no one is watching."
    } else {
        "Hide the phone. Pass back to the table."
    }
    HideScreen(line = text, onTap = onTap, modifier = modifier)
}
