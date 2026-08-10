package com.parlor.engine.testing.registry

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.reducer.DefaultReducerContext
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
import com.parlor.engine.testing.fakes.RrAction
import com.parlor.engine.testing.fakes.RrEvent
import com.parlor.engine.testing.fakes.RrPhase
import com.parlor.engine.testing.fakes.RrState
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * Acceptance contract for adding a game without touching the app's session or
 * networking cores. [RoundRobinAnnounceGame] is a test-fixture definition and
 * is never linked into the shipping app.
 */
class GameRegistryExtensibilityTest {

    private val players = listOf(
        Player(PlayerId("p1"), "Alice", seat = 0),
        Player(PlayerId("p2"), "Bob", seat = 1),
    )

    @Test
    fun second_game_can_be_registered_resolved_and_exercised() {
        val installedFixture = ExistingCatalogFixture()
        val secondGameFixture = RoundRobinAnnounceGame()
        val registry = DefaultGameRegistry(
            definitions = listOf(installedFixture, secondGameFixture),
        )

        assertEquals(
            listOf(installedFixture.id, secondGameFixture.id),
            registry.all.map { definition -> definition.id },
        )
        assertSame(installedFixture, registry.byId(installedFixture.id))

        val resolved = assertIs<RoundRobinAnnounceGame>(registry.byId(secondGameFixture.id))
        val config = SessionConfig(
            sessionId = SessionId("extensibility-contract"),
            caseId = CaseId("none"),
            modeId = ModeId("round-robin"),
            players = players,
            randomSeed = 7L,
        )
        var state = resolved.createInitialState(config)
        val context = DefaultReducerContext(
            clock = FakeClock(Instant.fromEpochSeconds(1_700_000_000)),
            random = RandomSource.seeded(7),
        )

        players.forEach { player ->
            state = resolved.reducer()
                .reduce(state, RrAction.Announce(player.id), context)
                .newState
        }

        assertIs<RrPhase.Finished>(state.phase)
        assertEquals(players.map { player -> player.id }, state.announcedBy)
        assertSame(state, resolved.projectionPolicy().toPublic(state).state)
    }

    @Test
    fun duplicate_game_ids_fail_during_registration() {
        val duplicate = RoundRobinAnnounceGame()

        val error = assertFailsWith<IllegalArgumentException> {
            DefaultGameRegistry(listOf(duplicate, duplicate))
        }

        assertEquals(
            "Duplicate game ids are not allowed: round-robin-test",
            error.message,
        )
    }
}

private class ExistingCatalogFixture(
    delegate: RoundRobinAnnounceGame = RoundRobinAnnounceGame(),
) : GameDefinition<RrState, RrAction, RrEvent> by delegate {
    override val id: GameId = GameId("existing-catalog-fixture")
}
