package com.parlor.app.shell.multiplayer

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Actual shared field/button callbacks; not native delivery, LAN or device evidence. */
@OptIn(ExperimentalTestApi::class)
class JoinInputLiveCodeTest {
    @Test
    fun retained_enabled_join_rejects_invalid_live_codes_without_consuming_a_later_valid_choice() {
        for (invalid in listOf("", "ABCD2")) verify(invalid)
    }

    @Test
    fun retained_join_submits_the_current_valid_code_before_recomposition() = verify(invalid = null)

    private fun verify(invalid: String?) {
        for (language in AppLanguage.entries) runComposeUiTest {
            val submitted = mutableListOf<String>()
            setContent {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        JoinPromptScreen(onConfirm = submitted::add, onCancel = {})
                    }
                }
            }
            val edit = checkNotNull(onNode(hasSetTextAction()).fetchSemanticsNode()
                .config[SemanticsActions.SetText].action)
            runOnUiThread { check(edit(AnnotatedString("ABCD23"))) }
            waitForIdle()
            val description = if (language == AppLanguage.Arabic) "الاتصال بالمضيف باستخدام الرمز المُدخل."
            else "Connect to the host using the entered room code."
            val confirm = checkNotNull(onNodeWithContentDescription(description).assertIsEnabled()
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
            runOnUiThread {
                if (invalid != null) {
                    check(edit(AnnotatedString(invalid)))
                    confirm()
                    assertEquals(emptyList(), submitted, "A retained enabled callback must validate the live room code")
                }
                check(edit(AnnotatedString("GHJK45")))
                confirm()
                assertEquals(listOf("GHJK45"), submitted, "Join must read the current valid code without waiting for recomposition")
            }
        }
    }
}
