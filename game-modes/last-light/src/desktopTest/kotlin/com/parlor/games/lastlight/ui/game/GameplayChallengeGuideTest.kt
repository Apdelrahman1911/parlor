package com.parlor.games.lastlight.ui.game

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GameplayChallengeGuideTest {
    @Test
    fun englishGuideExplainsTheRulesWithoutRevealingTheHand() = verifyGuide(AppLanguage.English)

    @Test
    fun arabicGuideExplainsTheRulesWithoutRevealingTheHand() = verifyGuide(AppLanguage.Arabic)

    private fun verifyGuide(language: AppLanguage) = runComposeUiTest {
        val bodyStart = if (language == AppLanguage.Arabic) "تحدَّ الادعاء الأخير" else "Challenge the latest claim"
        setContent {
            LastLightTestFrame(width = 320.dp, fontScale = 2f, language = language) { TestTable() }
        }
        onNodeWithText(bodyStart, substring = true).assertDoesNotExist()
        onNodeWithTag("game-challenge-help").bringIntoView().performClick()
        onNodeWithText(bodyStart, substring = true).bringIntoView()
        val output = File("build/reports/ux/last-light-guide-${language.tag}-2x.png")
        output.parentFile.mkdirs()
        ImageIO.write(onNodeWithTag(LAST_LIGHT_VIEWPORT).captureToImage().toAwtImage(), "png", output)
        onNodeWithTag("game-card-0").assertDoesNotExist()
        onNodeWithTag("game-challenge-help").bringIntoView().performClick()
        onNodeWithText(bodyStart, substring = true).assertDoesNotExist()
        onNodeWithTag("game-card-0").assertDoesNotExist()
    }
}
