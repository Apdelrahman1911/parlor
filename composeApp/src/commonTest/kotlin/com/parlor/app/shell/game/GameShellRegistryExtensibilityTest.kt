package com.parlor.app.shell.game

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.parlor.app.resolveLocalResumeDestination
import com.parlor.app.shell.playmode.PlayModePickerAvailability
import com.parlor.app.shell.playmode.toPlayModePickerAvailability
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.engine.state.Player
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
import com.parlor.engine.testing.fakes.RrState
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.storage.snapshot.InMemorySnapshotStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.time.Instant

/** End-to-end, non-shipping proof of the app shell's third-game contract. */
class GameShellRegistryExtensibilityTest {
    private val fixtureDefinition = RoundRobinAnnounceGame()
    private val fixtureBinding = FixtureBinding(fixtureDefinition)
    private val registry = DefaultGameShellRegistry(
        listOf(
            FixtureBinding(ExistingDefinition()),
            fixtureBinding,
        ),
    )
    private val router = GameShellRouter(registry)

    @Test
    fun third_game_registers_in_catalog_and_exposes_shell_contract() {
        val entry = assertNotNull(
            registry.catalog.singleOrNull { it.gameId == fixtureDefinition.id },
        )

        assertSame(fixtureBinding, registry.byId(fixtureDefinition.id))
        assertEquals(fixtureDefinition.supportedPlayerCounts, entry.supportedPlayerCounts)
        assertEquals(
            setOf(GameEntryMode.PassAndPlay, GameEntryMode.Host, GameEntryMode.Join),
            entry.capabilities.entryModes,
        )
        assertEquals(fixtureDefinition.metadata, entry.metadata)
        assertEquals(fixtureDefinition.id, fixtureBinding.multiplayerContract?.gameId)
        assertEquals(1, fixtureBinding.multiplayerContract?.gameVersion)
        assertEquals(
            fixtureDefinition.supportedPlayerCounts,
            fixtureBinding.multiplayerContract?.supportedPlayerCounts,
        )
    }

    @Test
    fun third_game_starts_locally_and_resolves_navigation_without_shell_branch() {
        val launch = assertNotNull(router.newGame(fixtureDefinition.id))
        val binding = assertNotNull(router.bindingFor(launch))
        val state = fixtureDefinition.createInitialState(sessionConfig())

        assertEquals(fixtureDefinition.id, launch.gameId)
        assertSame(fixtureBinding, binding)
        assertIs<RrState>(state)
    }

    @Test
    fun third_game_snapshot_round_trips_and_resolves_local_resume() = runTest {
        val store = InMemorySnapshotStore()
        val sessionId = SessionId("fixture-local")
        val state = fixtureDefinition.createInitialState(sessionConfig(sessionId))
        val encoded = fixtureDefinition.snapshotCodec().encode(state)
        store.save(
            GameSnapshot(
                sessionId = sessionId,
                gameId = fixtureDefinition.id,
                engineVersion = SemVer(1, 0, 0),
                createdAt = Instant.fromEpochSeconds(1),
                phaseId = state.phase.id,
                payload = encoded,
            ),
        )

        val destination = assertIs<Result.Success<GameShellLaunch.ResumeLocal>>(
            resolveLocalResumeDestination(store, router, sessionId),
        ).data
        val restoredEnvelope = assertIs<Result.Success<GameSnapshot>>(store.load(sessionId)).data
        val restoredState = fixtureDefinition.snapshotCodec().decode(restoredEnvelope.payload)

        assertEquals(GameShellLaunch.ResumeLocal(fixtureDefinition.id, sessionId), destination)
        assertSame(fixtureBinding, router.bindingFor(destination))
        assertEquals(state, restoredState)
    }

    @Test
    fun third_game_participates_in_owned_host_and_peer_session_restoration() {
        val hostRoute = MultiplayerSessionRoute.host(
            gameId = fixtureDefinition.id,
            displayName = "Host",
        )
        val peerRoute = MultiplayerSessionRoute.peer(
            gameId = fixtureDefinition.id,
            displayName = "Peer",
            roomCode = "ROOM-1",
        )

        val hostLaunch = assertNotNull(router.restoreOwned(hostRoute))
        val peerLaunch = assertNotNull(router.restoreOwned(peerRoute))

        assertSame(fixtureBinding, router.bindingFor(hostLaunch))
        assertSame(fixtureBinding, router.bindingFor(peerLaunch))
    }

    @Test
    fun multiplayer_resume_requires_the_registered_game_protocol_version() {
        assertNotNull(
            router.resumeMultiplayer(
                gameId = fixtureDefinition.id,
                gameVersion = 1,
                displayName = "Peer",
            ),
        )
        assertEquals(
            null,
            router.resumeMultiplayer(
                gameId = fixtureDefinition.id,
                gameVersion = 99,
                displayName = "Peer",
            ),
        )
    }

    @Test
    fun duplicate_shell_registration_fails_before_catalog_or_routing() {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultGameShellRegistry(listOf(fixtureBinding, FixtureBinding(fixtureDefinition)))
        }

