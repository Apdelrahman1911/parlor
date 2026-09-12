package com.parlor.games.mafia.ui.screens.setup

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MafiaSetupToggleLayoutTest {
    @Test
    fun english_compact_toggle_keeps_room_for_its_switch(): Unit = verifySwitchRoom(AppLanguage.English, 1f)

    @Test
    fun arabic_compact_toggle_keeps_room_for_its_switch(): Unit = verifySwitchRoom(AppLanguage.Arabic, 1f)

    @Test
    fun english_compact_large_text_toggle_keeps_room_for_its_switch(): Unit = verifySwitchRoom(AppLanguage.English, 2f)

    @Test
    fun arabic_compact_large_text_toggle_keeps_room_for_its_switch(): Unit = verifySwitchRoom(AppLanguage.Arabic, 2f)

    private fun verifySwitchRoom(language: AppLanguage, fontScale: Float): Unit = runComposeUiTest {
        val label = if (language == AppLanguage.Arabic) {
            "يستطيع الدكتور حماية نفس اللاعب في ليالٍ متتالية"
        } else {
            "Doctor can protect the same player on consecutive nights"
        }
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        MafiaSetupScreen(
                            playerCount = 6,
                            initialSettings = MafiaSettings(MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1)),
                            onStart = {},
                            modifier = Modifier.size(320.dp, 640.dp),
                        )
                    }
                }
            }
        }
        val toggle = onNodeWithText(label).performScrollTo()
        waitForIdle()
        val row = toggle.fetchSemanticsNode().boundsInRoot
        val text = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(text.width > 0f && row.width > 0f, "The actual setup label and toggle must be laid out")
        // The merged row remains clickable even when its label consumes all available width.
        // Pinned Material3 uses a 52dp track. A clickable row alone does not prove the track fits.
        assertTrue(
            row.width - text.width >= 52f,
            "$language fontScale=$fontScale: label ${text.width}px leaves no switch room in ${row.width}px row",
        )
    }
}
