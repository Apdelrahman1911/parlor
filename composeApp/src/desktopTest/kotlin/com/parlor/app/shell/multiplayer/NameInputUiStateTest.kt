package com.parlor.app.shell.multiplayer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.designsystem.tokens.CozyNoirPalette
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** Real Desktop pointer and edit-state coverage, not native UIKit/XCTest delivery. */
@OptIn(ExperimentalTestApi::class)
class NameInputUiStateTest {
    @Test
    fun english_edits_reach_the_draft_and_done_does_not_submit_or_clear_it() {
        verifyEdits(AppLanguage.English, "Table Test", isHost = false)
    }

    @Test
    fun arabic_edits_reach_the_draft_and_done_does_not_submit_or_clear_it() {
        verifyEdits(AppLanguage.Arabic, "إيمان المصري", isHost = false)
    }

    @Test
    fun english_host_edits_require_explicit_continue() {
        verifyEdits(AppLanguage.English, "Table Test", isHost = true)
    }

    @Test
    fun arabic_host_edits_require_explicit_continue() {
        verifyEdits(AppLanguage.Arabic, "إيمان المصري", isHost = true)
    }

    @Test
    fun english_full_string_peer_edit_retains_the_original_native_test_payload() {
        verifyEdits(AppLanguage.English, "Table Test", isHost = false, fullString = true)
    }

    @Test
    fun arabic_full_string_peer_edit_retains_the_original_native_test_payload() {
        // The original Arabic XCTest types Latin text, not the Arabic text in
        // the incremental test above. Exercise that exact payload as one edit.
        verifyEdits(AppLanguage.Arabic, "Table Test", isHost = false, fullString = true)
    }

    @Test
    fun english_full_string_host_edit_preserves_mixed_direction_text() {
        verifyEdits(AppLanguage.English, "Table Test نور", isHost = true, fullString = true)
    }

    @Test
    fun arabic_full_string_host_edit_preserves_mixed_direction_text() {
        verifyEdits(AppLanguage.Arabic, "Table Test نور", isHost = true, fullString = true)
    }

    @Test
    fun focused_draft_survives_language_theme_and_initial_argument_updates() = runComposeUiTest {
        val language = mutableStateOf(AppLanguage.English)
        val theme = mutableStateOf(ThemeMode.Light)
        val initial = mutableStateOf("")
        val submissions = mutableListOf<String>()
        var observedDirection: LayoutDirection? = null
        var observedTheme: ThemeMode? = null
        var observedDarkPalette = false
        setContent {
            ProvideAppLanguage(language.value) {
                ParlorTheme(themeMode = theme.value, reducedMotion = true) {
                    val direction = LocalLayoutDirection.current
                    val mode = ParlorTheme.themeMode
                    val darkPalette = ParlorTheme.colors == CozyNoirPalette
                    SideEffect {
                        observedDirection = direction
                        observedTheme = mode
                        observedDarkPalette = darkPalette
                    }
                    NameInputScreen(
                        isHost = false,
                        initial = initial.value,
                        onConfirm = submissions::add,
                        onBack = {},
                    )
                }
            }
        }
        onNode(hasSetTextAction()).performTouchInput { click() }.performTextInput("Table Test")
        onNodeWithText("Your name").assertExists()
        assertEquals(LayoutDirection.Ltr, observedDirection)
        assertEquals(ThemeMode.Light, observedTheme)
        assertDraft("Table Test")
        runOnIdle {
            language.value = AppLanguage.Arabic
            theme.value = ThemeMode.Dark
            initial.value = "Different initial value"
        }
        waitForIdle()
        onNodeWithText("اسمك").assertExists()
        onNodeWithContentDescription(continueDescription(AppLanguage.Arabic, isHost = false))
            .assertIsEnabled()
        assertEquals(LayoutDirection.Rtl, observedDirection)
        assertEquals(ThemeMode.Dark, observedTheme)
        assertEquals(true, observedDarkPalette)
        assertDraft("Table Test")
        onNode(hasSetTextAction()).assertIsFocused().performTextInput(" نور")
        assertDraft("Table Test نور")
        assertEquals(emptyList(), submissions)
    }

    private fun verifyEdits(language: AppLanguage, input: String, isHost: Boolean, fullString: Boolean = false) {
        val previousLocale = Locale.getDefault()
        try {
            runComposeUiTest { verifyEditsInComposition(language, input, isHost, fullString) }
            assertEquals(previousLocale, Locale.getDefault(), "Disposal must restore the process locale")
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    private fun ComposeUiTest.verifyEditsInComposition(
        language: AppLanguage,
        input: String,
        isHost: Boolean,
        fullString: Boolean,
    ) {
        val submissions = mutableListOf<String>()
        setContent {
            ProvideAppLanguage(language) {
                ParlorTheme(reducedMotion = true) {
                    NameInputScreen(
                        isHost = isHost,
                        initial = "",
                        onConfirm = submissions::add,
                        onBack = {},
                    )
                }
            }
        }
        val field = onNode(hasSetTextAction())
        field.performTouchInput { click() }.assertIsFocused()
        if (fullString) {
            // Desktop edit-command receiver coverage only: this does not
            // synthesize UIKit keyboard events or certify native delivery.
            field.performTextInput(input)
            assertDraft(input)
        } else {
            input.forEachIndexed { index, character ->
                field.performTextInput(character.toString())
                assertDraft(input.take(index + 1))
            }
        }
        field.performImeAction()
        field.assertIsNotFocused()
        assertDraft(input)
        assertEquals(emptyList(), submissions, "Done dismisses focus; only explicit Continue submits")
        field.performTouchInput { click() }.assertIsFocused()
        assertDraft(input)
        onNodeWithContentDescription(continueDescription(language, isHost)).assertIsEnabled()
            .performTouchInput { click() }
        assertEquals(listOf(input), submissions)
    }

    private fun continueDescription(language: AppLanguage, isHost: Boolean): String = when {
        language == AppLanguage.Arabic && isHost -> "احفظ اسمك وتابع لإعداد طاولتك."
        language == AppLanguage.Arabic -> "احفظ اسمك وتابع لإدخال رمز الغرفة."
        isHost -> "Save your name and continue to set up your table."
        else -> "Save your name and continue to enter a room code."
    }

    private fun ComposeUiTest.assertDraft(expected: String) {
        val actual = onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals(expected, actual)
    }
}
