package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CatalogArtworkRenderingTest {
    @Test
    fun all_six_motifs_render_distinctly_in_both_themes_and_layout_directions() {
        for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            val english = renderAll(theme, AppLanguage.English)
            val arabic = renderAll(theme, AppLanguage.Arabic)
            for (index in english.indices) {
                assertTrue(english[index].contentEquals(arabic[index]), "Decorative artwork must not mirror")
                for (other in 0 until index) {
                    assertFalse(english[index].contentEquals(english[other]), "Different motifs must paint different pixels")
                }
            }
        }
    }

    private fun renderAll(theme: ThemeMode, language: AppLanguage): List<IntArray> =
        ParlorArtwork.entries.map { artwork ->
            var pixels = IntArray(0)
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        ProvideAppLanguage(language) {
                            ParlorTheme(themeMode = theme, reducedMotion = true) {
                                Box(Modifier.size(64.dp).background(ParlorTheme.colors.surfaceHero).testTag("artwork-capture")) {
                                    CatalogArtworkBadge(artwork)
                                }
                            }
                        }
                    }
                }
                val image = onNodeWithTag("artwork-capture").captureToImage()
                pixels = IntArray(image.width * image.height)
                image.readPixels(pixels)
                val output = File("build/reports/ux/catalog-artwork", "${theme.tag}-${language.tag}-${artwork.name}.png")
                check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
                Image.makeFromBitmap(image.asSkiaBitmap()).use { native ->
                    checkNotNull(native.encodeToData()).use { output.writeBytes(it.bytes) }
                }
            }
            pixels
        }
}
