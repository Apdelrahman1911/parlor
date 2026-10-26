package com.parlor.games.dominoes.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_seat
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DominoRosterAccessibilityTest {
    @Test
    fun english_light_opponent_names_have_room_for_whole_words() = verifyNames(AppLanguage.English, ThemeMode.Light)

    @Test
    fun english_dark_opponent_names_have_room_for_whole_words() = verifyNames(AppLanguage.English, ThemeMode.Dark)

    @Test
    fun arabic_light_opponent_names_have_room_for_whole_words() = verifyNames(AppLanguage.Arabic, ThemeMode.Light)

    @Test
    fun arabic_dark_opponent_names_have_room_for_whole_words() = verifyNames(AppLanguage.Arabic, ThemeMode.Dark)

    private fun verifyNames(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val players = uiPlayers(4).map { player ->
            if (language == AppLanguage.Arabic) player.copy(displayName = "عبدالرحمن ${player.seat}") else player
        }
        val initial = DominoReducer().initial(players, DominoSettings(), 18)
        val self = players.first().id
        val labelsAndWidths = mutableListOf<Pair<String, Int>>()
        setContent {
            GameUiFrame(language, 320, 740, 2f, themeMode = themeMode) {
                val measurer = rememberTextMeasurer()
                labelsAndWidths.clear()
                players.filter { it.id != self }.forEach { player ->
                    val label = stringResource(Res.string.dom_seat, player.displayName.asBidiArgument(),
                        initial.public.handCounts.getValue(player.id), 0)
                    val wordWidth = player.displayName.split(' ').maxOf {
                        measurer.measure(it, style = ParlorTheme.typography.labelLarge).size.width
                    }
                    labelsAndWidths += label to wordWidth
                }
                DominoTable(DominoProjection.toPlayer(initial, self).state, self, false, true, 0, {}, {})
            }
        }
        waitForIdle()
        // Visual children intentionally clear duplicate semantics; measure the real public seat card
        // against the same font's longest word, rather than adding hidden test-only accessibility text.
        labelsAndWidths.forEach { (label, wordWidth) ->
            val bounds = onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            val cardWidth = (bounds.right - bounds.left).value
            assertTrue(cardWidth >= wordWidth,
                "Opponent card ${cardWidth}px is narrower than its name word ${wordWidth}px: $label")
        }
        val firstCard = onNodeWithContentDescription(labelsAndWidths.first().first)
        val reveal = if (language == AppLanguage.Arabic) "ورّيني قطعي" else "Show my tiles"
        val actionBounds = onNodeWithText(reveal).getUnclippedBoundsInRoot()
        assertTrue(actionBounds.bottom <= firstCard.getUnclippedBoundsInRoot().top,
            "The current action must precede an expanded opponent roster")
        firstCard.performScrollTo()
        capturePublicGameFrame("roster-${language.tag}-${themeMode.tag}-2.0")
    }
}
