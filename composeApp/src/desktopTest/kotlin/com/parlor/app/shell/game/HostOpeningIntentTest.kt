package com.parlor.app.shell.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.App
import com.parlor.app.StartupMultiplayerRecoveryGate
import com.parlor.app.di.contentModule
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.dominoes.di.dominoModule
import com.parlor.games.ghamza.di.ghamzaModule
import com.parlor.games.lastlight.di.lastLightModule
import com.parlor.games.mafia.di.mafiaModule
import com.parlor.games.whodunit.di.whodunitModule
import com.parlor.games.wordimpostor.di.wordImpostorModule
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import com.parlor.storage.settings.InMemorySettingsStore
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Actual App restores a retained failed host; this fixture never opens LAN or private gameplay. */
@OptIn(ExperimentalTestApi::class)
class HostOpeningIntentTest {
    @Test fun whodunit_failed_host_is_not_retried_by_root_restoration() = verifyHost("whodunit")
    @Test fun mafia_failed_host_is_not_retried_by_root_restoration() = verifyHost("mafia")
    @Test fun last_light_failed_host_is_not_retried_by_root_restoration() = verifyHost("last-light")
    @Test fun dominoes_failed_host_is_not_retried_by_root_restoration() = verifyHost("egyptian-dominoes")
    @Test fun ghamza_failed_host_is_not_retried_by_root_restoration() = verifyHost("ghamza")
    @Test fun word_impostor_failed_host_is_not_retried_by_root_restoration() = verifyHost("word-impostor")

    private fun verifyHost(id: String) {
        for (language in AppLanguage.entries) verifyHost(id, language)
    }

    private fun verifyHost(id: String, language: AppLanguage) {
        val job = SupervisorJob()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val owner = ProcessMultiplayerSessionOwner(CoroutineScope(job + dispatcher))
        val transport = FailedHostTripwire()
        val route = MultiplayerSessionRoute.host(GameId(id), "Host Test",
            contentId = "last-dinner".takeIf { id == "whodunit" },
            modeId = "classic-vote".takeIf { id == "whodunit" })
        val settings = InMemorySettingsStore(initialReducedMotion = true, initialLanguageOverride = language.tag)
        val epoch = mutableStateOf(0)
        val retry = when {
            id in setOf("whodunit", "mafia", "last-light") ->
                if (language == AppLanguage.Arabic) "أعد تنفيذ عملية الشبكة المحلية." else "Retry the local-network operation."
            language == AppLanguage.Arabic -> "جرّب تاني"
            else -> "Try again"
        }
        try {
            runBlocking {
                assertIs<Result.Failure<NetError>>(owner.acquire(route, hostSeed = 7L) {
                    transport.host(HostConfig("Host Test"))
                })
            }
            runComposeUiTest {
                setContent {
                    KoinApplication(application = {
                        modules(whodunitModule, mafiaModule, lastLightModule, dominoModule,
                            ghamzaModule, wordImpostorModule, contentModule, module {
                                single { Json { encodeDefaults = true } }
                                single<RoomTransport> { transport }
                                single { owner }
                                single { StartupMultiplayerRecoveryGate() }
                                single<SnapshotStore> { InMemorySnapshotStore() }
                                single { AppLifecycleCoordinator(transport).apply { notifyActive() } }
                            })
                    }) {
                        CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                            Box(Modifier.size(320.dp, 740.dp)) { key(epoch.value) { App(settings) } }
                        }
                    }
                }
                onNodeWithContentDescription(retry).assertExists()
                runOnIdle { assertEquals(1, transport.hostCalls.get(), "Restoring Failed is not explicit Retry") }
                runOnIdle { epoch.value++ }
                onNodeWithContentDescription(retry).assertExists()
                runOnIdle { assertEquals(1, transport.hostCalls.get(), "Root recreation must not open a new room") }
                if (id !in setOf("whodunit", "mafia", "last-light")) {
                    onNodeWithContentDescription(retry).performScrollTo()
                }
                onNodeWithContentDescription(retry).assertIsDisplayed().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    transport.hostCalls.get() == 2 && owner.state.value is ProcessMultiplayerState.Failed
                }
                runOnIdle { assertIs<ProcessMultiplayerState.Failed>(owner.state.value) }
                runOnIdle { epoch.value++ }
                onNodeWithContentDescription(retry).assertExists()
                runOnIdle { assertEquals(2, transport.hostCalls.get(), "A failed explicit Retry must stay failed") }
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

private class FailedHostTripwire : RoomTransport {
    override val capability = TransportCapability(false, 1, 1024)
    val hostCalls = AtomicInteger()
    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
        hostCalls.incrementAndGet()
        return Result.Failure(NetError.NotConnected)
    }
    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> =
        error("The host restoration fixture must never join")
}
