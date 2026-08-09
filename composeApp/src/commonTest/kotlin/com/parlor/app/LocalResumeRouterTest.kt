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

    private fun snapshot(sessionId: SessionId, gameId: GameId) = GameSnapshot(
        sessionId = sessionId,
        gameId = gameId,
        engineVersion = SemVer(1, 0, 0),
        createdAt = Instant.fromEpochSeconds(1),
        phaseId = "test",
        payload = byteArrayOf(1),
    )
}