        assertEquals(
            "Duplicate game shell ids are not allowed: round-robin-test",
            error.message,
        )
    }

    @Test
    fun router_rejects_launch_kinds_the_registered_binding_does_not_support() {
        val hostOnly = FixtureBinding(
            definition = ExistingDefinition(),
            modes = setOf(GameEntryMode.Host),
        )
        val hostOnlyRouter = GameShellRouter(DefaultGameShellRegistry(listOf(hostOnly)))
        val gameId = hostOnly.definition.id

        assertEquals(null, hostOnlyRouter.resumeLocal(gameId, SessionId("local")))
        assertEquals(null, hostOnlyRouter.resumeMultiplayer(gameId, 1, "Peer"))
        assertNotNull(
            hostOnlyRouter.restoreOwned(
                MultiplayerSessionRoute.host(gameId, "Host"),
            ),
        )
    }

    @Test
    fun picker_preserves_independent_host_join_and_local_capabilities() {
        assertEquals(
            PlayModePickerAvailability(
                solo = false,
                passAndPlay = false,
                host = true,
                join = false,
            ),
            GameShellCapabilities(setOf(GameEntryMode.Host)).toPlayModePickerAvailability(),
        )
        assertEquals(
            PlayModePickerAvailability(
                solo = false,
                passAndPlay = true,
                host = false,
                join = true,
            ),
            GameShellCapabilities(
                setOf(GameEntryMode.PassAndPlay, GameEntryMode.Join),
            ).toPlayModePickerAvailability(),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun third_game_content_is_rendered_through_the_registered_shell_binding() = runTest {
        var renderedLaunch: GameShellLaunch? = null
        val binding = FixtureBinding(
            definition = fixtureDefinition,
            onRendered = { renderedLaunch = it },
        )
        val fixtureRouter = GameShellRouter(DefaultGameShellRegistry(listOf(binding)))
        val launch = assertNotNull(fixtureRouter.newGame(fixtureDefinition.id))
        val compositionContext = coroutineContext + ImmediateFrameClock
        val recomposer = Recomposer(compositionContext)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerJob = launch(ImmediateFrameClock) { recomposer.runRecomposeAndApplyChanges() }

        try {
            composition.setContent {
                assertNotNull(fixtureRouter.bindingFor(launch)).Content(
                    launch = launch,
                    onExit = {},
                )
            }
            runCurrent()
            assertEquals(launch, renderedLaunch)
        } finally {
            composition.dispose()
            recomposer.close()
            recomposerJob.join()
        }
    }

    @Test
    fun registry_rejects_a_multiplayer_contract_that_does_not_match_definition() {
        val mismatched = object : GameShellBinding {
            override val definition: GameDefinition<*, *, *> = fixtureDefinition
            override val capabilities = GameShellCapabilities(
                setOf(GameEntryMode.Host, GameEntryMode.Join),
            )
            override val multiplayerContract = GameShellMultiplayerContract(
                gameId = GameId("other-game"),
                gameVersion = 1,
                supportedPlayerCounts = fixtureDefinition.supportedPlayerCounts,
            )

            @Composable
            override fun catalogPresentation() = fixtureBinding.catalogPresentation()

            @Composable
            override fun Content(
                launch: GameShellLaunch,
                onExit: () -> Unit,
                modifier: Modifier,
            ) = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            DefaultGameShellRegistry(listOf(mismatched))
        }
    }

    private fun sessionConfig(
        sessionId: SessionId = SessionId("fixture-start"),
    ) = SessionConfig(
        sessionId = sessionId,
        caseId = CaseId("none"),
        modeId = ModeId("round-robin"),
        players = listOf(
            Player(PlayerId("p1"), "Alice", 0),
            Player(PlayerId("p2"), "Bob", 1),
        ),
        randomSeed = 7L,
    )
}

private class FixtureBinding(
    override val definition: GameDefinition<*, *, *>,
    modes: Set<GameEntryMode> = setOf(
        GameEntryMode.PassAndPlay,
        GameEntryMode.Host,
        GameEntryMode.Join,
    ),
    private val onRendered: ((GameShellLaunch) -> Unit)? = null,
) : GameShellBinding {
    override val capabilities = GameShellCapabilities(modes)
    override val multiplayerContract = if (
        GameEntryMode.Host in modes || GameEntryMode.Join in modes
    ) {
        GameShellMultiplayerContract(
            gameId = definition.id,
            gameVersion = 1,
            supportedPlayerCounts = definition.supportedPlayerCounts,
        )
    } else {
        null
    }

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = definition.id.raw,
        subtitle = "fixture",
        tagline = "fixture",
        openLabel = "open",
        openContentDescription = "open fixture",
    )

    @Composable
    override fun Content(launch: GameShellLaunch, onExit: () -> Unit, modifier: Modifier) {
        SideEffect { onRendered?.invoke(launch) }
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

private class ExistingDefinition(
    delegate: RoundRobinAnnounceGame = RoundRobinAnnounceGame(),
) : GameDefinition<com.parlor.engine.testing.fakes.RrState,
    com.parlor.engine.testing.fakes.RrAction,
    com.parlor.engine.testing.fakes.RrEvent> by delegate {
    override val id: GameId = GameId("existing-test-game")
}
