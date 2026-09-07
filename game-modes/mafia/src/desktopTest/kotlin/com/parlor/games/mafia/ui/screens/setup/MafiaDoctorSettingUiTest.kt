package com.parlor.games.mafia.ui.screens.setup

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MafiaDoctorSettingUiTest {
    @Test
    fun english_consecutive_nights_toggle_defaults_off_and_commits_on_and_off(): Unit =
        verifySelection(AppLanguage.English)

    @Test
    fun arabic_consecutive_nights_toggle_defaults_off_and_commits_on_and_off(): Unit =
        verifySelection(AppLanguage.Arabic)

    private fun verifySelection(language: AppLanguage): Unit = runComposeUiTest {
        val base = MafiaSettings(MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1))
        val repeatLabel = if (language == AppLanguage.Arabic) {
            "يستطيع الدكتور حماية نفس اللاعب في ليالٍ متتالية"
        } else {
            "Doctor can protect the same player on consecutive nights"
        }
        val startDescription = if (language == AppLanguage.Arabic) {
            "ابدأ لعبة حرامية وظباط بهذه الإعدادات"
        } else {
            "Start the Mafia game with these settings"
        }
        var selected: MafiaSettings? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        MafiaSetupScreen(
                            playerCount = 6,
                            initialSettings = base,
                            onStart = { selected = it },
                            modifier = Modifier.size(320.dp, 640.dp),
                        )
                    }
                }
            }
        }
        val toggle = onNodeWithText(repeatLabel)
        toggle.performScrollTo().assertIsOff().performClick().assertIsOn()
        onNodeWithContentDescription(startDescription).performScrollTo().performClick()
        assertEquals(base.copy(doctorCanProtectSamePlayerConsecutively = true), selected)

        toggle.performScrollTo().performClick().assertIsOff()
        onNodeWithContentDescription(startDescription).performScrollTo().performClick()
        assertEquals(base, selected)
    }
}
