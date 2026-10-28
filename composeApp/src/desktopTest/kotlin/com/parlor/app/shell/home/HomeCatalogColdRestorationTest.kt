package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.LastLightGameShellBinding
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.core.ids.SessionId
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json

/** Recreate the root saveable composition, not just a disposed navigation entry. */
@OptIn(ExperimentalTestApi::class)
class HomeCatalogColdRestorationTest {
    @Test
    fun root_recreation_while_recovery_loads_restores_the_same_catalog_anchor() {
        verifyRootRestoration(AppLanguage.English, 1f)
    }

    @Test
    fun arabic_large_text_root_recreation_restores_the_same_catalog_anchor() {
        verifyRootRestoration(AppLanguage.Arabic, 2f)
    }

    @Test
    fun removed_recovery_rows_do_not_shift_the_recreated_catalog() {
        verifyRootRestoration(AppLanguage.English, 1f, initialSaves = 3, restoredSaves = 0)
    }

    @Test
    fun added_recovery_rows_do_not_shift_the_recreated_arabic_catalog() {
        verifyRootRestoration(AppLanguage.Arabic, 2f, initialSaves = 0, restoredSaves = 3)
    }

    @Test
    fun a_second_platform_save_while_loading_keeps_the_original_catalog_intent() {
        verifyRootRestoration(AppLanguage.English, 1f, saveAgainDuringLoading = true)
    }

    @Test
    fun a_user_scroll_while_loading_takes_precedence_over_the_saved_catalog_position() {
        verifyRootRestoration(AppLanguage.English, 1f, scrollDuringLoading = true)
    }

    private fun verifyRootRestoration(
        language: AppLanguage,
        fontScale: Float,
        initialSaves: Int = 0,
        restoredSaves: Int = initialSaves,
        saveAgainDuringLoading: Boolean = false,
        scrollDuringLoading: Boolean = false,
    ) = runComposeUiTest {
        val visible = mutableStateOf(true)
        val loading = mutableStateOf(false)
        val registry = mutableStateOf(SaveableStateRegistry(null) { true })
        var currentState: HomeCatalogScrollState? = null
        val games = listOf(
            WhodunitGameShellBinding(WhodunitDefinition(Json.Default)),
            MafiaGameShellBinding(MafiaDefinition(Json.Default)),
            LastLightGameShellBinding(LastLightDefinition()),
            DominoGameShellBinding(DominoDefinition()),
            GhamzaGameShellBinding(GhamzaDefinition()),
            WordImpostorGameShellBinding(WordImpostorDefinition()),
        )
        fun recoveryRows(count: Int) = List(count) {
            LocalRecoveryEntry(SessionId("synthetic-root-save-$it"), games.first().definition.id)
        }
        val recovery = mutableStateOf(recoveryRows(initialSaves))
        setContent {
            if (visible.value) {
                CompositionLocalProvider(
                    LocalSaveableStateRegistry provides registry.value,
                    LocalDensity provides Density(1f, fontScale),
                ) {
                    ProvideAppLanguage(language) {
                        ParlorTheme(reducedMotion = true) {
                            val scrollState = rememberHomeCatalogScrollState()
                            currentState = scrollState
                            Box(Modifier.size(402.dp, 740.dp)) {
                                HomeScreen(
                                    games = games,
                                    catalogScrollState = scrollState,
                                    unfinishedSessions = if (loading.value) emptyList() else recovery.value,
                                    recoveryLoading = loading.value,
                                    onGameSelected = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        val prefix = if (language == AppLanguage.Arabic) "افتح غمزة" else "Open Ghamza"
        onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(prefix, substring = true))
        val card = onNodeWithContentDescription(prefix, substring = true)
        card.assertIsDisplayed()
        val before = card.getUnclippedBoundsInRoot()
        val originalAnchor = runOnIdle { assertNotNull(currentState).anchorForSave() }
        val saved = runOnIdle { registry.value.performSave() }
        runOnIdle { visible.value = false }
        card.assertDoesNotExist()
        runOnIdle {
            registry.value = SaveableStateRegistry(saved) { true }
            recovery.value = recoveryRows(restoredSaves)
            loading.value = true
            visible.value = true
        }
        waitForIdle()
        if (saveAgainDuringLoading) {
            val savedAgain = runOnIdle { registry.value.performSave() }
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle {
                registry.value = SaveableStateRegistry(savedAgain) { true }
                visible.value = true
            }
            waitForIdle()
        }
        if (scrollDuringLoading) {
            onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(durationMillis = 600) }
            waitForIdle()
            val userAnchor = runOnIdle { assertNotNull(currentState).anchorForSave() }
            assertNotEquals(originalAnchor, userAnchor, "The scroll must replace the pending platform anchor")
            runOnIdle { loading.value = false }
            waitForIdle()
            assertEquals(userAnchor, runOnIdle { assertNotNull(currentState).anchorForSave() })
            return@runComposeUiTest
        }
        runOnIdle { loading.value = false }
        waitForIdle()
        val after = card.getUnclippedBoundsInRoot()
        println("Public root restoration: language=${language.tag}, before=$before, after=$after")
        assertEquals(before, after, "A recreated root must not reinterpret the saved catalog index")
        card.assertIsDisplayed()
    }
}
