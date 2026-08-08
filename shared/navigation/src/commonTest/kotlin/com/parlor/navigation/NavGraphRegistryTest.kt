package com.parlor.navigation

import com.parlor.core.ids.GameId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class NavGraphRegistryTest {

    @Test
    fun graphs_are_discovered_by_game_id_without_shell_branches() {
        val first = TestGraph("first")
        val second = TestGraph("second")
        val registry = DefaultNavGraphRegistry(listOf(first, second))

        assertEquals(listOf(first, second), registry.all)
        assertSame(second, registry.byGameId(GameId("second")))
    }

    @Test
    fun duplicate_graph_ids_fail_during_registration() {
        val first = TestGraph("duplicate")
        val second = TestGraph("duplicate")

        val error = assertFailsWith<IllegalArgumentException> {
            DefaultNavGraphRegistry(listOf(first, second))
        }

        assertEquals(
            "Duplicate navigation graphs are not allowed: duplicate",
            error.message,
        )
    }
}

private class TestGraph(id: String) : ModuleNavGraph {
    override val gameId: GameId = GameId(id)
    override val entryRoute: ParlorRoute = ParlorRoute.Home
}
