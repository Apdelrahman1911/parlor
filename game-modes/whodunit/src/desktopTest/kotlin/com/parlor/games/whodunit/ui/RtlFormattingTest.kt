package com.parlor.games.whodunit.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_clue_bullet_format
import com.parlor.games.whodunit.resources.timer_elapsed_format
import com.parlor.games.whodunit.resources.timer_total_format
import com.parlor.games.whodunit.resources.whodunit_list_separator
import java.io.File
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RtlFormattingTest {

    @Test
    fun affected_rendering_uses_resources_instead_of_raw_punctuation() {
        val root = projectRoot()
        val expectations = mapOf(
            "ui/screens/round/RoundScreens.kt" to listOf("round_clue_bullet_format"),
            "ui/screens/setup/PublicIntroScreen.kt" to listOf("round_clue_bullet_format"),
            "ui/screens/vote/VoteScreens.kt" to listOf("whodunit_list_separator"),
            "ui/components/DossierCard.kt" to listOf("whodunit_list_separator"),
            "ui/components/TimerRibbon.kt" to listOf(
                "timer_elapsed_format",
                "timer_total_format",
            ),
        )
        val violations = mutableListOf<String>()

        expectations.forEach { (relativePath, requiredResources) ->
            val source = root.resolve("game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit")
                .resolve(relativePath)
                .readText()
            source.lineSequence().forEachIndexed { index, line ->
                if (RAW_BULLET_OR_SEPARATOR.containsMatchIn(line) ||
                    RAW_ELAPSED_TIMER in line ||
                    RAW_TOTAL_TIMER in line
                ) {
                    violations += "$relativePath:${index + 1}: ${line.trim()}"
                }
            }
            requiredResources.forEach { resource ->
                assertTrue(
                    "Res.string.$resource" in source,
                    "$relativePath must render through $resource",
                )
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Affected UI must not hard-code directional punctuation:\n${violations.joinToString("\n")}",
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class, InternalComposeUiApi::class)
    @Test
    fun localized_resources_render_english_and_arabic_output_with_matching_direction() = runTest {
        val processLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        val selectedLanguage = mutableStateOf(AppLanguage.English)
        var rendered: RenderedFormatting? = null
        val compositionContext = coroutineContext + ImmediateFrameClock
        val recomposer = Recomposer(compositionContext)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerJob = launch(ImmediateFrameClock) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalLayoutDirection provides selectedLanguage.value.layoutDirection,
                    LocalSystemTheme provides SystemTheme.Light,
                ) {
                    val separator = stringResource(Res.string.whodunit_list_separator)
                    val output = RenderedFormatting(
                        direction = LocalLayoutDirection.current,
                        bullet = stringResource(Res.string.round_clue_bullet_format, "Evidence"),
                        names = listOf("Layla", "Omar").joinToString(separator),
                        elapsed = stringResource(Res.string.timer_elapsed_format, "03", "07"),
                        total = stringResource(Res.string.timer_total_format, "05", "00"),
                    )
                    SideEffect { rendered = output }
                }
            }
            runCurrent()
            assertEquals(
                RenderedFormatting(
                    direction = LayoutDirection.Ltr,
                    bullet = "•  Evidence",
                    names = "Layla · Omar",
                    elapsed = "03:07",
                    total = "/ 05:00",
                ),
                rendered,
            )

            Locale.setDefault(Locale.forLanguageTag(AppLanguage.Arabic.tag))
            selectedLanguage.value = AppLanguage.Arabic
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(
                RenderedFormatting(
                    direction = LayoutDirection.Rtl,
                    bullet = "•  Evidence",
                    names = "Layla، Omar",
                    elapsed = "03:07",
                    total = "/ 05:00",
                ),
                rendered,
            )
        } finally {
            composition.dispose()
            recomposer.close()
            recomposerJob.join()
            Locale.setDefault(processLocale)
        }
    }

    private fun projectRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate project root")
    }

    private data class RenderedFormatting(
        val direction: LayoutDirection,
        val bullet: String,
        val names: String,
        val elapsed: String,
        val total: String,
    )

    private companion object {
        val RAW_BULLET_OR_SEPARATOR: Regex = Regex("""(?:text|separator)\s*=\s*"[^"]*[·•،][^"]*"""")
        const val RAW_ELAPSED_TIMER: String = "text = \"\$mm:\$ss\""
        const val RAW_TOTAL_TIMER: String = "text = \"/ \$totalMm:\$totalSs\""
    }
}

private class UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private object ImmediateFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(0L)
}
