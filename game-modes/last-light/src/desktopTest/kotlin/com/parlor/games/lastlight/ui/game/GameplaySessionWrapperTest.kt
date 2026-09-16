package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GameplaySessionWrapperTest {
    @Test
    fun real_wrapper_conceals_on_process_epoch_session_change_and_options_dialog() = runComposeUiTest {
        var visibility by mutableStateOf(LastLightProcessVisibility(true, 0L))
        var session by mutableStateOf("one")
        setContent {
            LastLightTestFrame {
                TestSessionTable(playingView(), sessionId = session, visibility = visibility)
            }
        }
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").performClick().assertIsOn()
        runOnIdle { visibility = LastLightProcessVisibility(true, 1L) }
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsEnabled()
        onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("game-reveal-hand").performClick()
        onNodeWithTag("game-card-0").assertIsOff()

        onNodeWithText("Table options").performClick()
        onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithText("Done").performClick()
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        runOnIdle { session = "two" }
        onNodeWithTag("game-card-0").assertDoesNotExist()
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsEnabled()
    }

    @Test
    fun switching_to_arabic_preserves_selection_and_relabels_card_accessibility() = runComposeUiTest {
        var language by mutableStateOf(AppLanguage.English)
        setContent {
            LastLightTestFrame(language = language) { TestSessionTable(playingView()) }
        }
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").performClick().assertIsOn()
        runOnIdle { language = AppLanguage.Arabic }
        onNodeWithTag("game-card-0").assertIsOn()
        onAllNodes(hasContentDescription("قمر. الورقة", substring = true)).assertCountEquals(2)
        onNodeWithTag("game-hide-hand").bringIntoView().performClick()
        onNodeWithText("أظهر أوراقك").assertExists()
        onAllNodes(hasContentDescription("الورقة", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("game-play").assertIsNotEnabled()
    }
}
