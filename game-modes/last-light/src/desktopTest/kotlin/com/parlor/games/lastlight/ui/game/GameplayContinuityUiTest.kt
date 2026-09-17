package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.SessionExitKind
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.lastlight.ui.LastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightProcessVisibility
import com.parlor.games.lastlight.ui.LocalLastLightVisibilityReader
import com.parlor.games.lastlight.ui.awake.LastLightScreenAwake
import com.parlor.games.lastlight.ui.awake.LocalLastLightScreenAwake
import com.parlor.games.lastlight.ui.flow.common.LastLightExitConfirmation
import com.parlor.games.lastlight.ui.flow.multidevice.LastLightConnectionRecovery
import com.parlor.games.lastlight.ui.flow.multidevice.LastLightRecoveryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GameplayContinuityUiTest {
    @Test
    fun awakeOptionDefaultsOffAndReleasesForBackgroundResultsExitAndNewMatch() = runComposeUiTest {
        val output = RecordingScreenAwake()
        var visibility by mutableStateOf(LastLightProcessVisibility(true, 0L))
        var view by mutableStateOf(playingView())
        var sessionId by mutableStateOf("first-match")
        var mounted by mutableStateOf(true)
        setContent {
            LastLightTestFrame {
                CompositionLocalProvider(LocalLastLightScreenAwake provides output) {
                    if (mounted) TestSessionTable(view, sessionId = sessionId, visibility = visibility)
                }
            }
        }
        runOnIdle { assertFalse(output.awake) }
        onNodeWithTag("game-options").performClick()
        onNodeWithText("Keep screen awake").bringIntoView().assertIsOff().performClick().assertIsOn()
        runOnIdle { assertTrue(output.awake) }
        onNodeWithTag("game-options-close").bringIntoView().performClick()
        runOnIdle { visibility = LastLightProcessVisibility(false, 1L) }
        runOnIdle { assertFalse(output.awake); visibility = LastLightProcessVisibility(true, 1L) }
        runOnIdle { assertTrue(output.awake); view = roundResultView() }
        runOnIdle { assertFalse(output.awake); view = finishedView() }
        runOnIdle { assertFalse(output.awake); view = playingView() }
        runOnIdle { assertTrue(output.awake); sessionId = "new-match" }
        runOnIdle { assertFalse(output.awake); mounted = false }
        runOnIdle { assertFalse(output.awake); assertEquals(1, output.closes) }
    }

    @Test
    fun staleOptionCallbackCannotEnableAwakeAfterNativeVisibilityChanged() = runComposeUiTest {
        val output = RecordingScreenAwake()
        var currentVisibility = LastLightProcessVisibility(true, 0L)
        setContent {
            LastLightTestFrame {
                CompositionLocalProvider(
                    LocalLastLightScreenAwake provides output,
                    LocalLastLightVisibilityReader provides { currentVisibility },
                ) { TestSessionTable(playingView()) }
            }
        }
        onNodeWithTag("game-options").performClick()
        val toggle = onNodeWithText("Keep screen awake").bringIntoView()
        val callback = checkNotNull(toggle.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        runOnIdle {
            currentVisibility = LastLightProcessVisibility(false, 1L)
            callback()
        }
        toggle.assertIsOff()
        runOnIdle { assertFalse(output.awake) }
        runOnIdle {
            currentVisibility = LastLightProcessVisibility(true, 1L)
            callback()
        }
        toggle.assertIsOff()
        runOnIdle { assertFalse(output.awake) }
    }

    @Test
    fun englishRecoveryReplacesPrivateTableAndKeepsGuardedLeave() = verifyRecovery(AppLanguage.English)

    @Test
    fun arabicRecoveryAndAwakeOptionFitCompactDoubleTextLayout() = verifyRecovery(AppLanguage.Arabic, largeText = true)

    @Test
    fun englishRecoveryNamesMissingHostOrPlayerAndDistinguishesLocalSync() = verifyRecoveryContext(AppLanguage.English)

    @Test
    fun arabicRecoveryNamesMissingHostOrPlayerAtDoubleTextScale() = verifyRecoveryContext(AppLanguage.Arabic)

    private fun verifyRecoveryContext(language: AppLanguage) = runComposeUiTest {
        val arabic = language == AppLanguage.Arabic
        val name = if (arabic) "نور" else "Alex"
        var recovery by mutableStateOf<LastLightRecoveryState>(LastLightRecoveryState.ReconnectingToHost(name))
        setContent {
            LastLightTestFrame(width = 320.dp, height = 680.dp, fontScale = 2f, language = language) {
                CompositionLocalProvider(LocalLastLightProcessVisibility provides LastLightProcessVisibility(true, 0L)) {
                    LastLightConnectionRecovery(state = recovery, onRequestLeave = {})
                }
            }
        }
        onNodeWithText(if (arabic) "جارٍ إعادة الاتصال بـ $name" else "Reconnecting to $name").bringIntoView().assertExists()
        onNodeWithTag("game-recovery-leave").bringIntoView().assertIsEnabled()
        onNodeWithTag("game-table").assertDoesNotExist()
        runOnIdle { recovery = LastLightRecoveryState.WaitingForPlayer(name) }
        onNodeWithText(if (arabic) "بانتظار $name" else "Waiting for $name").bringIntoView().assertExists()
        onNodeWithTag("game-recovery-leave").bringIntoView().assertIsEnabled()
        runOnIdle { recovery = LastLightRecoveryState.Syncing }
        onNodeWithText(if (arabic) "جارٍ مزامنة الطاولة" else "Syncing the table").bringIntoView().assertExists()
        onAllNodes(hasContentDescription("private-", substring = true), useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("game-recovery-leave").bringIntoView().assertIsEnabled()
    }

    private fun verifyRecovery(language: AppLanguage, largeText: Boolean = false) = runComposeUiTest {
        var recovering by mutableStateOf(false)
        var confirmLeave by mutableStateOf(false)
        var busy by mutableStateOf(false)
        var visibility by mutableStateOf(LastLightProcessVisibility(true, 0L))
        var exits = 0
        setContent {
            LastLightTestFrame(width = 320.dp, height = 680.dp, fontScale = if (largeText) 2f else 1f, language = language) {
                CompositionLocalProvider(LocalLastLightProcessVisibility provides visibility) {
                    when {
                        confirmLeave -> LastLightExitConfirmation(
                            SessionExitKind.Peer, onStay = { confirmLeave = false }, onExit = { exits++ }, exitInFlight = false,
                        )
                        recovering -> LastLightConnectionRecovery(
                            state = LastLightRecoveryState.Reconnecting,
                            onRequestLeave = { confirmLeave = true }, actionsEnabled = !busy,
                        )
                        else -> TestSessionTable(playingView())
                    }
                }
            }
        }
        onNodeWithTag("game-options").performClick()
        val awake = if (language == AppLanguage.Arabic) "إبقاء الشاشة مضاءة" else "Keep screen awake"
        onNodeWithText(awake).bringIntoView().assertIsOff().performClick().assertIsOn()
        onNodeWithTag("game-options-close").bringIntoView().performClick()
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        onNodeWithTag("game-card-0").bringIntoView().assertExists()
        runOnIdle { recovering = true }
        onNodeWithTag("game-connection-recovery").assertExists()
        onNodeWithText(if (language == AppLanguage.Arabic) "جارٍ إعادة الاتصال" else "Reconnecting").bringIntoView().assertExists()
        onAllNodes(hasTestTag("game-table-backdrop")).assertCountEquals(1)
        onNodeWithTag("game-card-0").assertDoesNotExist()
        onNodeWithTag("game-table").assertDoesNotExist()
        onAllNodes(hasContentDescription("private-", substring = true), useUnmergedTree = true).assertCountEquals(0)
        val leave = onNodeWithTag("game-recovery-leave").bringIntoView().assertIsEnabled()
        runOnIdle { busy = true }
        leave.assertIsNotEnabled().performClick()
        assertFalse(confirmLeave)
        runOnIdle { busy = false; visibility = LastLightProcessVisibility(false, 1L) }
        leave.assertIsNotEnabled()
        runOnIdle { visibility = LastLightProcessVisibility(true, 1L) }
        leave.bringIntoView().performClick()
        onNodeWithTag("game-exit-confirmation").assertExists()
        assertEquals(0, exits)
        onNodeWithTag("game-stay").bringIntoView().performClick()
        onNodeWithTag("game-connection-recovery").assertExists()
        onNodeWithTag("game-card-0").assertDoesNotExist()
    }

    private class RecordingScreenAwake : LastLightScreenAwake {
        var awake = false
        var closes = 0
        override fun prepare() = Unit
        override fun setEnabled(enabled: Boolean) { awake = enabled }
        override fun close() { awake = false; closes++ }
    }
}
