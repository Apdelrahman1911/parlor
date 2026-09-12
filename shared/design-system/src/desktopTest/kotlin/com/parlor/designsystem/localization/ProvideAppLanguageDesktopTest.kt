package com.parlor.designsystem.localization

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import java.util.Locale
import org.jetbrains.compose.resources.stringResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.session_exit_affordance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ProvideAppLanguageDesktopTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun locale_switches_resources_without_replacing_remembered_content_state() {
        val processLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        val selectedLanguage = mutableStateOf<AppLanguage?>(AppLanguage.English)
        val phases = mutableListOf<RenderedPhase>()
        val textWhileLoading = mutableListOf<String>()
        var rendered = ""
        var observedLocale = ""
        var retainedState: Any? = null
        var disposalCount = 0

        try {
            runSkikoComposeUiTest(size = Size(320f, 640f), testTimeout = 60.seconds) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        ProvideAppLanguage(
                            language = selectedLanguage.value,
                            loading = {
                                SideEffect {
                                    phases += RenderedPhase.Loading
                                    textWhileLoading += rendered
                                }
                            },
                        ) {
                            val state = remember { Any() }
                            DisposableEffect(Unit) {
                                onDispose { disposalCount++ }
                            }
                            val text = stringResource(Res.string.session_exit_affordance)
                            SideEffect {
                                phases += RenderedPhase.Content
                                rendered = text
                                observedLocale = Locale.getDefault().toLanguageTag()
                                retainedState = state
                            }
                        }
                    }
                }
                assertEquals(RenderedPhase.Loading, phases.first())
                assertEquals(RenderedPhase.Content, phases.last())
                assertTrue(textWhileLoading.isNotEmpty())
                assertTrue(textWhileLoading.all { it.isEmpty() })
                assertEquals("Leave", rendered)
                val initialState = checkNotNull(retainedState)
                val initialLoadingCount = textWhileLoading.size

                // Public idle synchronization drains both the external global
                // snapshot notifier and provider-internal locale effect writes.
                runOnIdle { selectedLanguage.value = AppLanguage.Arabic }
                waitForIdle()
                assertEquals("ar", observedLocale)
                assertEquals("مغادرة", rendered)
                assertSame(initialState, retainedState)
                assertEquals(0, disposalCount)

                runOnIdle { selectedLanguage.value = null }
                waitForIdle()
                assertEquals("en-US", observedLocale)
                assertEquals("Leave", rendered)
                assertSame(initialState, retainedState)
                assertEquals(0, disposalCount)
                assertEquals(initialLoadingCount, textWhileLoading.size)
            }
            assertEquals(1, disposalCount)
            assertEquals(Locale.US, Locale.getDefault())
        } finally {
            Locale.setDefault(processLocale)
        }
    }

    private enum class RenderedPhase {
        Loading,
        Content,
    }
}
