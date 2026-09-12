package com.parlor.games.mafia.ui.screens.setup

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
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
        val failures = labels.flatMap { label -> stepperFailures(label, arabic) }
        assertTrue(failures.isEmpty(), "$language fontScale=$fontScale:\n${failures.joinToString("\n")}")
    }

    private fun ComposeUiTest.stepperFailures(label: String, arabic: Boolean): List<String> {
        val descriptions = if (arabic) {
            listOf("أنقص عدد $label", "زد عدد $label")
        } else {
            listOf("Decrease $label count", "Increase $label count")
        }
        val controls = descriptions.map { onNodeWithContentDescription(it) }
        // Layout ancestry, not semantic sibling flattening: button -> controls ->
        // stepper. This distinguishes the role label from the English Mafia title.
        val stepper = checkNotNull(controls[0].fetchSemanticsNode().layoutInfo.parentInfo?.parentInfo)
        val name = onNode(
            hasText(label) and SemanticsMatcher("label in the exact $label stepper layout") {
                it.layoutInfo.parentInfo === stepper
            },
            useUnmergedTree = true,
        )
        name.performScrollTo()
        waitForIdle()
        return descriptions.flatMapIndexed { index, description ->
            controlFailures(description, name, controls[1 - index])
        }
    }

    private fun ComposeUiTest.controlFailures(
        description: String,
        name: SemanticsNodeInteraction,
        sibling: SemanticsNodeInteraction,
    ): List<String> {
        val control = onNodeWithContentDescription(description)
        val measured = control.fetchSemanticsNode().size
        // Scroll each drawable control separately. Scrolling only the shorter
        // label can leave a correct-height button partially below the viewport.
        if (measured.width > 0 && measured.height > 0) {
            control.performScrollTo()
            waitForIdle()
        }
        val node = control.fetchSemanticsNode()
        val bounds = node.unclippedBounds()
        val label = name.fetchSemanticsNode().unclippedBounds()
        val other = sibling.fetchSemanticsNode().unclippedBounds()
        val frame = onNodeWithTag("stepper-layout-screen").fetchSemanticsNode().unclippedBounds()
        val failures = mutableListOf<String>()
        // size is un-clipped layout geometry, not an inflated touch hit region.
        if (node.size.width < 48 || node.size.height < 48) {
            failures += "$description measured ${node.size}; bounds=$bounds; label=$label"
        }
        if (node.size.width > 0 && node.size.height > 0) {
            val visible = node.boundsInRoot
            if (visible.width + 0.5f < node.size.width || visible.height + 0.5f < node.size.height) {
                failures += "$description remains clipped after control scroll: layout=$bounds; visible=$visible"
            }
            if (bounds.left < frame.left || bounds.right > frame.right ||
                bounds.top < frame.top || bounds.bottom > frame.bottom
            ) {
                failures += "$description overflows screen after control scroll: $bounds; screen=$frame"
            }
            if (bounds.overlaps(label) || bounds.overlaps(other)) {
                failures += "$description overlaps label/sibling: $bounds; label=$label; sibling=$other"
            }
        }
        return failures
    }

    private fun SemanticsNode.unclippedBounds(): Rect = Rect(positionInRoot, size.toSize())
}
