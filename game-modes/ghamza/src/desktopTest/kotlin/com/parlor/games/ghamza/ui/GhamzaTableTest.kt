package com.parlor.games.ghamza.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.components.InPlaceBackOwner
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaProjection
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GhamzaTableTest {
    @Test
    fun private_reveal_and_readiness_work_in_english_and_arabic_at_large_text() {
        verifyReveal(AppLanguage.English)
        verifyReveal(AppLanguage.Arabic)
    }

    private fun verifyReveal(language: AppLanguage) = runComposeUiTest {
        val state = GhamzaReducer().initial(uiPlayers(12), GhamzaSettings(), 42)
        val self = checkNotNull(state.hostOnly.winker)
        var epoch by mutableStateOf(0L)
        var sent: GhamzaAction? = null
        val back = InPlaceBackOwner()
        setContent {
            GameUiFrame(language, 320, 740, 2f, backOwner = back) {
                GhamzaTable(GhamzaProjection.toPlayer(state, self).state, self, false, true, epoch, { sent = it }, {})
            }
        }
        val arabic = language == AppLanguage.Arabic
        val role = if (arabic) "إنت الغمّاز" else "You are the Winker"
        val reveal = if (arabic) "اكشف دوري" else "Reveal my role"
        val ready = if (arabic) "عرفت دوري · غطّيه" else "I know my role · cover it"
        onNodeWithText(role).assertDoesNotExist()
        onNodeWithText(reveal).reachable().performClick()
        onNodeWithText(role).assertExists()
        runOnIdle { assertTrue(back.handleBack()) }
        onNodeWithText(role).assertDoesNotExist()
        onNodeWithText(reveal).reachable().performClick()
        onNodeWithText(ready).reachable().assertIsEnabled()
        onNodeWithText(ready, useUnmergedTree = true).assertFullText()
        runOnIdle { epoch++ }
        onNodeWithText(role).assertDoesNotExist()
        onNodeWithText(reveal).reachable().performClick()
        onNodeWithText(ready).reachable().performClick()
        assertEquals(GhamzaAction.Ready(self, state.public.token), sent)
        onNodeWithText(role).assertDoesNotExist()
    }

    @Test
    fun physical_winks_require_confirmation_and_elimination_leads_to_guess_and_result() = runComposeUiTest {
        val reducer = GhamzaReducer()
        val initial = reducer.initial(uiPlayers(4), GhamzaSettings(2, 1), 2)
        val self = initial.players.first { it.id != initial.hostOnly.winker }.id
        var state by mutableStateOf(initial.players.fold(initial) { current, player ->
            reducer.apply(current, GhamzaAction.Ready(player.id, current.public.token))
        })
        val back = InPlaceBackOwner()
        setContent {
            GameUiFrame(backOwner = back) {
                GhamzaTable(GhamzaProjection.toPlayer(state, self).state, self, true, true, 0,
                    { state = reducer.apply(state, it) }, {})
            }
        }
        onNodeWithText("I was winked at").reachable().performClick()
        runOnIdle { assertTrue(back.handleBack()) }
        onNodeWithText("Yes · record the wink").assertDoesNotExist()
        assertEquals(0, state.public.reports[self])
        onNodeWithText("I was winked at").reachable().performClick()
        assertEquals(0, state.public.reports[self])
        onNodeWithText("Not yet").reachable().performClick()
        assertEquals(0, state.public.reports[self])
        onNodeWithText("I was winked at").reachable().performClick()
        onNodeWithText("Yes · record the wink").reachable().performClick()
        assertEquals(1, state.public.reports[self])
        runOnIdle {
            state.players.filter { it.id != self && it.id != state.hostOnly.winker }.forEach { player ->
                for (attempt in 1..2) state = reducer.apply(state, GhamzaAction.Winked(player.id, state.public.token, attempt))
            }
        }
        assertEquals(GhamzaPhase.FinalGuess, state.phase)
        val lastReport = state.public.recentReports.last()
        val reportedName = state.players.first { it.id == lastReport.player }.displayName.asBidiArgument()
        onNodeWithText("$reportedName was winked at · attempt ${lastReport.attempt}").assertExists()
        val winker = state.players.first { it.id == state.hostOnly.winker }
        onNodeWithText(winker.displayName).reachable().performClick()
        onNodeWithText("Lock my guess").reachable().performClick()
        assertEquals(GhamzaPhase.MatchResult, state.phase)
        assertEquals(self, state.public.result?.winner)
        onNodeWithText("All rounds complete").assertExists()
        onNodeWithText("SCORES").assertDoesNotExist()
        onNodeWithText("${winker.displayName.asBidiArgument()} is eliminated this round").assertExists()
        onNodeWithText("Play again · new roles").reachable().performClick()
        assertEquals(GhamzaPhase.Reveal, state.phase)
        assertTrue(state.public.reports.values.all { it == 0 })
        onNodeWithText("Reveal my role").reachable().assertIsEnabled()
    }
}
