package com.parlor.navigation

import com.parlor.core.ids.GameId

/**
 * Each game module registers a [ModuleNavGraph] in the registry. The Parlor
 * shell's NavHost composes them all into one graph at startup.
 */
interface NavGraphRegistry {
    val all: List<ModuleNavGraph>
    fun byGameId(id: GameId): ModuleNavGraph?
}

/**
 * Contract every game module's nav graph builder satisfies. The actual
 * NavGraphBuilder extension lives in module UI code (Compose-aware); this
 * pure-Kotlin contract just lets the shell discover them.
 *
 * The `entryRoute` is the route the shell navigates to when a user enters
 * this game from the Home / Game Details screen.
 */
interface ModuleNavGraph {
    val gameId: GameId
    val entryRoute: ParlorRoute
}

class DefaultNavGraphRegistry(
    private val graphs: List<ModuleNavGraph>,
) : NavGraphRegistry {
    override val all: List<ModuleNavGraph> = graphs.toList()

    init {
        val duplicateIds = all
            .groupingBy { it.gameId }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sortedBy { id -> id.raw }
        require(duplicateIds.isEmpty()) {
            "Duplicate navigation graphs are not allowed: ${duplicateIds.joinToString { it.raw }}"
        }
    }

    private val byId = all.associateBy { it.gameId }
    override fun byGameId(id: GameId): ModuleNavGraph? = byId[id]
}
