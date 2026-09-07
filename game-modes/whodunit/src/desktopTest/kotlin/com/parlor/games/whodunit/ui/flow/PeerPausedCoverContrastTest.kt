package com.parlor.games.whodunit.ui.flow

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Render the actual peer-only pause cover, not a duplicate palette or fake UI. */
@OptIn(ExperimentalTestApi::class)
class PeerPausedCoverContrastTest {
    @Test
    fun englishLightCoverTextRemainsReadable(): Unit = verify(AppLanguage.English, ThemeMode.Light)

    @Test
    fun arabicLightCoverTextRemainsReadable(): Unit = verify(AppLanguage.Arabic, ThemeMode.Light)

    @Test
    fun englishDarkCoverTextRemainsReadable(): Unit = verify(AppLanguage.English, ThemeMode.Dark)

    @Test
    fun arabicDarkCoverTextRemainsReadable(): Unit = verify(AppLanguage.Arabic, ThemeMode.Dark)

    private fun verify(language: AppLanguage, theme: ThemeMode) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(themeMode = theme, reducedMotion = true) {
                        PeerHostPausedBanner(Modifier.size(320.dp, 640.dp).testTag(COVER_TAG))
                    }
                }
            }
        }
        val title = if (language == AppLanguage.Arabic) "متوقّفة" else "Paused"
        val body = if (language == AppLanguage.Arabic) {
            "المضيف أوقف اللعبة مؤقتًا."
        } else {
            "The host paused the game."
        }
        val cover = onNodeWithTag(COVER_TAG).assertIsDisplayed()
        val semantics = cover.fetchSemanticsNode().config
        assertEquals(title, semantics[SemanticsProperties.PaneTitle])
        assertEquals(LiveRegionMode.Assertive, semantics[SemanticsProperties.LiveRegion])
        val background = cover.captureToImage().toPixelMap()[0, 0]
        assertEquals(Color.Black, background, "The host pause cover must not reveal the game underneath")
        val violations = mutableListOf<String>()
        listOf(title.uppercase() to 4.5f, body to 3f).forEach { (text, minimum) ->
            val layouts = mutableListOf<TextLayoutResult>()
            val node = onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val foreground = layouts.single().layoutInput.style.color
            assertEquals(1f, foreground.alpha)
            val ratio = (foreground.luminance() + 0.05f) / (background.luminance() + 0.05f)
            if (ratio < minimum) violations += "$text: $ratio:1 < $minimum:1"
            if (text == title.uppercase()) {
                assertTrue(node.fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
            }
        }
        assertTrue(violations.isEmpty(), "$theme $language pause-cover contrast: ${violations.joinToString()}")
    }

    private companion object {
        const val COVER_TAG = "peer-host-paused-cover"
    }
}
