package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.lastlight.domain.model.Card
import com.parlor.games.lastlight.domain.model.CardRank
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GameplayResultDesignTest {
    @Test
    fun english_proof_and_early_burnout_are_readable_on_a_compact_phone() = verifyResults(AppLanguage.English)

    @Test
    fun arabic_proof_and_early_burnout_are_readable_on_a_compact_phone() = verifyResults(AppLanguage.Arabic)

    @Test
    fun english_proof_and_fuse_explanation_support_double_text() = verifyResults(AppLanguage.English, scale = 2f)

    @Test
    fun arabic_proof_and_fuse_explanation_support_double_text() = verifyResults(AppLanguage.Arabic, scale = 2f)

    private fun verifyResults(language: AppLanguage, scale: Float = 1f) = runComposeUiTest {
        val base = roundResultView()
        val original = checkNotNull(base.roundOutcome)
        var view by mutableStateOf(base)
        setContent {
            LastLightTestFrame(width = 320.dp, height = 740.dp, fontScale = scale, language = language) {
                TestSessionTable(view)
            }
        }
        for (burnedOut in listOf(false, true)) {
            for (attempt in 1..2) {
                runOnIdle {
                    val outcome = original.copy(
                        penaltyAttempt = attempt,
                        burnedOut = burnedOut,
                        truthful = !burnedOut,
                        penalizedPlayerId = if (burnedOut) "a" else "b",
                        revealedCards = if (burnedOut) original.revealedCards else listOf(
                            Card("public-crown", CardRank.CROWN),
                            Card("public-crown-2", CardRank.CROWN),
                            Card("public-wild", CardRank.WILD),
                        ),
                    )
                    view = base.copy(
                        roundOutcome = outcome,
                        players = base.players.map {
                            it.copy(
                                eliminated = it.id == outcome.penalizedPlayerId && burnedOut,
                                penaltyAttempts = if (it.id == outcome.penalizedPlayerId) attempt else 0,
                            )
                        },
                    )
                }
                onNodeWithTag("game-fuse-result").performScrollTo()
                val name = "result-design-${language.tag}-$scale-test-$attempt-out-$burnedOut"
                val file = File("build/ui-snapshots/$name.png").apply { parentFile.mkdirs() }
                ImageIO.write(onNodeWithTag(LAST_LIGHT_VIEWPORT).captureToImage().toAwtImage(), "png", file)
                val tests = if (language == AppLanguage.Arabic) "اختُبر الفتيل $attempt من 6 مرات" else "Fuse tested $attempt of 6"
                onNodeWithText(tests).bringIntoView()
                val explanation = if (language == AppLanguage.Arabic) "ليست ست فرص للنجاة." else "Not six lives."
                onNodeWithText(explanation).bringIntoView()
                onNodeWithTag("game-next-round").bringIntoView().assertIsEnabled()
                val bounds = onNodeWithTag("game-next-round").getUnclippedBoundsInRoot()
                val viewport = onNodeWithTag(LAST_LIGHT_VIEWPORT).getUnclippedBoundsInRoot()
                assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
                onAllNodes(hasContentDescription("Card ", substring = true), useUnmergedTree = true).assertCountEquals(0)
            }
        }
    }
}
