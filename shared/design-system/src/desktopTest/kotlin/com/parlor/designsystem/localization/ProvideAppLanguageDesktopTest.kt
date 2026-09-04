package com.parlor.designsystem.localization

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlin.test.assertSame

class ProvideAppLanguageDesktopTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun locale_switches_resources_without_replacing_remembered_content_state() = runTest {
        val processLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        val selectedLanguage = mutableStateOf<AppLanguage?>(AppLanguage.English)
        var renderedPhase = RenderedPhase.None
        var rendered = ""
        var observedLocale = ""
        var retainedState: Any? = null
        var disposalCount = 0
        val compositionContext = coroutineContext + ImmediateFrameClock
        val recomposer = Recomposer(compositionContext)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerJob = launch(ImmediateFrameClock) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ProvideAppLanguage(
                        language = selectedLanguage.value,
                        loading = {
                            SideEffect { renderedPhase = RenderedPhase.Loading }
                        },
                    ) {
                        val state = remember { Any() }
                        DisposableEffect(Unit) {
                            onDispose { disposalCount++ }
                        }
                        val text = stringResource(Res.string.session_exit_affordance)
                        SideEffect {
                            renderedPhase = RenderedPhase.Content
                            rendered = text
                            observedLocale = Locale.getDefault().toLanguageTag()
                            retainedState = state
                        }
                    }
                }
            }
            assertEquals(RenderedPhase.Loading, renderedPhase)
            assertEquals("", rendered)

            runCurrent()
            assertEquals(RenderedPhase.Content, renderedPhase)
            assertEquals("Leave", rendered)
            val initialState = checkNotNull(retainedState)

            selectedLanguage.value = AppLanguage.Arabic
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals("ar", observedLocale)
            assertEquals("مغادرة", rendered)
            assertSame(initialState, retainedState)
            assertEquals(0, disposalCount)

            selectedLanguage.value = null
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals("Leave", rendered)
            assertSame(initialState, retainedState)
            assertEquals(0, disposalCount)
        } finally {
            composition.dispose()
            recomposer.close()
            recomposerJob.join()
            assertEquals(1, disposalCount)
            assertEquals(Locale.US, Locale.getDefault())
            Locale.setDefault(processLocale)
        }
    }

    private enum class RenderedPhase {
        None,
        Loading,
        Content,
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
