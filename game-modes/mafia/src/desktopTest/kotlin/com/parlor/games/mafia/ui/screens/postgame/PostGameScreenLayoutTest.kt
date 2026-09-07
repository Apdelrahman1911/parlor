package com.parlor.games.mafia.ui.screens.postgame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.networking.room.RoomInputPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PostGameScreenLayoutTest {
    @Test
    fun compact_english_long_name_does_not_consume_final_role_width(): Unit =
        verifyRoles(AppLanguage.English, fontScale = 1f)

    @Test
    fun compact_arabic_long_name_does_not_consume_final_role_width(): Unit =
        verifyRoles(AppLanguage.Arabic, fontScale = 1f)

    @Test
    fun compact_large_text_english_preserves_every_final_role(): Unit =
        verifyRoles(AppLanguage.English, fontScale = 2f)

    @Test
    fun compact_large_text_arabic_preserves_every_final_role(): Unit =
        verifyRoles(AppLanguage.Arabic, fontScale = 2f)

    @Test
    fun landscape_large_text_english_roles_remain_scrollable(): Unit =
        verifyRoles(AppLanguage.English, fontScale = 2f, width = 640.dp, height = 320.dp)

    @Test
    fun landscape_large_text_arabic_roles_remain_scrollable(): Unit =
        verifyRoles(AppLanguage.Arabic, fontScale = 2f, width = 640.dp, height = 320.dp)

    private fun verifyRoles(
        language: AppLanguage,
        fontScale: Float,
        width: Dp = 320.dp,
        height: Dp = 640.dp,
    ): Unit = runComposeUiTest {
        val letter = if (language == AppLanguage.Arabic) "ش" else "W"
        val longName = letter.repeat(RoomInputPolicy.MAX_DISPLAY_NAME_LENGTH)
        assertTrue(RoomInputPolicy.isValidDisplayName(longName))
        val labels = if (language == AppLanguage.Arabic) {
            listOf("حرامي", "دكتور", "محقق", "مدني")
        } else {
            listOf("Mafia", "Doctor", "Detective", "Civilian")
        }
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        Box(Modifier.size(width, height).testTag(VIEWPORT)) {
                            PostGameScreen(
                                winner = Team.Town,
                                finalRoles = listOf(
                                    longName to Role.Mafia,
                                    "Player B" to Role.Doctor,
                                    "Player C" to Role.Detective,
                                    "Player D" to Role.Civilian,
                                    "Player E" to Role.Civilian,
                                ),
                                onExit = {},
                            )
                        }
                    }
                }
            }
        }
        for (label in labels) {
            val nodes = onAllNodesWithText(label, useUnmergedTree = true)
            val count = nodes.fetchSemanticsNodes().size
            assertTrue(count > 0, "Missing final role: $label")
            repeat(count) { index ->
                val node = nodes[index]
                node.performScrollTo().assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                val viewport = onNodeWithTag(VIEWPORT).getUnclippedBoundsInRoot()
                assertTrue(bounds.right > bounds.left && bounds.bottom > bounds.top, "Empty role $label: $bounds")
                assertTrue(
                    bounds.left >= viewport.left && bounds.right <= viewport.right,
                    "Role $label outside viewport: bounds=$bounds viewport=$viewport",
                )
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertFalse(
                    layout.hasVisualOverflow,
                    "Final role is clipped: $label bounds=$bounds viewport=$viewport ${layout.diagnostics()}",
                )
            }
        }
        val nameLayouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(longName, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(nameLayouts) }
        val nameLayout = nameLayouts.single()
        assertFalse(
            nameLayout.hasVisualOverflow,
            "Long name is clipped instead of reflowed: ${nameLayout.diagnostics()}",
        )
    }

    private fun TextLayoutResult.diagnostics(): String =
        "size=$size paragraph=${multiParagraph.width}x${multiParagraph.height} " +
            "lines=$lineCount widthOverflow=$didOverflowWidth heightOverflow=$didOverflowHeight " +
            "constraints=${layoutInput.constraints}"

    private companion object {
        const val VIEWPORT = "mafia-postgame-viewport"
    }
}
