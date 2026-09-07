package com.parlor.games.mafia.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorAccent
import com.parlor.designsystem.theme.ParlorAccentScope
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual game-accented handoff cover; no duplicated rendering or palette. */
@OptIn(ExperimentalTestApi::class)
class MafiaCoverContrastTest {
    @Test
    fun englishLightCover(): Unit = verify(AppLanguage.English, ThemeMode.Light)

    @Test
    fun arabicLightCover(): Unit = verify(AppLanguage.Arabic, ThemeMode.Light)

    @Test
    fun englishDarkCover(): Unit = verify(AppLanguage.English, ThemeMode.Dark)

    @Test
    fun arabicDarkCover(): Unit = verify(AppLanguage.Arabic, ThemeMode.Dark)

    @Test
    fun englishLightLargeText(): Unit = verify(AppLanguage.English, ThemeMode.Light, 2f)

    @Test
    fun arabicLightLargeText(): Unit = verify(AppLanguage.Arabic, ThemeMode.Light, 2f)

    @Test
    fun englishDarkLargeText(): Unit = verify(AppLanguage.English, ThemeMode.Dark, 2f)

    @Test
    fun arabicDarkLargeText(): Unit = verify(AppLanguage.Arabic, ThemeMode.Dark, 2f)

    private fun verify(language: AppLanguage, theme: ThemeMode, scale: Float = 1f): Unit = runComposeUiTest {
        var confirmations = 0
        val name = if (language == AppLanguage.Arabic) "سارة" else "Sarah"
        val instruction = if (language == AppLanguage.Arabic) "سلّم الجهاز إلى $name" else "Pass the device to $name"
        val confirm = if (language == AppLanguage.Arabic) "أنا $name · متابعة" else "I’m $name · continue"
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(themeMode = theme, reducedMotion = true) {
                        ParlorAccentScope(ParlorAccent.Crimson) {
                            MafiaCandlelitCover(
                                playerName = name,
                                onDismiss = { confirmations++ },
                                modifier = Modifier.size(320.dp, 640.dp).testTag(COVER_TAG),
                            )
                        }
                    }
                }
            }
        }
        val cover = onNodeWithTag(COVER_TAG).assertIsDisplayed()
        val background = cover.captureToImage().toPixelMap()[0, 0]
        assertEquals(Color.Black, background, "The handoff must remain opaque")
        val label = onNodeWithText(instruction.uppercase(), useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val foreground = layout.layoutInput.style.color
        assertEquals(1f, foreground.alpha)
        val ratio = (foreground.luminance() + 0.05f) / (background.luminance() + 0.05f)
        assertTrue(ratio >= 4.5f, "$language $theme scale=$scale handoff contrast $ratio:1 < 4.5:1")
        assertFalse(
            layout.hasVisualOverflow,
            "Handoff scale=$scale size=${layout.size} paragraph=${layout.multiParagraph.width}x" +
                "${layout.multiParagraph.height} widthOverflow=${layout.didOverflowWidth} " +
                "heightOverflow=${layout.didOverflowHeight} lines=${layout.lineCount} " +
                "constraints=${layout.layoutInput.constraints}",
        )
        val bounds = label.getUnclippedBoundsInRoot()
        val viewport = cover.getUnclippedBoundsInRoot()
        assertTrue(bounds.right > bounds.left && bounds.left >= viewport.left && bounds.right <= viewport.right)
        assertEquals(0, confirmations, "Presentation must not reveal the private role")
        onNodeWithText(confirm).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, confirmations)
    }

    private companion object {
        const val COVER_TAG = "mafia-handoff-cover"
    }
}
