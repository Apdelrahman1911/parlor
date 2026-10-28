package com.parlor.app.shell.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.di.contentModule
import com.parlor.app.App
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.di.dominoModule
import com.parlor.games.ghamza.di.ghamzaModule
import com.parlor.games.lastlight.di.lastLightModule
import com.parlor.games.mafia.di.mafiaModule
import com.parlor.games.whodunit.di.whodunitModule
import com.parlor.games.wordimpostor.di.wordImpostorModule
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.ResumableSessionInfo
import com.parlor.networking.transport.ResumableSessionSelection
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.storage.settings.InMemorySettingsStore
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import com.parlor.session.multidevice.ProcessMultiplayerState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Real registered Resume routes; the transport tripwire never creates a room or private state. */
@OptIn(ExperimentalTestApi::class)
class ResumeGameBindingTest {
    @Test fun whodunit_resume_binds_its_game_and_version() = verifyResume("whodunit")
    @Test fun mafia_resume_binds_its_game_and_version() = verifyResume("mafia")
    @Test fun last_light_resume_binds_its_game_and_version() = verifyResume("last-light")
    @Test fun dominoes_resume_binds_its_game_and_version() = verifyResume("egyptian-dominoes")
    @Test fun ghamza_resume_binds_its_game_and_version() = verifyResume("ghamza")
    @Test fun word_impostor_resume_binds_its_game_and_version() = verifyResume("word-impostor")

    private fun verifyResume(id: String) {
        for (language in AppLanguage.entries) {
            verifyResume(GameId(id), language, fromHome = false)
            verifyResume(GameId(id), language, fromHome = true)
        }
    }

    private fun verifyResume(gameId: GameId, language: AppLanguage, fromHome: Boolean) {
        val job = SupervisorJob()
        // The test owns and injects its executor; no platform/global dispatcher or shipping thread is added.
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val owner = ProcessMultiplayerSessionOwner(CoroutineScope(job + dispatcher))
        val transport = ResumeBindingTripwire()
        val selection = object : ResumableSessionSelection {}
        val settings = InMemorySettingsStore(initialReducedMotion = true, initialLanguageOverride = language.tag)
        var expected: HostedGameProtocol? = null
        try {
            runComposeUiTest {
                setContent {
                    KoinApplication(application = {
                        modules(
                            whodunitModule, mafiaModule, lastLightModule, dominoModule,
                            ghamzaModule, wordImpostorModule, contentModule,
                            module {
                                single { Json { encodeDefaults = true } }
                                single<RoomTransport> { transport }
                                single { owner }
                                single<SnapshotStore> { InMemorySnapshotStore() }
                                single { AppLifecycleCoordinator(transport).apply { notifyActive() } }
                            },
                        )
                    }) {
                        val registry: GameShellRegistry = koinInject()
                        val binding = assertNotNull(registry.all.singleOrNull { it.definition.id == gameId })
                        val contract = assertNotNull(binding.multiplayerContract)
                        expected = HostedGameProtocol(contract.gameId, contract.gameVersion)
                        transport.summary = ResumableSessionInfo(
                            gameId, contract.gameVersion, "Resume Test", Long.MAX_VALUE, selection,
                        )
                        CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                            ProvideAppLanguage(language) {
                                ParlorTheme(reducedMotion = true) {
                                    Box(Modifier.size(320.dp, 740.dp)) {
                                        if (fromHome) App(settings)
                                        else binding.Content(GameShellLaunch.ResumeMultiplayer(gameId, "Resume Test", selection), {})
                                    }
                                }
                            }
                        }
                    }
                }
                if (fromHome) {
                    val description = if (language == AppLanguage.Arabic) "العودة إلى اللعبة الجماعية السابقة."
                        else "Rejoin the previous multiplayer game."
                    waitUntil(timeoutMillis = 5_000) { transport.metadataRequests.get() > 0 }
                    waitForIdle()
                    onNode(hasScrollToIndexAction()).performScrollToIndex(3)
                    onNodeWithContentDescription(description).performClick()
                }
                waitUntil(timeoutMillis = 5_000) { owner.state.value is ProcessMultiplayerState.Failed }
                runOnIdle {
                    assertEquals(listOf(assertNotNull(expected)), transport.boundRequests.toList())
                    assertEquals(listOf(selection), transport.membershipRequests.toList())
                    assertEquals(0, transport.gameOnlyRequests.get(), "Home Resume must not lose its selected membership")
                    assertEquals(0, transport.unboundRequests.get(), "Shipping Resume must never lose its selected binding")
                    assertEquals(0, transport.otherOperations.get())
                    val failed = assertIs<ProcessMultiplayerState.Failed>(owner.state.value)
                    assertEquals(gameId, failed.route.gameId)
                    assertEquals(NetError.IncompatibleProtocol, failed.error)
                }
            }
        } finally {
            try {
                runBlocking { withTimeout(5_000) { job.cancelAndJoin() } }
            } finally {
                dispatcher.close()
            }
        }
    }
}

private class ResumeBindingTripwire : RoomTransport {
    override val capability = TransportCapability(false, 1, 1024)
    var summary: ResumableSessionInfo? = null
    val metadataRequests = AtomicInteger()
    override suspend fun resumableSession(): Result<ResumableSessionInfo?, NetError> {
        metadataRequests.incrementAndGet()
        return Result.Success(summary)
    }
    val boundRequests = CopyOnWriteArrayList<HostedGameProtocol>()
    val membershipRequests = CopyOnWriteArrayList<ResumableSessionSelection>()
    val gameOnlyRequests = AtomicInteger()
    val unboundRequests = AtomicInteger()
    val otherOperations = AtomicInteger()

    override suspend fun resumeLastSession(expectedGameProtocol: HostedGameProtocol): Result<LocalRoom, NetError> {
        gameOnlyRequests.incrementAndGet()
        return Result.Failure(NetError.IncompatibleProtocol)
    }

    override suspend fun resumeLastSession(
        expectedGameProtocol: HostedGameProtocol,
        selection: ResumableSessionSelection,
    ): Result<LocalRoom, NetError> {
        boundRequests += expectedGameProtocol
        membershipRequests += selection
        return Result.Failure(NetError.IncompatibleProtocol)
    }

    override suspend fun resumeLastSession(): Result<LocalRoom, NetError> {
        unboundRequests.incrementAndGet()
        return Result.Failure(NetError.IncompatibleProtocol)
    }

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = forbiddenOperation()
    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> = forbiddenOperation()

    private fun forbiddenOperation(): Result<LocalRoom, NetError> {
        otherOperations.incrementAndGet()
        return Result.Failure(NetError.NotConnected)
    }
}
