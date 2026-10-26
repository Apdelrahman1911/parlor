package com.parlor.app.shell.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.storage.settings.InMemorySettingsStore
import com.parlor.storage.settings.SettingsStore
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenRenderingTest {
    @Test
    fun enlarged_settings_choices_remain_selectable_and_persist_their_values() {
        val settings = InMemorySettingsStore()
        startKoin { modules(module { single<SettingsStore> { settings } }) }
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f, 2f),
                        LocalParlorToastState provides ParlorToastState(),
                    ) {
                        ProvideAppLanguage(AppLanguage.English) {
                            ParlorTheme(reducedMotion = true) {
                                Box(Modifier.size(320.dp, 740.dp)) {
                                    SettingsScreen(mutationScope = rememberCoroutineScope())
                                }
                            }
                        }
                    }
                }
                onNodeWithText("English").performScrollTo().performClick()
                waitUntil { settings.languageOverride.value == "en" }
                onNodeWithText("English").assertIsSelected()
                onNodeWithText("Light").performScrollTo().performClick()
                waitUntil { settings.themeMode.value == "light" }
                onNodeWithText("Light").assertIsSelected()
                onNodeWithText("Reduce motion").performScrollTo().performClick()
                waitUntil { settings.reducedMotion.value }
                onNodeWithText("Reduce motion").assertIsOn()
                runOnIdle { assertEquals("en", settings.languageOverride.value) }
            }
        } finally {
            stopKoin()
        }
    }
}
