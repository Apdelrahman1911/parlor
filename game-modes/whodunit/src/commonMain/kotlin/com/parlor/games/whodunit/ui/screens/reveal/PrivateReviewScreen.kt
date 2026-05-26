package com.parlor.games.whodunit.ui.screens.reveal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.domain.state.PlayerRole

/**
 * Private Review Mode (design doc §9):
 *
 * 1. Player taps their own name in the roster.
 * 2. The app shows a **cover screen** — "[Name], private review. Make sure no one can see."
 * 3. Player presses and holds the wax seal (1.5 s).
 * 4. The dossier reappears (Must Read by default; Optional Details collapsed).
 * 5. Player taps "I'm Done" → black hide screen.
 * 6. A second tap returns to the round.
 */
@Composable
fun PrivateReviewScreen(
    playerName: String,
    character: Character,
    role: PlayerRole,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    allCharacters: List<Character> = emptyList(),
) {
    var stage by remember { mutableStateOf(Stage.Cover) }

    when (stage) {
        Stage.Cover -> CharacterRevealHandoffScreen(
            playerName = playerName,
            onContinue = { stage = Stage.Gate },
            modifier = modifier,
        )
        Stage.Gate -> CharacterRevealGateScreen(
            playerName = playerName,
            onRevealed = { stage = Stage.Dossier },
            modifier = modifier,
        )
        Stage.Dossier -> DossierRevealScreen(
            character = character,
            role = role,
            onDone = { stage = Stage.Hide },
            modifier = modifier,
            allCharacters = allCharacters,
        )
        Stage.Hide -> HideAndPassScreen(
            nextPlayerName = null,
            onTap = onClose,
            modifier = modifier,
        )
    }
}

private enum class Stage { Cover, Gate, Dossier, Hide }
