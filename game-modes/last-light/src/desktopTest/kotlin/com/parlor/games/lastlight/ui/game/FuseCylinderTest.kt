package com.parlor.games.lastlight.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FuseCylinderTest {
    @Test
    fun every_untested_chamber_stays_unknown_and_only_completed_public_tests_are_marked() {
        for (attempts in 0..5) {
            val chambers = fuseChambers(attempts, burnedOut = false)
            assertEquals(6, chambers.size)
            assertEquals(List(attempts) { FuseChamberState.Safe } + List(6 - attempts) { FuseChamberState.Untested }, chambers)
        }
    }

    @Test
    fun burnout_is_explicit_on_the_first_second_or_any_later_test_not_only_the_sixth() {
        for (attempts in 1..6) {
            val chambers = fuseChambers(attempts, burnedOut = true)
            assertEquals(
                List(attempts - 1) { FuseChamberState.Safe } + FuseChamberState.Burnout +
                    List(6 - attempts) { FuseChamberState.Untested },
                chambers,
            )
        }
    }

    @Test
    fun public_history_and_legend_are_accessible_in_english() = verifyCylinder(AppLanguage.English)

    @Test
    fun public_history_and_legend_are_accessible_in_arabic() = verifyCylinder(AppLanguage.Arabic)

    private fun verifyCylinder(language: AppLanguage) = runComposeUiTest {
        setContent {
            LastLightTestFrame(language = language) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FuseCylinder(0, false, Modifier.size(88.dp).testTag("new-cylinder"))
                    FuseCylinder(2, false, Modifier.size(88.dp).testTag("safe-cylinder"))
                    FuseCylinder(1, true, Modifier.size(88.dp).testTag("first-cylinder"))
                    FuseCylinder(2, true, Modifier.size(88.dp).testTag("second-cylinder"))
                    FuseLegend()
                    FuseExplanation()
                }
            }
        }
        val arabic = language == AppLanguage.Arabic
        onNodeWithTag("new-cylinder").assertContentDescriptionEquals(
            if (arabic) "اختُبر الفتيل 0 من 6 مرات. لم يُختبَر." else "Fuse tested 0 of 6. Untested.",
        )
        onNodeWithTag("safe-cylinder").assertContentDescriptionEquals(
            if (arabic) "اختُبر الفتيل 2 من 6 مرات. اختبار آمن." else "Fuse tested 2 of 6. Safe test.",
        )
        onNodeWithTag("first-cylinder").assertContentDescriptionEquals(
            if (arabic) "اختُبر الفتيل 1 من 6 مرات. احتراق." else "Fuse tested 1 of 6. Burnout.",
        )
        onNodeWithTag("second-cylinder").assertContentDescriptionEquals(
            if (arabic) "اختُبر الفتيل 2 من 6 مرات. احتراق." else "Fuse tested 2 of 6. Burnout.",
        )
        val file = File("build/ui-snapshots/fuse-cylinder-${language.tag}.png").apply { parentFile.mkdirs() }
        ImageIO.write(onNodeWithTag(LAST_LIGHT_VIEWPORT).captureToImage().toAwtImage(), "png", file)
    }
}
