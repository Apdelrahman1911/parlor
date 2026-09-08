package com.parlor.games.mafia.ui.screens.setup

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
class MafiaSetupStepperLayoutTest {
    @Test
    fun english_compact_stepper_controls_keep_their_full_size(): Unit = verifySteppers(AppLanguage.English, 1f)

    @Test
    fun arabic_compact_stepper_controls_keep_their_full_size(): Unit = verifySteppers(AppLanguage.Arabic, 1f)

    @Test
    fun english_compact_large_text_stepper_controls_keep_their_full_size(): Unit = verifySteppers(AppLanguage.English, 2f)

    @Test
    fun arabic_compact_large_text_stepper_controls_keep_their_full_size(): Unit = verifySteppers(AppLanguage.Arabic, 2f)

    private fun verifySteppers(language: AppLanguage, fontScale: Float): Unit = runComposeUiTest {
        val arabic = language == AppLanguage.Arabic
        val labels = if (arabic) {
            listOf("حرامي", "محقق", "دكتور", "الحد الأقصى لإعادات التصويت")
        } else {
            listOf("Mafia", "Detective", "Doctor", "Maximum revotes")
        }
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        MafiaSetupScreen(
                            playerCount = 6,
                            initialSettings = MafiaSettings(MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1)),
                            onStart = {},
                            modifier = Modifier.size(320.dp, 640.dp).testTag("stepper-layout-screen"),
                        )
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        labels.forEach { label ->
            // Scroll the actual label, not a possibly zero-width control; a selector
            // or scroll failure must remain an error, not be counted as layout proof.
            onNodeWithText(label).performScrollTo()
            waitForIdle()
            val frame = onNodeWithTag("stepper-layout-screen").fetchSemanticsNode().boundsInRoot
            val name = onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            val descriptions = if (arabic) {
                listOf("أنقص عدد $label", "زد عدد $label")
            } else {
                listOf("Decrease $label count", "Increase $label count")
            }
            val controls = descriptions.map { description ->
                val bounds = onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
                // ParlorIconButton's existing spacing.xxl contract is 48dp, not
                // just a 24dp icon or an inflated minimum touch hit region.
                if (bounds.width < 48f || bounds.height < 48f) {
                    failures += "$description clipped/shrunk: $bounds; label=$name; screen=$frame"
                }
                if (bounds.left < frame.left || bounds.right > frame.right ||
                    bounds.top < frame.top || bounds.bottom > frame.bottom
                ) {
                    failures += "$description extends beyond screen: $bounds; screen=$frame"
                }
                if (bounds.overlaps(name)) {
                    failures += "$description overlaps its label: control=$bounds; label=$name"
                }
                bounds
            }
            if (controls[0].overlaps(controls[1])) {
                failures += "$label decrement/increment overlap: $controls"
            }
        }
        assertTrue(failures.isEmpty(), "$language fontScale=$fontScale:\n${failures.joinToString("\n")}")
    }
}
