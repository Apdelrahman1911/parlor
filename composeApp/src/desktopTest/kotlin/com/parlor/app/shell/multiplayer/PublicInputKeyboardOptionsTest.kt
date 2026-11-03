package com.parlor.app.shell.multiplayer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual Compose platform requests, not executed UIKit keyboard behavior. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class PublicInputKeyboardOptionsTest {
    @Test
    fun english_room_code_requests_ascii_without_autocorrection() = verifyOptions(AppLanguage.English, isCode = true)

    @Test
    fun arabic_room_code_requests_ascii_without_autocorrection() = verifyOptions(AppLanguage.Arabic, isCode = true)

    @Test
    fun english_name_retains_language_appropriate_text_keyboard() = verifyOptions(AppLanguage.English, isCode = false)

    @Test
    fun arabic_name_retains_language_appropriate_text_keyboard() = verifyOptions(AppLanguage.Arabic, isCode = false)

    private fun verifyOptions(language: AppLanguage, isCode: Boolean) = runComposeUiTest {
        val requests = mutableListOf<ImeOptions>()
        setContent {
            ProvideAppLanguage(language) {
                ParlorTheme(reducedMotion = true) {
                    InterceptPlatformTextInput(
                        interceptor = { request, _ ->
                            requests += request.imeOptions
                            awaitCancellation()
                        },
                    ) {
                        if (isCode) {
                            JoinPromptScreen(onConfirm = {}, onCancel = {})
                        } else {
                            NameInputScreen(isHost = false, initial = "", onConfirm = {}, onBack = {})
                        }
                    }
                }
            }
        }
        onNode(hasSetTextAction()).performClick()
        runOnIdle {
            val options = requests.single()
            assertEquals(if (isCode) KeyboardType.Ascii else KeyboardType.Text, options.keyboardType)
            assertEquals(
                if (isCode) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                options.capitalization,
            )
            assertEquals(ImeAction.Done, options.imeAction)
            assertTrue(options.singleLine)
            if (isCode) assertFalse(options.autoCorrect) else assertTrue(options.autoCorrect)
        }
    }
}
