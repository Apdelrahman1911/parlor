package com.parlor.games.dominoes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.dominoes.domain.DominoCompetition
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoVariant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DominoSettingsTest {
    @Test fun default_targets_and_team_selection_survive_a_shrinking_roster_without_trapping_the_host() = runComposeUiTest {
        var settings by mutableStateOf(DominoSettings())
        var count by mutableStateOf(4)
        setContent {
            GameUiFrame {
                DominoSettingsContent(settings.caseId, count, true) { settings = checkNotNull(DominoSettings.fromCaseId(it.raw)) }
            }
        }
        onNodeWithText("Default").assertIsSelected()
        onNodeWithText("101 points").assertIsSelected()
        onNodeWithText("151 points").performClick()
        onNodeWithText("Teams").performClick()
        assertEquals(DominoSettings(target = 151, competition = DominoCompetition.Teams), settings)
        runOnIdle { count = 3 }
        onNodeWithText("Teams").assertIsNotEnabled()
        onNodeWithText("Individual").assertIsEnabled().performClick()
        assertEquals(DominoCompetition.Individual, settings.competition)
        runOnIdle { count = 4 }
        onNodeWithText("Teams").performClick()
        onNodeWithText("Draw").performClick()
        assertEquals(DominoVariant.Draw, settings.variant)
        assertEquals(DominoCompetition.Individual, settings.competition)
    }

    @Test fun team_relationships_are_named_in_both_languages_and_three_player_tables_have_no_stock() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) runComposeUiTest {
            val canonical = DominoReducer().initial(uiPlayers(4), DominoSettings(competition = DominoCompetition.Teams), 5)
            val self = canonical.players.first().id
            setContent {
                GameUiFrame(language, 320, 740, 2f) {
                    DominoTable(DominoProjection.toPlayer(canonical, self).state, self, true, true, 0, {}, {})
                }
            }
            val label = if (language == AppLanguage.Arabic) "فريق 1 · النقط المشتركة: 0 · فريقك" else
                "Team 1 · Shared points: 0 · Your team"
            onNodeWithText(label).reachable().assertExists()
            onNodeWithText(label, useUnmergedTree = true).assertFullText()
        }
        runComposeUiTest {
            val canonical = DominoReducer().initial(uiPlayers(3), DominoSettings(), 4)
            val self = canonical.players.first().id
            setContent {
                GameUiFrame { DominoTable(DominoProjection.toPlayer(canonical, self).state, self, true, true, 0, {}, {}) }
            }
            onNodeWithText("Stock", substring = true).assertDoesNotExist()
            onNodeWithText("Draw one tile").assertDoesNotExist()
        }
    }
}
