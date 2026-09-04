package com.parlor.games.whodunit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.ui.components.TimerRibbon
import com.parlor.games.whodunit.ui.screens.reveal.RevealStageScreen
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WhodunitAccessibilitySemanticsTest {
    @Test
    fun timer_exposes_one_coherent_normal_and_urgent_value() = runComposeUiTest {
        val remainingSeconds = mutableStateOf(42)
        setContent {
            EnglishParlorTheme {
                TimerRibbon(
                    remainingSeconds = remainingSeconds.value,
                    totalSeconds = 60,
                    paused = false,
                )
            }
        }

        onNode(hasContentDescription("DISCUSSION. 0:42 remaining of 1:00.")).assertExists()
        onNode(hasText("DISCUSSION")).assertDoesNotExist()
        onNode(hasText("0:42")).assertDoesNotExist()
        onNode(hasText("/ 1:00")).assertDoesNotExist()

        runOnIdle { remainingSeconds.value = 9 }

        onNode(hasContentDescription("HURRY. 0:09 remaining of 1:00.")).assertExists()
        onNode(hasContentDescription("DISCUSSION. 0:42 remaining of 1:00.")).assertDoesNotExist()
        onAllNodes(hasLiveRegion(LiveRegionMode.Assertive)).assertCountEquals(0)
    }

    @Test
    fun timer_announces_only_the_exact_ten_second_threshold() = runComposeUiTest {
        val remainingSeconds = mutableStateOf(11)
        setContent {
            EnglishParlorTheme {
                TimerRibbon(
                    remainingSeconds = remainingSeconds.value,
                    totalSeconds = 60,
                    paused = false,
                )
            }
        }

        onAllNodes(hasLiveRegion(LiveRegionMode.Assertive)).assertCountEquals(0)

        runOnIdle { remainingSeconds.value = 10 }

        onNode(
            hasContentDescription("HURRY. 0:10 remaining of 1:00.") and
                hasLiveRegion(LiveRegionMode.Assertive),
        ).assertExists()
        onAllNodes(hasContentDescription("HURRY. 0:10 remaining of 1:00."))
            .assertCountEquals(1)

        runOnIdle { remainingSeconds.value = 9 }

        onNode(hasContentDescription("HURRY. 0:09 remaining of 1:00.")).assertExists()
        onAllNodes(hasLiveRegion(LiveRegionMode.Assertive)).assertCountEquals(0)
    }

    @Test
    fun reveal_hides_each_stage_until_its_fade_completes() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            EnglishParlorTheme {
                RevealStageScreen(
                    verdict = Verdict.PlayersWin("killer"),
                    killerDisplayName = KILLER_NAME,
                    revealNarrative = NARRATIVE,
                    onAcknowledge = null,
                )
            }
        }

        onNode(hasText(KILLER_NAME)).assertDoesNotExist()
        onNode(hasText(NARRATIVE)).assertDoesNotExist()

        mainClock.advanceTimeBy(FAST_MILLIS + SLOW_MILLIS)

        onNode(hasText(KILLER_NAME) and hasLiveRegion(LiveRegionMode.Assertive)).assertExists()
        onNode(hasText(NARRATIVE)).assertDoesNotExist()

        mainClock.advanceTimeBy(SLOW_MILLIS)

        onNode(hasText(NARRATIVE) and hasLiveRegion(LiveRegionMode.Polite)).assertExists()
    }

    @Test
    fun reduced_motion_exposes_complete_reveal_semantics_immediately() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            EnglishParlorTheme(reducedMotion = true) {
                RevealStageScreen(
                    verdict = Verdict.PlayersWin("killer"),
                    killerDisplayName = KILLER_NAME,
                    revealNarrative = NARRATIVE,
                    onAcknowledge = null,
                )
            }
        }

        onNode(hasText(KILLER_NAME) and hasLiveRegion(LiveRegionMode.Assertive)).assertExists()
        onNode(hasText(NARRATIVE) and hasLiveRegion(LiveRegionMode.Polite)).assertExists()
    }

    @Test
    fun reveal_continue_is_enabled_only_after_the_final_fade_completes() = runComposeUiTest {
        mainClock.autoAdvance = false
        var acknowledgements = 0
        setContent {
            EnglishParlorTheme {
                RevealStageScreen(
                    verdict = Verdict.PlayersWin("killer"),
                    killerDisplayName = KILLER_NAME,
                    revealNarrative = NARRATIVE,
                    onAcknowledge = { acknowledgements++ },
                )
            }
        }

        val continueButton = onNodeWithContentDescription(CONTINUE_DESCRIPTION)
        continueButton.assertIsNotEnabled()

        mainClock.advanceTimeBy(FAST_MILLIS + SLOW_MILLIS)
        continueButton.assertIsNotEnabled()

        mainClock.advanceTimeBy(SLOW_MILLIS)
        continueButton.assertIsEnabled().performClick()
        runOnIdle { assertEquals(1, acknowledgements) }
    }

    private fun hasLiveRegion(mode: LiveRegionMode): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)

    @Composable
    private fun EnglishParlorTheme(
        reducedMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        ProvideAppLanguage(AppLanguage.English) {
            ParlorTheme(reducedMotion = reducedMotion, content = content)
        }
    }

    private companion object {
        const val KILLER_NAME = "Mara Vale"
        const val NARRATIVE = "The final clue reveals the hidden killer."
        const val CONTINUE_DESCRIPTION = "Acknowledge the reveal and continue to post-game."
        const val FAST_MILLIS = 180L
        const val SLOW_MILLIS = 480L
    }
}
