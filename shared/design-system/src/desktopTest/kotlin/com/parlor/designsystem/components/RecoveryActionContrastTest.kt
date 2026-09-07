package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.designsystem.tokens.CozyNoirPalette
import com.parlor.designsystem.tokens.LightCozyNoirPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Read the color actually used by the production Text, not just the palette constants. */
@OptIn(ExperimentalTestApi::class)
class RecoveryActionContrastTest {
    @Test
    fun reconnecting_leave_is_readable_in_both_themes() {
        listOf(ThemeMode.Light, ThemeMode.Dark).forEach { theme ->
            assertLeaveContrast(theme, cover = true) {
                ReconnectingOverlay("Reconnecting", "Leave", "Leave", {})
            }
        }
    }

    @Test
    fun disconnected_host_leave_is_readable_in_both_themes() {
        listOf(ThemeMode.Light, ThemeMode.Dark).forEach { theme ->
            assertLeaveContrast(theme, cover = true) {
                HostDisconnectedOverlay("Disconnected", "Waiting", "Continue", "Continue", "Leave", "Leave", {}, {})
            }
        }
    }

    @Test
    fun ordinary_ghost_button_retains_its_theme_appropriate_foreground() {
        listOf(ThemeMode.Light, ThemeMode.Dark).forEach { theme ->
            assertLeaveContrast(theme, cover = false) {
                Box(Modifier.fillMaxSize().background(ParlorTheme.colors.surfaceCanvas)) {
                    ParlorButton("Leave", {}, "Leave", variant = ParlorButtonVariant.Ghost)
                }
            }
        }
    }

    private fun assertLeaveContrast(theme: ThemeMode, cover: Boolean, content: @Composable () -> Unit) =
        runComposeUiTest {
            val colors = if (theme == ThemeMode.Light) LightCozyNoirPalette else CozyNoirPalette
            setContent { ParlorTheme(themeMode = theme, reducedMotion = true, content = content) }
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText("Leave", useUnmergedTree = true)
                .assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val foreground = layouts.single().layoutInput.style.color
            val background = if (cover) colors.coverScreen else colors.surfaceCanvas
            assertEquals(if (cover) colors.coverScreenTextSecondary else colors.textSecondary, foreground)
            assertTrue(contrast(foreground, background) >= 4.5f, "Leave must meet normal-text AA contrast in $theme")
        }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) / (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
