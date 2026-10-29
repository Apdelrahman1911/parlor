package com.parlor.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.app.lifecycle.AppVisibility
import com.parlor.app.shell.game.DefaultGameShellRegistry
import com.parlor.app.shell.game.GameCatalogPresentation
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellBackRequest
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.app.shell.game.GameShellCapabilities
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GameShellMultiplayerContract
import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.core.result.Result
import com.parlor.core.result.DataError
import com.parlor.core.ids.SessionId
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.networking.transport.ResumableSessionSelection
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.storage.settings.InMemorySettingsStore
import com.parlor.storage.settings.SettingsStore
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Actual App navigation with an explicitly non-shipping binding; no LAN/authentication claim. */
@OptIn(ExperimentalTestApi::class)
class StartupMultiplayerRecoveryJourneyTest {
    @Test
    fun background_and_inactive_startups_wait_for_active_before_lookup_and_routing() {
        for (initial in listOf(AppVisibility.Background, AppVisibility.Inactive)) {
            StartupJourneyFixture(initial).use { fixture ->
                runComposeUiTest {
                    installApp(fixture)
                    waitForIdle()
                    assertEquals(0, fixture.transport.metadataRequests.get(), "Covered startup must not probe protected storage")
                    assertEquals(0, fixture.snapshots.inventoryRequests.get(), "Covered startup must not inspect local saves")
                    assertTrue(fixture.rendered.isEmpty())
                    runOnIdle { fixture.lifecycle.notifyActive() }
                    assertSelectedResume(fixture)
                }
            }
        }
    }

    @Test
    fun delayed_lookup_cannot_override_settings_then_games_but_manual_resume_still_works() {
        StartupJourneyFixture(delayMetadata = true).use { fixture ->
            runComposeUiTest {
                installApp(fixture)
                waitUntil(timeoutMillis = 5_000) { fixture.transport.metadataRequests.get() == 1 }
                onNodeWithContentDescription("Open settings.").performClick()
                onNodeWithContentDescription("Open the game library.").performClick()
                runOnIdle { fixture.transport.metadataBarrier.complete(Unit) }
                waitUntil(timeoutMillis = 5_000) { fixture.transport.completedMetadataRequests.get() > 0 }
                waitForIdle()
                assertTrue(fixture.rendered.isEmpty(), "Returning to Games must not rearm delayed startup")
                manualResume()
                assertSelectedResume(fixture)
            }
        }
    }

    @Test
    fun failed_or_absent_first_lookup_does_not_auto_retry_after_root_recreation() {
        for (initial in listOf(Result.Failure(NetError.NotConnected), Result.Success(null))) {
            StartupJourneyFixture().use { fixture ->
                fixture.transport.result.set(initial)
                runComposeUiTest {
                    installApp(fixture)
                    waitUntil(timeoutMillis = 5_000) { fixture.transport.completedMetadataRequests.get() == 1 }
                    waitForIdle()
                    runOnIdle {
                        fixture.transport.result.set(Result.Success(fixture.summary))
                        fixture.rootEpoch.value++
                    }
                    waitUntil(timeoutMillis = 5_000) { fixture.transport.completedMetadataRequests.get() == 2 }
                    waitForIdle()
                    assertTrue(fixture.rendered.isEmpty(), "A new root must not retry a completed startup lookup")
                    manualResume()
                    assertSelectedResume(fixture)
                }
            }
        }
    }

    @Test
    fun routed_exit_and_root_recreation_do_not_repeat_the_automatic_attempt() {
        StartupJourneyFixture().use { fixture ->
            runComposeUiTest {
                installApp(fixture)
                assertSelectedResume(fixture)
                onNodeWithText("Exit fixture").performClick()
                waitUntil(timeoutMillis = 5_000) { fixture.transport.completedMetadataRequests.get() >= 2 }
                runOnIdle { fixture.rootEpoch.value++ }
                waitUntil(timeoutMillis = 5_000) { fixture.transport.completedMetadataRequests.get() >= 3 }
                waitForIdle()
                assertEquals(1, fixture.rendered.size, "Root recreation cannot auto-replay a routed attempt")
                onNodeWithContentDescription("Open the game library.").assertExists()
            }
        }
    }

    @Test
    fun opening_a_new_game_while_metadata_is_delayed_prevents_later_startup_override() {
        StartupJourneyFixture(delayMetadata = true).use { fixture ->
            runComposeUiTest {
                installApp(fixture)
                waitUntil(timeoutMillis = 5_000) { fixture.transport.metadataRequests.get() == 1 }
                onNode(hasScrollToIndexAction()).performScrollToIndex(4)
                onNodeWithContentDescription("Open fixture game").performClick()
                waitUntil(timeoutMillis = 5_000) { fixture.rendered.size == 1 }
                assertIs<GameShellLaunch.New>(fixture.rendered.single())
                runOnIdle { fixture.transport.metadataBarrier.complete(Unit) }
                onNodeWithText("Exit fixture").performClick()
                waitUntil(timeoutMillis = 5_000) { fixture.transport.completedMetadataRequests.get() > 0 }
                waitForIdle()
                assertEquals(1, fixture.rendered.size, "Startup may not supersede an explicit game choice")
                onNodeWithContentDescription("Open the game library.").assertExists()
            }
        }
    }

