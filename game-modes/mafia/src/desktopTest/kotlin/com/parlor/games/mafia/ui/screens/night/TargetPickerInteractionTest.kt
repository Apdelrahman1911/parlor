package com.parlor.games.mafia.ui.screens.night

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.ui.screens.vote.VoteCastScreen
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TargetPickerInteractionTest {
    @Test
    fun englishLargeTextSelectionHasOneActionableRadioRow() = verifySelection(AppLanguage.English)

    @Test
    fun arabicLargeTextSelectionHasOneActionableRadioRow() = verifySelection(AppLanguage.Arabic)

    @Test
    fun publicBallotNeverPromisesPrivateVoting() = runComposeUiTest {
        setContent {
            ProvideAppLanguage(AppLanguage.English) {
                ParlorTheme(reducedMotion = true) {
                    VoteCastScreen(
                        voterName = "Alice", candidates = listOf(PickableTarget(PlayerId("bob"), "Bob")),
                        revoteRound = 0, onCast = {}, onAbstain = {},
                    )
                }
            }
        }
        onNodeWithText("Public vote").assertExists()
        onNodeWithText("Your vote is visible to everyone.").assertExists()
        onNodeWithText("PRIVATE SCREEN").assertDoesNotExist()
    }

    private fun verifySelection(language: AppLanguage) = runComposeUiTest {
        val id = PlayerId("player")
        val name = if (language == AppLanguage.Arabic) "لاعب باسم طويل يحتاج إلى مساحة كافية" else "Player with a long readable name"
        var submitted: PlayerId? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ProvideAppLanguage(language) {
                    ParlorTheme(reducedMotion = true) {
                        TargetPickerScreen(
                            eyebrow = "Night", headline = "Choose", instructions = "Choose one player",
                            targets = listOf(PickableTarget(id, name)), submitLabel = "Confirm",
                            onSubmit = { submitted = it }, skipLabel = "Skip", allowSkip = true,
                            modifier = Modifier.size(320.dp, 640.dp).testTag("mafia-target-evidence"),
                        )
                    }
                }
            }
        }
        onNodeWithContentDescription("Confirm").assertIsNotEnabled()
        onNodeWithText(name).performScrollTo().performClick().assertIsSelected()
        val output = File("build/reports/ux/mafia-target-${language.tag}-2x.png")
        output.parentFile.mkdirs()
        ImageIO.write(onNodeWithTag("mafia-target-evidence").captureToImage().toAwtImage(), "png", output)
        onNodeWithContentDescription("Confirm").performScrollTo().performClick()
        assertEquals(id, submitted)
    }
}
