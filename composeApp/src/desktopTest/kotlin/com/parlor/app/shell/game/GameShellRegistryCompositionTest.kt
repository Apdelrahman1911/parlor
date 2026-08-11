package com.parlor.app.shell.game

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Desktop-runtime proof that shell resolution executes a third game's content. */
class GameShellRegistryCompositionTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun registered_fixture_content_is_composed_by_the_resolved_binding() = runTest {
        val definition = RoundRobinAnnounceGame()
        var renderedLaunch: GameShellLaunch? = null
        var renderedBackRequest: GameShellBackRequest? = null
        val binding = RenderingFixtureBinding(definition) { launch, backRequest ->
            renderedLaunch = launch
            renderedBackRequest = backRequest
        }
        val router = GameShellRouter(DefaultGameShellRegistry(listOf(binding)))
        val launch = assertNotNull(router.newGame(definition.id))
        val compositionContext = coroutineContext + ImmediateFrameClock
        val recomposer = Recomposer(compositionContext)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerJob = launch(ImmediateFrameClock) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                assertNotNull(router.bindingFor(launch)).Content(
                    launch = launch,
                    onExit = {},
                    backRequest = GameShellBackRequest(7L),
                )
            }
            runCurrent()
            assertEquals(launch, renderedLaunch)
            assertEquals(GameShellBackRequest(7L), renderedBackRequest)
        } finally {
            composition.dispose()
            recomposer.close()
            recomposerJob.join()
        }
    }
}

private class RenderingFixtureBinding(
    override val definition: GameDefinition<*, *, *>,
    private val onRendered: (GameShellLaunch, GameShellBackRequest) -> Unit,
) : GameShellBinding {
    override val capabilities = GameShellCapabilities(
        setOf(GameEntryMode.PassAndPlay, GameEntryMode.Host, GameEntryMode.Join),
    )
    override val multiplayerContract = GameShellMultiplayerContract(
        gameId = definition.id,
        gameVersion = 1,
        supportedPlayerCounts = definition.supportedPlayerCounts,
    )

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = definition.id.raw,
        subtitle = "fixture",
        tagline = "fixture",
        openLabel = "open",
        openContentDescription = "open fixture",
    )

    @Composable
    override fun Content(
        launch: GameShellLaunch,
        onExit: () -> Unit,
        backRequest: GameShellBackRequest,
        modifier: Modifier,
    ) {
        SideEffect { onRendered(launch, backRequest) }
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
