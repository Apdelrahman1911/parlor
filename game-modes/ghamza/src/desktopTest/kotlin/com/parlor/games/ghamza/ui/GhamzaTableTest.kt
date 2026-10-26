package com.parlor.games.ghamza.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.theme.ThemeMode
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
    fun english_light_compact_large_text_journey() =
        verifyThemedJourney(AppLanguage.English, ThemeMode.Light)

    @Test
    fun english_dark_compact_large_text_journey() =
        verifyThemedJourney(AppLanguage.English, ThemeMode.Dark)

    @Test
    fun arabic_light_compact_large_text_journey() =
        verifyThemedJourney(AppLanguage.Arabic, ThemeMode.Light)

    @Test
    fun arabic_dark_compact_large_text_journey() =
        verifyThemedJourney(AppLanguage.Arabic, ThemeMode.Dark)

    private fun verifyThemedJourney(language: AppLanguage, themeMode: ThemeMode) {
        verifyReveal(language, themeMode)
        verifySocialJourney(language, themeMode)
    }

    @Test
    fun light_theme_rules_start_with_a_compact_guide_and_expand_details_on_request() = runComposeUiTest {
        val state = GhamzaReducer().initial(uiPlayers(3), GhamzaSettings(), 42)
        val self = state.players.first().id
        setContent {
            GameUiFrame(width = 320, fontScale = 2f, themeMode = ThemeMode.Light) {
                GhamzaTable(GhamzaProjection.toPlayer(state, self).state, self, false, true, 0, {}, {})
            }
        }
        onNodeWithText("How to play").reachable().performClick()
        onNodeWithText("Start in three steps").assertExists()
        onNodeWithText("Start in three steps", useUnmergedTree = true).assertFullText()
        onNodeWithText("Each round, one random player", substring = true).assertDoesNotExist()
        onNodeWithText("Full rules").reachable().performClick()
        onNodeWithText("Each round, one random player", substring = true).assertExists()
        onNodeWithText("Hide details").reachable().performClick()
        onNodeWithText("Each round, one random player", substring = true).assertDoesNotExist()
    }

    @Test
    fun covering_a_revealed_role_never_requires_a_network_action() = runComposeUiTest {
        val state = GhamzaReducer().initial(uiPlayers(3), GhamzaSettings(), 42)
        val self = checkNotNull(state.hostOnly.winker)
        var enabled by mutableStateOf(true)
        var sent: GhamzaAction? = null
        setContent {
            GameUiFrame {
                GhamzaTable(GhamzaProjection.toPlayer(state, self).state, self, false, enabled, 0, { sent = it }, {})
            }
        }
        onNodeWithText("Reveal my role").reachable().performClick()
        onNodeWithText("You are the Winker").assertExists()
        runOnIdle { enabled = false }
        onNodeWithText("Cover my role").reachable().assertIsEnabled().performClick()
        onNodeWithText("You are the Winker").assertDoesNotExist()
        assertEquals(null, sent)
    }

    @Test
    fun private_reveal_and_readiness_work_in_english_and_arabic_at_large_text() {
        verifyReveal(AppLanguage.English)
        verifyReveal(AppLanguage.Arabic)
    }

    private fun verifyReveal(language: AppLanguage, themeMode: ThemeMode = ThemeMode.Default) = runComposeUiTest {
        val state = GhamzaReducer().initial(uiPlayers(12), GhamzaSettings(), 42)
        val self = checkNotNull(state.hostOnly.winker)
        var epoch by mutableStateOf(0L)
        var sent: GhamzaAction? = null
        val back = InPlaceBackOwner()
        setContent {
            GameUiFrame(language, 320, 740, 2f, themeMode = themeMode, backOwner = back) {
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
    fun physical_winks_require_confirmation_and_elimination_leads_to_guess_and_result() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) verifySocialJourney(language)
    }

    private fun verifySocialJourney(language: AppLanguage, themeMode: ThemeMode = ThemeMode.Default) = runComposeUiTest {
        fun localized(en: String, ar: String) = if (language == AppLanguage.Arabic) ar else en
        val reducer = GhamzaReducer()
        val initial = reducer.initial(uiPlayers(4), GhamzaSettings(2, 1), 2)
        val self = initial.players.first { it.id != initial.hostOnly.winker }.id
        var state by mutableStateOf(initial.players.fold(initial) { current, player ->
            reducer.apply(current, GhamzaAction.Ready(player.id, current.public.token))
        })
        val back = InPlaceBackOwner()
        setContent {
            GameUiFrame(language, 320, 740, 2f, themeMode = themeMode, backOwner = back) {
                GhamzaTable(GhamzaProjection.toPlayer(state, self).state, self, true, true, 0,
                    { state = reducer.apply(state, it) }, {})
            }
        }
        capturePublicGameFrame("social-${language.tag}-${themeMode.name.lowercase()}-2.0")
        onNodeWithText(localized("I was winked at", "اتغمزلي")).reachable().performClick()
        runOnIdle { assertTrue(back.handleBack()) }
        onNodeWithText(localized("Yes · record the wink", "أيوه · سجّل الغمزة")).assertDoesNotExist()
        assertEquals(0, state.public.reports[self])
        onNodeWithText(localized("I was winked at", "اتغمزلي")).reachable().performClick()
        assertEquals(0, state.public.reports[self])
        onNodeWithText(localized("Not yet", "لسه")).reachable().performClick()
        assertEquals(0, state.public.reports[self])
        onNodeWithText(localized("I was winked at", "اتغمزلي")).reachable().performClick()
        onNodeWithText(localized("Yes · record the wink", "أيوه · سجّل الغمزة")).reachable().performClick()
        assertEquals(1, state.public.reports[self])
        runOnIdle {
            state.players.filter { it.id != self && it.id != state.hostOnly.winker }.forEach { player ->
                for (attempt in 1..2) state = reducer.apply(state, GhamzaAction.Winked(player.id, state.public.token, attempt))
            }
        }
        assertEquals(GhamzaPhase.FinalGuess, state.phase)
        val lastReport = state.public.recentReports.last()
        val reportedName = state.players.first { it.id == lastReport.player }.displayName.asBidiArgument()
        onNodeWithText(localized("$reportedName was winked at · attempt ${lastReport.attempt}", "$reportedName اتغمزله · محاولة ${lastReport.attempt}")).assertExists()
        val winker = state.players.first { it.id == state.hostOnly.winker }
        onNodeWithText(winker.displayName).reachable().performClick()
        onNodeWithText(localized("Lock my guess", "ثبّت تخميني")).reachable().performClick()
        assertEquals(GhamzaPhase.MatchResult, state.phase)
        onNodeWithText(localized("The secret is out", "السر اتكشف")).assertIsDisplayed()
        capturePublicGameFrame("results-${language.tag}-${themeMode.name.lowercase()}-2.0")
        assertEquals(self, state.public.result?.winner)
        onNodeWithText(localized("All rounds complete", "كل الجولات خلصت")).assertExists()
        onNodeWithText("SCORES").assertDoesNotExist()
        onNodeWithText(localized("${winker.displayName.asBidiArgument()} is eliminated this round", "${winker.displayName.asBidiArgument()} خرج من الجولة دي")).assertExists()
        onNodeWithText(localized("Play again · new roles", "العبوا تاني · أدوار جديدة")).reachable().performClick()
        assertEquals(GhamzaPhase.Reveal, state.phase)
        assertTrue(state.public.reports.values.all { it == 0 })
        capturePublicGameFrame("rematch-covered-${language.tag}-${themeMode.name.lowercase()}-2.0")
        onNodeWithText(localized("Reveal my role", "اكشف دوري")).reachable().assertIsEnabled()
    }
}
