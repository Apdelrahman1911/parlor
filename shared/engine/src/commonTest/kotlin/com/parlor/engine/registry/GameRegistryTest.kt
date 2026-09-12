package com.parlor.engine.registry

import com.parlor.core.ids.GameId
import com.parlor.engine.action.GameAction
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.engine.definition.GameMode
import com.parlor.engine.event.GameEvent
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.snapshot.SnapshotCodec
import com.parlor.engine.state.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameRegistryTest {
    @Test
    fun empty_registry_has_no_catalog_or_lookup_entries(): Unit {
        val registry = DefaultGameRegistry(emptyList())

        assertTrue(registry.all.isEmpty())
        assertNull(registry.byId(GameId("missing-fixture")))
    }

    @Test
    fun catalog_preserves_registration_order_and_lookup_returns_original_definitions(): Unit {
        val lastAlphabetically = RegistryFixture("z-fixture")
        val firstAlphabetically = RegistryFixture("a-fixture")
        val registry = DefaultGameRegistry(listOf(lastAlphabetically, firstAlphabetically))

        assertEquals(listOf(lastAlphabetically, firstAlphabetically), registry.all)
        assertSame(lastAlphabetically, registry.byId(GameId("z-fixture")))
        assertSame(firstAlphabetically, registry.byId(GameId("a-fixture")))
        assertNull(registry.byId(GameId("missing-fixture")))
    }

    @Test
    fun duplicate_ids_on_distinct_definitions_fail_before_a_catalog_is_published(): Unit {
        val error = assertFailsWith<IllegalArgumentException> {
            DefaultGameRegistry(
                listOf(
                    RegistryFixture("z-fixture"),
                    RegistryFixture("a-fixture"),
                    RegistryFixture("z-fixture"),
                    RegistryFixture("a-fixture"),
                ),
            )
        }

        assertEquals("Duplicate game ids are not allowed: a-fixture, z-fixture", error.message)
    }

    @Test
    fun registering_the_same_definition_twice_is_also_rejected(): Unit {
        val fixture = RegistryFixture("same-fixture")

        val error = assertFailsWith<IllegalArgumentException> {
            DefaultGameRegistry(listOf(fixture, fixture))
        }

        assertEquals("Duplicate game ids are not allowed: same-fixture", error.message)
    }

    @Test
    fun mutating_the_input_list_cannot_change_the_captured_catalog_or_lookup(): Unit {
        val first = RegistryFixture("first-fixture")
        val second = RegistryFixture("second-fixture")
        val replacement = RegistryFixture("replacement-fixture")
        val registrations = mutableListOf<GameDefinition<*, *, *>>(first, second)
        val registry = DefaultGameRegistry(registrations)

        registrations.clear()
        registrations.add(replacement)

        assertEquals(listOf(first, second), registry.all)
        assertSame(first, registry.byId(first.id))
        assertSame(second, registry.byId(second.id))
        assertNull(registry.byId(replacement.id))
    }
}

/** Registry operations must not instantiate a session or execute any game behavior. */
private class RegistryFixture(rawId: String) : GameDefinition<GameState, GameAction, GameEvent> {
    override val id = GameId(rawId)
    override val metadata: GameMetadata get() = error("Registry must not read game metadata")
    override val supportedModes: List<GameMode> get() = error("Registry must not read game modes")
    override val supportedPlayerCounts: IntRange get() = error("Registry must not read player counts")

    override fun createInitialState(config: SessionConfig): GameState = error("Registry must not create a session")
    override fun reducer(): GameReducer<GameState, GameAction, GameEvent> = error("Registry must not execute a reducer")
    override fun projectionPolicy(): ProjectionPolicy<GameState> = error("Registry must not execute a projection")
    override fun snapshotCodec(): SnapshotCodec<GameState> = error("Registry must not encode or restore a session")
}
