package com.parlor.app

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.snapshot.GameSnapshot
import com.parlor.app.shell.game.DefaultGameShellRegistry
import com.parlor.app.shell.game.GameShellLaunch
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.mafia.MafiaIds
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LocalResumeRouterTest {
    private val json = Json { encodeDefaults = true }
    private val router = GameShellRouter(
        DefaultGameShellRegistry(
            listOf(
                WhodunitGameShellBinding(WhodunitDefinition(json)),
                MafiaGameShellBinding(MafiaDefinition(json)),
            ),
        ),
    )

    @Test
    fun routes_each_shipping_game_from_the_authenticated_envelope() = kotlinx.coroutines.test.runTest {
        val store = InMemorySnapshotStore()
        val whodunit = SessionId("whodunit-session")
        val mafia = SessionId("mafia-session")
        store.save(snapshot(whodunit, WhodunitIds.GameId))
        store.save(snapshot(mafia, MafiaIds.GameId))

        assertEquals(
            Result.Success(GameShellLaunch.ResumeLocal(WhodunitIds.GameId, whodunit)),
            resolveLocalResumeDestination(store, router, whodunit),
        )
        assertEquals(
            Result.Success(GameShellLaunch.ResumeLocal(MafiaIds.GameId, mafia)),
            resolveLocalResumeDestination(store, router, mafia),
        )
    }

    @Test
    fun rejects_unknown_games_instead_of_decoding_them_as_whodunit() = kotlinx.coroutines.test.runTest {
        val store = InMemorySnapshotStore()
        val sessionId = SessionId("unknown-session")
        store.save(snapshot(sessionId, GameId("modified-client-game")))

        val result = resolveLocalResumeDestination(store, router, sessionId)

        assertIs<Result.Failure<DataError>>(result)
        assertEquals(DataError.CorruptedData, result.error)
    }

    @Test
    fun unreadable_outer_snapshot_routes_to_explicit_recovery_for_that_exact_save() {
        val sessionId = SessionId("corrupt-session")

        val screen = localResumeResultScreen(
            sessionId = sessionId,
            result = Result.Failure(DataError.CorruptedData),
        )

        assertEquals(AppScreen.LocalResumeFailure(sessionId), screen)
    }

    @Test
    fun readable_outer_snapshot_still_routes_directly_to_its_registered_game() {
        val sessionId = SessionId("healthy-session")
        val launch = GameShellLaunch.ResumeLocal(MafiaIds.GameId, sessionId)

        val screen = localResumeResultScreen(sessionId, Result.Success(launch))

        assertEquals(AppScreen.Game(launch), screen)
    }

    @Test
    fun cancellation_during_snapshot_routing_is_not_converted_to_a_data_error() =
        kotlinx.coroutines.test.runTest {
            val store = object : SnapshotStore by InMemorySnapshotStore() {
                override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> =
                    awaitCancellation()
            }
            val request = async {
                resolveLocalResumeDestination(store, router, SessionId("cancelled-session"))
            }

            runCurrent()
            request.cancel()

            assertFailsWith<CancellationException> { request.await() }
        }

    @Test
    fun only_the_latest_resume_request_generation_may_navigate() {
        val gate = LocalResumeRequestGate()

        val first = gate.begin()
        val second = gate.begin()

        assertEquals(false, gate.isCurrent(first))
        assertEquals(true, gate.isCurrent(second))

        gate.invalidate()
        assertEquals(false, gate.isCurrent(second))
    }

    @Test
    fun coordinator_surfaces_an_unreadable_save_without_deleting_it() =
        kotlinx.coroutines.test.runTest {
            val sessionId = SessionId("unreadable-session")
            var deletes = 0
            val backingStore = InMemorySnapshotStore()
            val store = object : SnapshotStore by backingStore {
                override suspend fun delete(sessionId: SessionId) =
                    backingStore.delete(sessionId).also { deletes++ }
            }
            var screen: AppScreen = AppScreen.Home
            val coordinator = LocalResumeCoordinator(this, store, router)

            coordinator.request(sessionId, { screen }, { screen = it })
            assertTrue(coordinator.busy.value)
            runCurrent()

            assertEquals(AppScreen.LocalResumeFailure(sessionId), screen)
            assertEquals(0, deletes)
            assertFalse(coordinator.busy.value)
        }

    @Test
    fun coordinator_discards_only_after_an_explicit_recovery_action() =
        kotlinx.coroutines.test.runTest {
            val sessionId = SessionId("discard-session")
            val store = InMemorySnapshotStore().also {
                it.save(snapshot(sessionId, MafiaIds.GameId))
            }
            var screen: AppScreen = AppScreen.LocalResumeFailure(sessionId)
            var discarded = 0
            var failures = 0
            val coordinator = LocalResumeCoordinator(this, store, router)

            coordinator.discard(
                sessionId = sessionId,
                currentScreen = { screen },
                onDiscarded = {
                    discarded++
                    screen = AppScreen.Home
                },
                onFailure = { failures++ },
            )
            runCurrent()

            assertEquals(1, discarded)
            assertEquals(0, failures)
            assertIs<Result.Failure<DataError>>(store.load(sessionId))
            assertFalse(coordinator.busy.value)
        }

    @Test
    fun failed_discard_keeps_the_recovery_screen_and_reports_the_failure() =
        kotlinx.coroutines.test.runTest {
            val sessionId = SessionId("failed-discard-session")
            val store = object : SnapshotStore by InMemorySnapshotStore() {
                override suspend fun delete(sessionId: SessionId) =
                    Result.Failure(DataError.IoError("test_failure"))
            }
            val recovery = AppScreen.LocalResumeFailure(sessionId)
            var screen: AppScreen = recovery
            var discarded = 0
            var failures = 0
            val coordinator = LocalResumeCoordinator(this, store, router)

            coordinator.discard(
                sessionId = sessionId,
                currentScreen = { screen },
                onDiscarded = { discarded++ },
                onFailure = { failures++ },
            )
            runCurrent()

            assertEquals(recovery, screen)
            assertEquals(0, discarded)
            assertEquals(1, failures)
            assertFalse(coordinator.busy.value)
        }

    @Test
    fun invalidating_a_pending_resume_prevents_stale_navigation() =
        kotlinx.coroutines.test.runTest {
            val sessionId = SessionId("pending-session")
            val store = object : SnapshotStore by InMemorySnapshotStore() {
                override suspend fun load(sessionId: SessionId): Result<GameSnapshot, DataError> =
                    awaitCancellation()
            }
            var screen: AppScreen = AppScreen.Home
            val coordinator = LocalResumeCoordinator(this, store, router)

            coordinator.request(sessionId, { screen }, { screen = it })
            runCurrent()
            assertTrue(coordinator.busy.value)

            coordinator.invalidate()
            runCurrent()

            assertEquals(AppScreen.Home, screen)
            assertFalse(coordinator.busy.value)
        }

    private fun snapshot(sessionId: SessionId, gameId: GameId) = GameSnapshot(
        sessionId = sessionId,
        gameId = gameId,
        engineVersion = SemVer(1, 0, 0),
        createdAt = Instant.fromEpochSeconds(1),
        phaseId = "test",
        payload = byteArrayOf(1),
    )
}
