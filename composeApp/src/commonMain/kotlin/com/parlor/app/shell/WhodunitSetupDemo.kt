package com.parlor.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.core.ids.ModeId
import com.parlor.games.whodunit.ui.screens.setup.ModeSelectionScreen
import com.parlor.games.whodunit.ui.screens.setup.PlayerCountDisplayStrategy
import com.parlor.games.whodunit.ui.screens.setup.PlayerCountScreen
import com.parlor.games.whodunit.ui.screens.setup.PlayerEntryScreen
import com.parlor.games.whodunit.ui.screens.setup.PublicIntroScreen
import com.parlor.games.whodunit.ui.screens.setup.RulesBriefingScreen

/**
 * Phase 4 demo wiring: strings the setup screens together using local state.
 *
 * Phase 4–5 replaces this with a proper Compose Navigation + ViewModel
 * structure backed by `SessionController` and the validated case payload.
 * This file exists so the screens can be exercised end-to-end *now* against
 * placeholder content while the real wiring is built.
 */
@Composable
fun WhodunitSetupDemo(
    onSetupComplete: (SetupResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf<SetupStep>(SetupStep.SelectMode) }
    var mode by remember { mutableStateOf<ModeId?>(null) }
    var playerCount by remember { mutableStateOf<Int?>(null) }
    var playerNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var briefingIndex by remember { mutableStateOf(0) }

    // Placeholder content — Phase 3's repository will replace these constants
    // with the validated case payload from `The Last Dinner`.
    val caseTitle = "The Last Dinner"
    val caseIntro = "It was meant to be a celebration. Seventy candles on the cake, " +
        "six guests at the table, and the rain coming down outside in long gray sheets…"
    val bedrockClues = listOf(
        "Maxwell Hargrove, 70, was found dead in his study at 10:45 p.m.",
        "Cause of death: poison in his evening brandy.",
        "The poison was added between 8:30 p.m. and 9:30 p.m.",
    )

    when (val s = step) {
        SetupStep.SelectMode -> ModeSelectionScreen(
            onModeSelected = {
                mode = it
                step = SetupStep.SelectPlayerCount
            },
            modifier = modifier,
        )
        SetupStep.SelectPlayerCount -> PlayerCountScreen(
            moduleRange = 4..8,
            caseSupportedRange = 4..6,
            displayStrategy = PlayerCountDisplayStrategy.HideUnsupported,
            onCountSelected = {
                playerCount = it
                step = SetupStep.EnterNames
            },
            modifier = modifier,
        )
        SetupStep.EnterNames -> PlayerEntryScreen(
            playerCount = playerCount!!,
            onConfirm = {
                playerNames = it
                step = SetupStep.PublicIntro
            },
            modifier = modifier,
        )
        SetupStep.PublicIntro -> PublicIntroScreen(
            title = caseTitle,
            intro = caseIntro,
            bedrockClues = bedrockClues,
            onContinue = { step = SetupStep.RulesBriefing },
            modifier = modifier,
        )
        SetupStep.RulesBriefing -> RulesBriefingScreen(
            cardIndex = briefingIndex,
            onAdvance = { next ->
                if (next >= 4) {
                    onSetupComplete(
                        SetupResult(
                            modeId = mode!!,
                            playerNames = playerNames,
                        ),
                    )
                    step = SetupStep.Done
                } else {
                    briefingIndex = next
                }
            },
            modifier = modifier,
        )
        SetupStep.Done -> Unit  // Reveal flow takes over.
    }
}

data class SetupResult(
    val modeId: ModeId,
    val playerNames: List<String>,
)

private sealed interface SetupStep {
    data object SelectMode : SetupStep
    data object SelectPlayerCount : SetupStep
    data object EnterNames : SetupStep
    data object PublicIntro : SetupStep
    data object RulesBriefing : SetupStep
    data object Done : SetupStep
}
