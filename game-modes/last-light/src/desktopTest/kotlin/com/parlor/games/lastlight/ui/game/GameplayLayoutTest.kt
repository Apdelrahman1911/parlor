package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.CardId
import com.parlor.games.lastlight.domain.model.PublicClaim
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GameplayLayoutTest {
    @Test
    fun narrow_english_phone_reaches_the_fifth_card_and_host_results() =
        verifyLayout(AppLanguage.English, 360.dp, 640.dp)

    @Test
    fun narrow_arabic_phone_reaches_the_fifth_card_and_host_results() =
        verifyLayout(AppLanguage.Arabic, 360.dp, 640.dp)

    @Test
    fun large_english_text_keeps_cards_and_primary_controls_reachable() =
        verifyLayout(AppLanguage.English, 320.dp, 740.dp, 2f)

    @Test
    fun large_arabic_text_keeps_cards_and_primary_controls_reachable() =
        verifyLayout(AppLanguage.Arabic, 320.dp, 740.dp, 2f)

    @Test
    fun english_landscape_keeps_cards_and_result_actions_reachable() =
        verifyLayout(AppLanguage.English, 844.dp, 390.dp)

    @Test
    fun arabic_landscape_keeps_cards_and_result_actions_reachable() =
        verifyLayout(AppLanguage.Arabic, 844.dp, 390.dp)

    @Test
    fun tablet_uses_the_full_table_without_losing_controls() =
        verifyLayout(AppLanguage.English, 1024.dp, 768.dp)

    @Test
    fun large_text_reveal_control_is_fully_visible_before_scrolling() = runComposeUiTest {
        val names = listOf("Ari", "Moxie", "Pip", "Orbit", "Sol", "Noor")
        val view = playingView().let { initial ->
            initial.copy(
                roundNumber = 2,
                players = initial.players.mapIndexed { index, player -> player.copy(displayName = names[index]) },
                latestClaim = PublicClaim("b", 1),
                availableActions = AvailableActions(canPlay = true, canChallenge = true, maxPlayableCards = 3),
                roundOutcome = roundResultView().roundOutcome,
            )
        }
        setContent {
            LastLightTestFrame(width = 411.dp, height = 662.dp, fontScale = 2f) {
                // Match the reference's game-content viewport; other cases exercise the complete wrapper.
                TestTable(view)
            }
        }
        val control = onNodeWithTag("game-reveal-hand").assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = onNodeWithTag(LAST_LIGHT_VIEWPORT).getUnclippedBoundsInRoot()
        assertTrue(control.top >= viewport.top && control.bottom <= viewport.bottom, "Reveal must be fully visible: $control")
        assertTrue(control.bottom - control.top >= 56.dp)
    }

    private fun verifyLayout(
        language: AppLanguage,
        width: Dp,
        height: Dp,
        scale: Float = 1f,
    ) = runComposeUiTest {
        var view by mutableStateOf(playingView())
        var played = emptyList<CardId>()
        var nextRound = false
        var returned = false
        setContent {
            LastLightTestFrame(width, height, scale, language) {
                TestSessionTable(
                    view,
                    onPlay = { played = it },
                    onNextRound = { nextRound = true },
                    onReturnToLobby = { returned = true },
                )
            }
        }
        fun assertPrimaryControl(tag: String) {
            val bounds = onNodeWithTag(tag).bringIntoView().getUnclippedBoundsInRoot()
            val viewport = onNodeWithTag(LAST_LIGHT_VIEWPORT).getUnclippedBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top >= 56.dp, "$tag has a small touch target: $bounds")
            assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right, "$tag crosses the viewport: $bounds")
            assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom, "$tag is vertically clipped: $bounds")
        }
        fun capture(phase: String) {
            val filename = "last-light-${language.tag}-${width.value.toInt()}x${height.value.toInt()}-$scale-$phase.png"
            val file = File("build/ui-snapshots/$filename").apply { parentFile.mkdirs() }
            ImageIO.write(onNodeWithTag(LAST_LIGHT_VIEWPORT).captureToImage().toAwtImage(), "png", file)
        }

        capture("concealed")
        assertPrimaryControl("game-reveal-hand")
        onNodeWithTag("game-reveal-hand").performClick()
        for (index in 0..4) {
            onNodeWithTag("game-card-$index").performScrollTo().assertIsDisplayed()
        }
        onNodeWithTag("game-card-4").performClick().assertIsOn()
        assertPrimaryControl("game-play")
        onNodeWithTag("game-play").assertIsEnabled().performClick()
        assertEquals(listOf(view.yourHand[4].id), played)
        capture("playing")
        val playLabel = if (language == AppLanguage.Arabic) "العب ورقة واحدة (1)" else "Play 1 card"
        onNodeWithText(playLabel, useUnmergedTree = true).assertTextDoesNotOverflow()

        runOnIdle { view = roundResultView() }
        capture("round-result")
        val verdictLabel = if (language == AppLanguage.Arabic) "انكشفت الخدعة." else "Bluff caught."
        onNodeWithText(verdictLabel, useUnmergedTree = true).assertTextDoesNotOverflow(requireWholeWords = true)
        assertPrimaryControl("game-next-round")
        onNodeWithTag("game-next-round").assertIsEnabled().performClick()
        assertTrue(nextRound)

        runOnIdle { view = finishedView() }
        capture("winner")
        assertPrimaryControl("game-rematch")
        onNodeWithTag("game-rematch").assertIsEnabled().performClick()
        assertTrue(returned)
    }

    private fun SemanticsNodeInteraction.assertTextDoesNotOverflow(requireWholeWords: Boolean = false) {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val measured = fetchSemanticsNode().size
        // Compose 1.10.3 reconstructs plain-String Text semantics at incoming maxWidth,
        // while its painted Paragraph uses the measured intrinsic width. Compare actual
        // line extents and height, as Parlor's existing Mafia count assertions do.
        val widestLine = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
        assertTrue(measured.width >= ceil(widestLine), "Text glyphs exceed their frame: $measured / $widestLine")
        assertTrue(
            measured.height >= ceil(layout.multiParagraph.height),
            "Text lines exceed their frame: $measured / ${layout.multiParagraph.height}",
        )
        assertFalse((0 until layout.lineCount).any(layout::isLineEllipsized), "Text was ellipsized")
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1), "Text was truncated")
        if (requireWholeWords) {
            val text = layout.layoutInput.text
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line, visibleEnd = true)
                assertTrue(end == text.length || text[end].isWhitespace(), "Line $line splits a word in '$text'")
            }
        }
    }
}
