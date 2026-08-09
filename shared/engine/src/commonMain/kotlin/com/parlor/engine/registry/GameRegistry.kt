package com.parlor.engine.registry

import com.parlor.core.ids.GameId
import com.parlor.engine.definition.GameDefinition

/**
 * Transport- and UI-independent registry of installed game definitions.
 * The application composition root derives it from the installed shell
 * bindings so content validation and the visible catalog cannot drift.
 */
interface GameRegistry {
    val all: List<GameDefinition<*, *, *>>
    fun byId(id: GameId): GameDefinition<*, *, *>?
}

/**
 * In-memory implementation. Modules register at startup; the registry is
 * effectively immutable thereafter for the session.
 */
class DefaultGameRegistry(
    private val definitions: List<GameDefinition<*, *, *>>,
) : GameRegistry {
    override val all: List<GameDefinition<*, *, *>> = definitions.toList()

    init {
        val duplicateIds = all
            .groupingBy { it.id }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sortedBy { id -> id.raw }
        require(duplicateIds.isEmpty()) {
            "Duplicate game ids are not allowed: ${duplicateIds.joinToString { it.raw }}"
        }
    }

    private val byId: Map<GameId, GameDefinition<*, *, *>> = all.associateBy { it.id }
    override fun byId(id: GameId): GameDefinition<*, *, *>? = byId[id]
}
