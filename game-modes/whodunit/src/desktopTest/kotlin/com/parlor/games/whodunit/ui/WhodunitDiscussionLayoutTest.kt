package com.parlor.games.whodunit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.ClueId
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.ui.screens.round.DiscussionScreen
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WhodunitDiscussionLayoutTest {
    @Test
    fun englishDiscussionKeepsTimerAndAdvanceReachableAtDoubleText() = verifyDiscussion(AppLanguage.English)

    @Test
    fun arabicDiscussionKeepsTimerAndAdvanceReachableAtDoubleText() = verifyDiscussion(AppLanguage.Arabic)

    private fun verifyDiscussion(language: AppLanguage) = runComposeUiTest {
        var advances = 0
        val arabic = language == AppLanguage.Arabic
        val clue = if (arabic) "كانت النافذة مفتوحة قبل وصول الضيوف." else "The window was open before the guests arrived."
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        Box(Modifier.size(320.dp, 640.dp).testTag("discussion-evidence")) {
                            DiscussionScreen(
                                timer = PublicTimerState("discussion-1", 60, 42, false),
                                revealedClues = listOf(RevealedClue(
                                    ClueId("example-clue"),
                                    clue,
                                    roundIndex = 1,
                                )),
                                authorityLabel = if (arabic) "المضيف يتحكم بالتقدم" else "Host leads the discussion",
                                onAdvance = { advances++ },
                            )
                        }
                    }
                }
            }
        }
        val timer = if (arabic) "النقاش. المتبقي 0:42 من 1:00." else "DISCUSSION. 0:42 remaining of 1:00."
        onNodeWithContentDescription(timer).performScrollTo().assertIsDisplayed()
        val output = File("build/reports/ux/whodunit-discussion-${language.tag}-2x.png")
        output.parentFile.mkdirs()
        ImageIO.write(onNodeWithTag("discussion-evidence").captureToImage().toAwtImage(), "png", output)
        onNodeWithText(if (arabic) "الجولة 1  ·  دليل" else "ROUND 1  ·  CLUE").performScrollTo().assertIsDisplayed()
        onNodeWithText(clue).performScrollTo().assertIsDisplayed()
        onNodeWithText(if (arabic) "تابع" else "Move On").performScrollTo().performClick()
        runOnIdle { assertEquals(1, advances) }
    }
}
