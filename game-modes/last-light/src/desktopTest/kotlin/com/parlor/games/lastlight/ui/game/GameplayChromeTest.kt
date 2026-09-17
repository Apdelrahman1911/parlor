package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.flow.common.LastLightExitConfirmation
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GameplayChromeTest {
    @Test
    fun english_toolbar_options_and_results_share_one_unbroken_backdrop() = verifyChrome(AppLanguage.English)

    @Test
    fun arabic_toolbar_options_and_results_share_one_unbroken_backdrop() = verifyChrome(AppLanguage.Arabic)

    @Test
    fun english_options_and_exit_are_readable_at_double_text_size() = verifyChrome(AppLanguage.English, largeText = true)

    @Test
    fun arabic_options_and_exit_are_readable_at_double_text_size() = verifyChrome(AppLanguage.Arabic, largeText = true)

    @Test
    fun escape_dismisses_options_locally_without_leaving_or_revealing_the_hand() = runComposeUiTest {
        var leaves = 0
        setContent { LastLightTestFrame { TestSessionTable(playingView(), onRequestLeave = { leaves++ }) } }
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-options").performClick()
        onNodeWithTag("game-options-dialog").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("game-options-dialog").assertDoesNotExist()
        assertEquals(0, leaves)
        onNodeWithTag("game-card-0").assertDoesNotExist()
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsEnabled()
    }

    @Test
    fun backgrounded_toolbar_cannot_open_leave_or_options() = runComposeUiTest {
        setContent {
            LastLightTestFrame {
                TestSessionTable(playingView(), visibility = LastLightProcessVisibility(false, 1L))
            }
        }
        onNodeWithTag("game-leave").assertIsNotEnabled()
        onNodeWithTag("game-options").assertIsNotEnabled()
    }

    @Test
    fun host_and_peer_confirmations_keep_the_destructive_warning_and_disable_duplicate_actions() = runComposeUiTest {
        var kind by mutableStateOf(SessionExitKind.Host)
        var pending by mutableStateOf(false)
        var confirmations = 0
        setContent {
            LastLightTestFrame {
                LastLightExitConfirmation(
                    kind, onStay = {}, onExit = { confirmations++; pending = true }, exitInFlight = pending,
                )
            }
        }
        onNodeWithText("Leaving as host ends this room for everyone. This multiplayer session cannot be resumed.")
            .assertExists()
        onNodeWithTag("game-confirm-leave").bringIntoView().performClick().assertIsNotEnabled().performClick()
        onNodeWithTag("game-stay").assertIsNotEnabled()
        assertEquals(1, confirmations)
        runOnIdle { kind = SessionExitKind.Peer; pending = false }
        onNodeWithText("Your membership and rejoin credential will be permanently deleted. You cannot rejoin this session after leaving.")
            .assertExists()
        capture("peer-leave")
        onNodeWithTag("game-confirm-leave").bringIntoView().performClick().assertIsNotEnabled().performClick()
        assertEquals(2, confirmations)
    }

    private fun verifyChrome(language: AppLanguage, largeText: Boolean = false) = runComposeUiTest {
        var view by mutableStateOf(playingView())
        var leaveRequested by mutableStateOf(false)
        var exits = 0
        setContent {
            LastLightTestFrame(width = 320.dp, height = 740.dp, fontScale = if (largeText) 2f else 1f, language = language) {
                if (leaveRequested) {
                    LastLightExitConfirmation(
                        SessionExitKind.Local,
                        onStay = { leaveRequested = false },
                        onExit = { exits++ },
                        exitInFlight = false,
                    )
                } else {
                    TestSessionTable(view, onRequestLeave = { leaveRequested = true })
                }
            }
        }
        val name = "${language.tag}-${if (largeText) "large-text" else "compact"}"
        onAllNodes(hasTestTag("game-table-backdrop")).assertCountEquals(1)
        val viewport = onNodeWithTag(LAST_LIGHT_VIEWPORT).getUnclippedBoundsInRoot()
        assertEquals(viewport, onNodeWithTag("game-table-backdrop").getUnclippedBoundsInRoot())
        val leave = onNodeWithTag("game-leave").assertIsDisplayed().getUnclippedBoundsInRoot()
        val options = onNodeWithTag("game-options").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(leave.top, options.top, "Both controls belong to the same toolbar")
        for (tag in listOf("game-leave", "game-options")) {
            val control = onNodeWithTag(tag)
            val bounds = control.getUnclippedBoundsInRoot()
            assertEquals(bounds, control.getBoundsInRoot(), "$tag must not be clipped")
            assertTrue(bounds.bottom - bounds.top >= 48.dp)
            assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right)
        }
        assertTrue(if (language == AppLanguage.Arabic) leave.left > options.left else leave.left < options.left)
        val backdrop = onNodeWithTag(LAST_LIGHT_VIEWPORT).captureToImage().toAwtImage()
        val seam = onNodeWithTag("game-table-toolbar").getUnclippedBoundsInRoot().bottom.value.toInt()
        // The outer edge has no panel or text: crossing the toolbar cannot restart a flat fill/gradient.
        for (channel in listOf(0, 8, 16)) {
            val above = backdrop.getRGB(1, seam - 1).shr(channel) and 255
            val below = backdrop.getRGB(1, seam).shr(channel) and 255
            assertTrue(abs(above - below) <= 1, "The table backdrop has a header seam")
        }
        capture("$name-table")
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").bringIntoView().performClick().assertIsOn()
        onNodeWithTag("game-options").performClick()
        onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("game-card-0").assertDoesNotExist()
        val sound = if (language == AppLanguage.Arabic) "الصوت" else "Sound"
        onNodeWithText(sound).bringIntoView().assertIsOn().performClick().assertIsOff()
        capture("$name-options", "game-options-dialog")
        onNodeWithTag("game-options-close").bringIntoView().performClick()
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").bringIntoView().assertIsOff()
        onNodeWithTag("game-leave").performClick()
        onNodeWithTag("game-table").assertDoesNotExist()
        onNodeWithTag("game-card-0").assertDoesNotExist()
        assertEquals(0, exits, "Leave opens the guard, never exits directly")
        capture("$name-leave")
        onNodeWithTag("game-stay").bringIntoView().performClick()
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsEnabled()
        onNodeWithTag("game-card-0").assertDoesNotExist()
        for (result in listOf(roundResultView(), finishedView())) {
            runOnIdle { view = result }
            onAllNodes(hasTestTag("game-table-backdrop")).assertCountEquals(1)
            capture("$name-${result.phase}")
        }
    }

    private fun ComposeUiTest.capture(name: String, tag: String = LAST_LIGHT_VIEWPORT) {
        val file = File("build/ui-snapshots/table-chrome-$name.png").apply { parentFile.mkdirs() }
        ImageIO.write(onNodeWithTag(tag).captureToImage().toAwtImage(), "png", file)
    }
}