    private fun ComposeUiTest.installApp(fixture: StartupJourneyFixture) {
        setContent {
            KoinApplication(application = {
                modules(module {
                    single<SettingsStore> { fixture.settings }
                    single<SnapshotStore> { fixture.snapshots }
                    single<RoomTransport> { fixture.transport }
                    single { fixture.owner }
                    single { fixture.lifecycle }
                    single { fixture.gate }
                    single<GameShellRegistry> { DefaultGameShellRegistry(listOf(fixture.binding)) }
                })
            }) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.size(320.dp, 740.dp)) {
                        key(fixture.rootEpoch.value) { App(fixture.settings) }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.manualResume() {
        onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        onNodeWithContentDescription("Rejoin the previous multiplayer game.").performClick()
    }

    private fun ComposeUiTest.assertSelectedResume(fixture: StartupJourneyFixture) {
        waitUntil(timeoutMillis = 5_000) { fixture.rendered.size == 1 }
        runOnIdle {
            val launch = assertIs<GameShellLaunch.ResumeMultiplayer>(fixture.rendered.single())
            assertSame(fixture.selection, launch.selection)
            assertEquals(fixture.binding.definition.id, launch.gameId)
            assertEquals(fixture.summary.displayName, launch.displayName)
            assertEquals(0, fixture.transport.lanOperations.get())
        }
    }
}

private class StartupJourneyFixture(
    visibility: AppVisibility = AppVisibility.Active,
    delayMetadata: Boolean = false,
) : AutoCloseable {
    val rendered = CopyOnWriteArrayList<GameShellLaunch>()
    val binding = StartupJourneyBinding(rendered)
    val selection = object : ResumableSessionSelection {}
    val summary = ResumableSessionInfo(binding.definition.id, 1, "Fixture Player", Long.MAX_VALUE, selection)
    val transport = StartupJourneyTransport(summary, delayMetadata)
    val snapshots = StartupJourneySnapshotStore()
    val settings = InMemorySettingsStore(initialLanguageOverride = "en", initialReducedMotion = true)
    val gate = StartupMultiplayerRecoveryGate()
    val lifecycle = AppLifecycleCoordinator(transport).apply {
        when (visibility) {
            AppVisibility.Active -> notifyActive()
            AppVisibility.Inactive -> notifyInactive()
            AppVisibility.Background -> notifyBackgrounded()
        }
    }
    val rootEpoch = mutableStateOf(0)
    private val job = SupervisorJob()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val owner = ProcessMultiplayerSessionOwner(CoroutineScope(job + dispatcher))

    override fun close() {
        try {
            runBlocking { withTimeout(5_000) { job.cancelAndJoin() } }
        } finally {
            dispatcher.close()
        }
    }
}

private class StartupJourneySnapshotStore(
    private val delegate: SnapshotStore = InMemorySnapshotStore(),
) : SnapshotStore by delegate {
    val inventoryRequests = AtomicInteger()

    override suspend fun listUnfinished(): Result<List<SessionId>, DataError> {
        inventoryRequests.incrementAndGet()
        return delegate.listUnfinished()
    }
}

private class StartupJourneyBinding(private val rendered: MutableList<GameShellLaunch>) : GameShellBinding {
    override val definition = RoundRobinAnnounceGame()
    override val capabilities = GameShellCapabilities(setOf(GameEntryMode.PassAndPlay, GameEntryMode.Join))
    override val multiplayerContract = GameShellMultiplayerContract(definition.id, 1, definition.supportedPlayerCounts)

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = "Fixture game", subtitle = "Fixture", tagline = "Fixture",
        openLabel = "Open", openContentDescription = "Open fixture game",
    )

    @Composable
    override fun Content(
        launch: GameShellLaunch,
        onExit: () -> Unit,
        backRequest: GameShellBackRequest,
        modifier: Modifier,
    ) {
        LaunchedEffect(launch) { rendered += launch }
        Button(onClick = onExit, modifier = modifier) { Text("Exit fixture") }
    }
}

private class StartupJourneyTransport(summary: ResumableSessionInfo, delayed: Boolean) : RoomTransport {
    override val capability = TransportCapability(false, 1, 1024)
    val metadataRequests = AtomicInteger()
    val completedMetadataRequests = AtomicInteger()
    val metadataBarrier = CompletableDeferred<Unit>().apply { if (!delayed) complete(Unit) }
    val result = AtomicReference<Result<ResumableSessionInfo?, NetError>>(Result.Success(summary))
    val lanOperations = AtomicInteger()

    override suspend fun resumableSession(): Result<ResumableSessionInfo?, NetError> {
        metadataRequests.incrementAndGet()
        metadataBarrier.await()
        return result.get().also { completedMetadataRequests.incrementAndGet() }
    }

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = forbiddenOperation()
    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> = forbiddenOperation()
    override suspend fun resumeLastSession(): Result<LocalRoom, NetError> = forbiddenOperation()

    private fun forbiddenOperation(): Result<LocalRoom, NetError> {
        lanOperations.incrementAndGet()
        return Result.Failure(NetError.NotConnected)
    }
}
