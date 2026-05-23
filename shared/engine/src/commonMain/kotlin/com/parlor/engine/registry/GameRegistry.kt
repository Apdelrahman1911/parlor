package com.parlor.engine.registry

import com.parlor.core.ids.GameId
import com.parlor.engine.action.GameAction
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState

/**
 * Registry of installed game modules. Each module contributes its
 * `GameDefinition` via its Koin module at startup. The shell reads from this
 * registry to render the All Games grid.
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
    private val byId: Map<GameId, GameDefinition<*, *, *>> = all.associateBy { it.id }
    override fun byId(id: GameId): GameDefinition<*, *, *>? = byId[id]
}

@Suppress("UNCHECKED_CAST")
fun <S : GameState, A : GameAction, E : GameEvent> GameRegistry.typedByIdOrNull(
    id: GameId,
): GameDefinition<S, A, E>? = byId(id) as? GameDefinition<S, A, E>
