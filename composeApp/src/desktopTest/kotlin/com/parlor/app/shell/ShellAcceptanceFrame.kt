package com.parlor.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import java.io.File
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Image

/** Shipping screens at a compact phone width; public, synthetic fixtures only. */
@Composable
internal fun ShellAcceptanceFrame(language: AppLanguage, theme: ThemeMode, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
        ProvideAppLanguage(language) {
            ParlorTheme(themeMode = theme, reducedMotion = true) {
                Box(Modifier.size(320.dp, 740.dp).testTag("acceptance-viewport")) { content() }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.fullTextLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val layout = layouts.single()
    val measured = fetchSemanticsNode().size
    val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
    assertTrue(measured.width >= ceil(widest), "Text exceeds its measured width")
    assertTrue(measured.height >= ceil(layout.multiParagraph.height), "Text exceeds its measured height")
    assertFalse((0 until layout.lineCount).any(layout::isLineEllipsized))
    assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
    return layout
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.assertPhoneTouchTarget() {
    assertIsDisplayed()
    val bounds = getUnclippedBoundsInRoot()
    assertTrue(bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp,
        "Touch target is too small: $bounds")
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.captureShellAcceptance(name: String) {
    val directory = System.getProperty("parlor.ux.screenshotDir")?.takeIf { it.isNotBlank() }
        ?: System.getenv("PARLOR_UI_SCREENSHOT_DIR")?.takeIf { it.isNotBlank() }
        ?: "build/reports/ux"
    val output = File(directory, "$name.png")
    check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
    Image.makeFromBitmap(onNodeWithTag("acceptance-viewport").captureToImage().asSkiaBitmap()).use { image ->
        checkNotNull(image.encodeToData()).use { data -> output.writeBytes(data.bytes) }
    }
}
