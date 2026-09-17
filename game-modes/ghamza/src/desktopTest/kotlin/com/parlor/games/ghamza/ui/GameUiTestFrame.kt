package com.parlor.games.ghamza.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.InPlaceBackOwner
import com.parlor.designsystem.components.ProvideInPlaceBackOwner
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.engine.state.Player
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun uiPlayers(count: Int) = List(count) { Player(PlayerId("seat-$it"), "Player $it", it) }

@Composable
internal fun GameUiFrame(
    language: AppLanguage = AppLanguage.English,
    width: Int = 360,
    height: Int = 740,
    fontScale: Float = 1f,
    reducedMotion: Boolean = true,
    backOwner: InPlaceBackOwner? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
        ProvideAppLanguage(language) {
            ParlorTheme(reducedMotion = reducedMotion) {
                Box(Modifier.size(width.dp, height.dp).testTag("game-viewport")) {
                    if (backOwner == null) content() else ProvideInPlaceBackOwner(backOwner, content)
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.reachable(): SemanticsNodeInteraction = apply {
    performScrollTo()
    val bounds = getUnclippedBoundsInRoot()
    assertTrue(bounds.bottom - bounds.top >= 48.dp, "Primary controls need a real touch target: $bounds")
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.assertFullText() {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val layout = layouts.single()
    val bounds = fetchSemanticsNode().size
    val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
    assertTrue(bounds.width >= ceil(widest), "Text crosses its layout width")
    assertTrue(bounds.height >= ceil(layout.multiParagraph.height), "Text crosses its layout height")
    assertFalse((0 until layout.lineCount).any(layout::isLineEllipsized))
    assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
}
