package com.parlor.designsystem.localization

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.stringResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.session_exit_affordance
import kotlin.test.Test
import kotlin.test.assertEquals

class ProvideAppLanguageDesktopTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun explicit_language_switches_resources_and_disposal_restores_platform_locale() = runTest {
        val processLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        val selectedLanguage = mutableStateOf<AppLanguage?>(AppLanguage.English)
        var rendered = ""
        var observedLocale = ""
        val compositionContext = coroutineContext + ImmediateFrameClock
        val recomposer = Recomposer(compositionContext)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerJob = launch(ImmediateFrameClock) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ProvideAppLanguage(selectedLanguage.value) {
                        val text = stringResource(Res.string.session_exit_affordance)
                        SideEffect {
                            rendered = text
                            observedLocale = Locale.getDefault().toLanguageTag()
                        }
                    }
                }
            }
            runCurrent()
            assertEquals("Leave", rendered)

            selectedLanguage.value = AppLanguage.Arabic
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals("ar", observedLocale)
            assertEquals("مغادرة", rendered)

            selectedLanguage.value = null
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals("Leave", rendered)
        } finally {
            composition.dispose()
            recomposer.close()
            recomposerJob.join()
            assertEquals(Locale.US, Locale.getDefault())
            Locale.setDefault(processLocale)
        }
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
