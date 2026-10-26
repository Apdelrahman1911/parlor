package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Image

@OptIn(ExperimentalTestApi::class)
class RoomCodeLayoutTest {
    @Test fun english_light() = verify(AppLanguage.English, ThemeMode.Light)
    @Test fun english_dark() = verify(AppLanguage.English, ThemeMode.Dark)
    @Test fun arabic_light() = verify(AppLanguage.Arabic, ThemeMode.Light)
    @Test fun arabic_dark() = verify(AppLanguage.Arabic, ThemeMode.Dark)

    private fun verify(language: AppLanguage, theme: ThemeMode) {
        for (fontScale in listOf(1f, 2f)) {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                        ProvideAppLanguage(language) {
                            ParlorTheme(themeMode = theme, reducedMotion = true) {
                                Box(Modifier.size(320.dp, 320.dp).testTag("code-viewport")) {
                                    Box(Modifier.padding(ParlorTheme.spacing.l)) {
                                        ParlorCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = ParlorTheme.spacing.l,
                                            hero = true,
                                        ) {
                                            // Same typography and available width as the three original host lobbies.
                                            Text(
                                                CODE,
                                                style = ParlorTheme.typography.displayHero,
                                                color = ParlorTheme.colors.accentEmber,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                System.getenv("PARLOR_UI_SCREENSHOT_DIR")?.let { directory ->
                    val output = File(directory, "room-code-${language.tag}-${theme.tag}-$fontScale.png")
                    check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
                    Image.makeFromBitmap(onNodeWithTag("code-viewport").captureToImage().asSkiaBitmap()).use { image ->
                        checkNotNull(image.encodeToData()).use { output.writeBytes(it.bytes) }
                    }
                }
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(CODE).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertEquals(CODE, layout.layoutInput.text.text)
                assertEquals(1, layout.lineCount, "Six-character code wrapped at $fontScale in ${language.tag}")
                assertFalse(layout.hasVisualOverflow)
                assertEquals(fontScale, layout.layoutInput.density.fontScale, "Keep the user's full font scaling")
                for (index in CODE.indices) {
                    val glyph = layout.getBoundingBox(index)
                    assertTrue(glyph.left >= 0 && glyph.right <= layout.size.width, "Code glyph $index clipped")
                    if (index > 0) assertTrue(glyph.left > layout.getBoundingBox(index - 1).left, "Keep code LTR")
                }
            }
        }
    }

    private companion object {
        const val CODE = "WWWWWW"
    }
}
