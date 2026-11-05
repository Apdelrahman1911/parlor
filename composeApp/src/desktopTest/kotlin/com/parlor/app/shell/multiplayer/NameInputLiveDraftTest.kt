package com.parlor.app.shell.multiplayer

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Real shared field and button callbacks in one UI turn; not native delivery or device evidence. */
@OptIn(ExperimentalTestApi::class)
class NameInputLiveDraftTest {
    @Test
    fun retained_confirmation_submits_the_current_complete_draft_before_recomposition() = verify(invalidFirst = false)

    @Test
    fun retained_enabled_confirmation_rejects_an_invalid_current_draft_without_consuming_a_later_valid_choice() =
        verify(invalidFirst = true)

    private fun verify(invalidFirst: Boolean) {
        for (language in AppLanguage.entries) {
            for (isHost in listOf(true, false)) runComposeUiTest {
                val previous = if (language == AppLanguage.Arabic) "مضيف سابق" else "Table Before"
                val current = if (language == AppLanguage.Arabic) "مضيف جديد" else "Table After"
                val submitted = mutableListOf<String>()
                setContent {
                    ProvideAppLanguage(language) {
                        ParlorTheme(reducedMotion = true) {
                            NameInputScreen(isHost, previous, onConfirm = { submitted += it }, onBack = {})
                        }
                    }
                }
                val edit = checkNotNull(onNode(hasSetTextAction()).fetchSemanticsNode()
                    .config[SemanticsActions.SetText].action)
                val confirm = checkNotNull(onNodeWithContentDescription(confirmation(language, isHost))
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
                runOnUiThread {
                    if (invalidFirst) {
                        check(edit(AnnotatedString("")))
                        confirm()
                        assertEquals(emptyList(), submitted, "An old enabled callback cannot validate the previous draft")
                    }
                    check(edit(AnnotatedString(current)))
                    confirm()
                    assertEquals(listOf(current), submitted, "Confirmation must read the live draft, not a composition snapshot")
                }
            }
        }
    }

    private fun confirmation(language: AppLanguage, isHost: Boolean): String = when {
        language == AppLanguage.Arabic && isHost -> "احفظ اسمك وتابع لإعداد طاولتك."
        language == AppLanguage.Arabic -> "احفظ اسمك وتابع لإدخال رمز الغرفة."
        isHost -> "Save your name and continue to set up your table."
        else -> "Save your name and continue to enter a room code."
    }
}
