package com.parlor.games.wordimpostor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.components.InPlaceBackOwner
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorProjection
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordRole
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WordImpostorTableTest {
    @Test
    fun ordinary_and_impostor_secrets_are_covered_on_entry_and_interruption_in_both_languages() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) for (role in WordRole.entries) {
            verifyReveal(language, role)
        }
    }

    private fun verifyReveal(language: AppLanguage, role: WordRole) = runComposeUiTest {
        val canonical = WordImpostorReducer().initial(uiPlayers(7), WordImpostorSettings(impostors = 2), 42)
        val self = canonical.privatePerPlayer.entries.first { it.value.role == role }.key
        var epoch by mutableStateOf(0L)
        val back = InPlaceBackOwner()
        var word = ""
        var sent: WordImpostorAction? = null
        setContent {
            GameUiFrame(language, 320, 740, 2f, backOwner = back) {
                word = stringResource(WordContentResources.word(checkNotNull(canonical.hostOnly.wordId)))
                WordImpostorTable(WordImpostorProjection.toPlayer(canonical, self).state, self, false, true, epoch, { sent = it }, {})
            }
        }
        waitForIdle()
        val arabic = language == AppLanguage.Arabic
        val reveal = if (arabic) "اكشف سري" else "Reveal my secret"
        val ready = if (arabic) "افتكرت · اخفيها" else "I remember · hide it"
        val roleText = if (arabic) "إنت الإمبوستر" else "You are the Impostor"
        onNodeWithText(word).assertDoesNotExist()
        onNodeWithText(roleText).assertDoesNotExist()
        onNodeWithText(reveal).reachable().performClick()
        if (role == WordRole.Ordinary) onNodeWithText(word).assertExists() else {
            onNodeWithText(word).assertDoesNotExist()
            onNodeWithText(roleText).assertExists()
        }
        onNodeWithText(ready).reachable().assertIsEnabled()
        onNodeWithText(ready, useUnmergedTree = true).assertFullText()
        runOnIdle { assertTrue(back.handleBack()) }
        onNodeWithText(word).assertDoesNotExist()
        onNodeWithText(roleText).assertDoesNotExist()
        onNodeWithText(reveal).reachable().performClick()
        runOnIdle { epoch++ }
        onNodeWithText(word).assertDoesNotExist()
        onNodeWithText(roleText).assertDoesNotExist()
        onNodeWithText(reveal).reachable().performClick()
        onNodeWithText(ready).reachable().performClick()
        assertEquals(WordImpostorAction.Ready(self, canonical.public.token), sent)
        onNodeWithText(word).assertDoesNotExist()
    }

    @Test
    fun guided_question_is_private_to_asker_and_advances_with_its_exact_index() = runComposeUiTest {
        val reducer = WordImpostorReducer()
        val initial = reducer.initial(uiPlayers(5), WordImpostorSettings(), 8)
        var state by mutableStateOf(initial.players.fold(initial) { current, player ->
            reducer.apply(current, WordImpostorAction.Ready(player.id, current.public.token))
        })
        val self = state.public.interactions.first().asker
        var prompt = ""
        setContent {
            GameUiFrame {
                prompt = stringResource(WordContentResources.question(state.hostOnly.questions.first()))
                WordImpostorTable(WordImpostorProjection.toPlayer(state, self).state, self, false, true, 0,
                    { state = reducer.apply(state, it) }, {})
            }
        }
        waitForIdle()
        onNodeWithText(prompt).assertExists()
        onNodeWithText("Question answered · next").reachable().performClick()
        assertEquals(1, state.public.questionIndex)
        onNodeWithText(prompt).assertDoesNotExist()
        onNodeWithText("Question answered · next").assertDoesNotExist()
    }

    @Test
    fun private_vote_confirmation_then_five_choice_guess_and_public_result_work_end_to_end() = runComposeUiTest {
        val reducer = WordImpostorReducer()
        val initial = reducer.initial(uiPlayers(7), WordImpostorSettings(impostors = 2, rounds = 1), 12)
        val voting = toVoting(reducer, initial)
        var state by mutableStateOf(voting)
        val self = state.hostOnly.impostors.first()
        val target = state.players.first { it.id != self }
        val wordId = checkNotNull(state.hostOnly.wordId)
        val choiceIds = state.hostOnly.choices.getValue(self)
        var choices = emptyList<String>()
        var word = ""
        var epoch by mutableStateOf(0L)
        val back = InPlaceBackOwner()
        setContent {
            GameUiFrame(width = 360, height = 740, backOwner = back) {
                choices = choiceIds.map { stringResource(WordContentResources.word(it)) }
                word = stringResource(WordContentResources.word(wordId))
                WordImpostorTable(WordImpostorProjection.toPlayer(state, self).state, self, false, true, epoch,
                    { state = reducer.apply(state, it) }, {})
            }
        }
        onNodeWithText(target.displayName).reachable().performClick()
        assertTrue(state.public.voted.isEmpty())
        runOnIdle { assertTrue(back.handleBack()) }
        onNodeWithText("Lock my vote").assertDoesNotExist()
        onNodeWithText(target.displayName).reachable().performClick()
        runOnIdle { epoch++ }
        onNodeWithText("Lock my vote").assertDoesNotExist()
        onNodeWithText(target.displayName).reachable().performClick()
        onNodeWithText("Lock my vote").reachable().performClick()
        assertEquals(setOf(self), state.public.voted)
        assertNull(state.public.voteCounts)
        onNodeWithText("Vote locked. Waiting for the circle.").assertExists()
        runOnIdle {
            state.players.filter { it.id != self }.forEach { player ->
                val vote = state.players.first { it.id != player.id }.id
                state = reducer.apply(state, WordImpostorAction.Vote(player.id, state.public.token, vote))
            }
        }
        assertEquals(WordImpostorPhase.Guessing, state.phase)
        choices.forEach { onNodeWithText(it).assertDoesNotExist() }
        onNodeWithText("Reveal my secret").reachable().performClick()
        choices.forEach { onNodeWithText(it).reachable().assertIsEnabled() }
        onNodeWithText(word).reachable().performClick()
        assertNull(state.privatePerPlayer.getValue(self).guess)
        onNodeWithText("Lock my guess").reachable().performClick()
        assertEquals(wordId, state.privatePerPlayer.getValue(self).guess)
        assertNull(state.public.result)
        choices.forEach { onNodeWithText(it).assertDoesNotExist() }
        runOnIdle {
            state.hostOnly.impostors.filter { it != self }.forEach {
                state = reducer.apply(state, WordImpostorAction.Guess(it, state.public.token, wordId))
            }
        }
        assertEquals(WordImpostorPhase.MatchResult, state.phase)
        onNodeWithText(word).assertExists()
        onNodeWithText("Play again · reset scores").assertDoesNotExist() // Only the real host owns rematches.
    }

    private fun toVoting(reducer: WordImpostorReducer, initial: WordImpostorState): WordImpostorState {
        var state = initial
        state.players.forEach { state = reducer.apply(state, WordImpostorAction.Ready(it.id, state.public.token)) }
        state.public.interactions.forEachIndexed { index, pair ->
            state = reducer.apply(state, WordImpostorAction.Answered(pair.asker, state.public.token, index))
        }
        return reducer.apply(state, WordImpostorAction.OpenVoting(state.public.token))
    }
}
