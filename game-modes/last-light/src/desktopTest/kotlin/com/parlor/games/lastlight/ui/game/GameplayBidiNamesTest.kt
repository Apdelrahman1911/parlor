package com.parlor.games.lastlight.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.PublicClaim
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameplayBidiNamesTest {
    @Test
    fun englishTurnClaimAndSeatKeepArabicNameDirectionIsolated() = verifyPlaying(AppLanguage.English, "نور 12")

    @Test
    fun arabicTurnClaimAndSeatKeepLatinNameDirectionIsolated() = verifyPlaying(AppLanguage.Arabic, "Alex 12")

    @Test
    fun englishResultsKeepArabicWinnerIsolated() = verifyWinner(AppLanguage.English, "نور 12")

    @Test
    fun arabicResultsKeepLatinWinnerIsolated() = verifyWinner(AppLanguage.Arabic, "Alex 12")

    private fun verifyPlaying(language: AppLanguage, name: String) = runComposeUiTest {
        val view = playingView().let { original ->
            original.copy(
                players = original.players.map {
                    when (it.id) {
                        "b" -> it.copy(displayName = name)
                        "c" -> it.copy(displayName = "$name 3", handCount = 4)
                        else -> it
                    }
                },
                turnPlayerId = "b",
                latestClaim = PublicClaim("c", 1),
                availableActions = AvailableActions(),
            )
        }
        val isolated = name.asBidiArgument()
        setContent { LastLightTestFrame(language = language) { TestTable(view) } }
        onNodeWithText(if (language == AppLanguage.Arabic) "دور $isolated" else "$isolated’s turn").assertExists()
        val claimant = "$name 3".asBidiArgument()
        onNodeWithText(
            if (language == AppLanguage.Arabic) "$claimant يدّعي" else "$claimant claims",
            substring = true,
        ).assertExists()
        onNodeWithTag("game-player-b").assert(hasContentDescription("$isolated.", substring = true))
        onNodeWithTag("game-card-0").assertDoesNotExist()
        assertEquals(name, view.players.single { it.id == "b" }.displayName)
    }

    private fun verifyWinner(language: AppLanguage, name: String) = runComposeUiTest {
        val view = finishedView().let { original ->
            original.copy(players = original.players.map { if (it.id == "b") it.copy(displayName = name) else it })
        }
        val isolated = name.asBidiArgument()
        setContent { LastLightTestFrame(language = language) { TestTable(view) } }
        onNodeWithText(if (language == AppLanguage.Arabic) "فاز $isolated." else "$isolated wins.").assertExists()
        assertEquals(name, view.players.single { it.id == "b" }.displayName)
    }
}
