package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.parlor.app.shell.ShellAcceptanceFrame
import com.parlor.app.shell.assertPhoneTouchTarget
import com.parlor.app.shell.captureShellAcceptance
import com.parlor.app.shell.fullTextLayout
import com.parlor.app.shell.game.multiplayer.LobbyInvitation
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RoomCodeAccessibilityAcceptanceTest {
    @Test
    fun host_codes_are_complete_single_line_ltr_in_both_languages_and_themes() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                verifyInvitation(language, theme)
            }
        }
    }

    @Test
    fun join_codes_are_readable_and_only_six_valid_characters_enable_submission() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                verifyJoin(language, theme)
            }
        }
    }

    private fun verifyInvitation(language: AppLanguage, theme: ThemeMode) = runComposeUiTest {
        setContent {
            ShellAcceptanceFrame(language, theme) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ParlorTheme.spacing.l)) {
                    LobbyInvitation(code = CODE, hostName = "أحمد (Ahmed 12)")
                }
            }
        }
        onNodeWithText(CODE).performScrollTo()
        assertCompleteCode(onNodeWithText(CODE).fullTextLayout())
        captureShellAcceptance("host-room-code-${language.tag}-${theme.tag}-2.0")
    }

    private fun verifyJoin(language: AppLanguage, theme: ThemeMode) = runComposeUiTest {
        val submitted = mutableListOf<String>()
        var canceled = 0
        setContent {
            ShellAcceptanceFrame(language, theme) {
                JoinPromptScreen(onConfirm = submitted::add, onCancel = { canceled++ })
            }
        }
        val arabic = language == AppLanguage.Arabic
        val confirm = onNodeWithContentDescription(
            if (arabic) "الاتصال بالمضيف باستخدام الرمز المُدخل." else "Connect to the host using the entered room code.",
        )
        val input = onNode(hasSetTextAction())
        confirm.assertIsNotEnabled()
        input.performScrollTo().performTextReplacement("01oiا")
        confirm.assertIsNotEnabled()
        input.performTextReplacement("wx2y3")
        input.assertTextContains("WX2Y3")
        confirm.assertIsNotEnabled()
        input.performTextReplacement(" wx-2y 3zأ EXTRA")
        input.assertTextContains(CODE)
        assertCompleteCode(input.fullTextLayout())
        input.assertPhoneTouchTarget()
        input.performImeAction()
        confirm.assertIsEnabled()
        confirm.assertPhoneTouchTarget()
        captureShellAcceptance("join-room-code-${language.tag}-${theme.tag}-2.0")
        confirm.performClick()
        runOnIdle { assertEquals(listOf(CODE), submitted) }
        input.performTextReplacement("wx2")
        confirm.assertIsNotEnabled()
        onNodeWithContentDescription(if (arabic) "العودة إلى الشاشة الرئيسية." else "Return to the home screen.")
            .performScrollTo().performClick()
        runOnIdle { assertEquals(1, canceled) }
    }

    private fun assertCompleteCode(layout: TextLayoutResult) {
        assertEquals(CODE, layout.layoutInput.text.text)
        assertEquals(1, layout.lineCount, "The six-character code must not wrap")
        for (index in CODE.indices) {
            val glyph = layout.getBoundingBox(index)
            assertTrue(glyph.left >= 0 && glyph.right <= layout.size.width, "Character $index is clipped")
            if (index > 0) {
                assertTrue(glyph.left > layout.getBoundingBox(index - 1).left, "Code order must stay LTR")
            }
        }
    }

    private companion object {
        const val CODE = "WX2Y3Z"
    }
}
