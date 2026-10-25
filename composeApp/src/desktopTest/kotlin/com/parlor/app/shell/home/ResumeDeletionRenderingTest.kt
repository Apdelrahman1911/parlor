package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.ResumeDeletionRequest
import com.parlor.app.ResumeDeletionState
import com.parlor.app.ResumeDeletionTarget
import com.parlor.core.ids.SessionId
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.transport.ResumableSessionSelection
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ResumeDeletionRenderingTest {
    @Test
    fun local_and_multiplayer_entries_offer_cancel_then_confirm_in_both_languages_at_large_text() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            verifyJourney(language, multiplayer = false)
            verifyJourney(language, multiplayer = true)
        }
    }

    private fun verifyJourney(language: AppLanguage, multiplayer: Boolean) = runComposeUiTest {
        var confirmed = 0
        var cancelled = 0
        val session = SessionId("saved-local")
        setContent {
            var state by remember { mutableStateOf(ResumeDeletionState()) }
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        Box(Modifier.size(360.dp, 800.dp)) {
                            HomeScreen(
                                games = emptyList(),
                                onGameSelected = {},
                                unfinishedSessions = if (multiplayer) emptyList() else {
                                    listOf(LocalRecoveryEntry(session, null))
                                },
                                hasResumableMultiplayer = multiplayer,
                                onDiscardLocal = { id, label ->
                                    state = ResumeDeletionState(ResumeDeletionRequest(ResumeDeletionTarget.Local(id), label))
                                },
                                onDiscardMultiplayer = { label ->
                                    val target = ResumeDeletionTarget.Multiplayer(object : ResumableSessionSelection {})
                                    state = ResumeDeletionState(ResumeDeletionRequest(target, label))
                                },
                            )
                            ResumeDeletionDialog(
                                state = state,
                                onCancel = { cancelled++; state = ResumeDeletionState() },
                                onConfirm = { confirmed++; state = state.copy(busy = true) },
                            )
                        }
                    }
                }
            }
        }
        val remove = if (language == AppLanguage.Arabic) "حذف الجلسة المحفوظة" else "Remove saved session"
        onNodeWithText(remove).performScrollTo().performClick()
        onNodeWithTag("resume-delete-cancel").performClick()
        runOnIdle { assertEquals(1, cancelled); assertEquals(0, confirmed) }
        onNodeWithText(remove).performScrollTo().performClick()
        onNodeWithTag("resume-delete-confirm").performClick()
        onNodeWithTag("resume-delete-confirm").assertIsNotEnabled()
        onNodeWithTag("resume-delete-cancel").assertIsNotEnabled()
        onNodeWithTag("resume-delete-status").assertExists()
        runOnIdle { assertEquals(1, confirmed) }
    }
}
