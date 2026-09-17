package com.parlor.games.lastlight.domain.reducer

import com.parlor.core.ids.PlayerId
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.event.LastLightEvent
import com.parlor.games.lastlight.domain.model.GameAction
import com.parlor.games.lastlight.domain.model.GameDecision
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameState
import com.parlor.games.lastlight.domain.model.PlayerIdentity
import com.parlor.games.lastlight.domain.model.immutableSetSnapshot
import com.parlor.games.lastlight.domain.rules.LastLightCardIds
import com.parlor.games.lastlight.domain.rules.LastLightEngine
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.domain.rules.LastLightSessionRules
import com.parlor.games.lastlight.domain.state.LastLightHistoryEntry
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.domain.state.toLastLightState
import com.parlor.games.lastlight.domain.state.toRulesState
import kotlin.random.Random

/**
 * The original rules operate on immutable values. A round-specific entropy
 * factory is injected so the complete future is determined by persisted
 * host-only seed and round, rather than a process-local RNG cursor.
 */
class LastLightReducer(private val randomForRound: (Long, Int) -> Random) :
    GameReducer<LastLightState, LastLightAction, LastLightEvent> {

    fun createInitialState(config: SessionConfig): LastLightState {
        LastLightSessionRules.requireValidConfig(config)
        val engine = LastLightEngine(randomForRound(config.randomSeed, 1))
        val initial = engine.start(config.players.map { PlayerIdentity(it.id.raw, it.displayName) })
        return initial.toLastLightState(config.players, config.randomSeed)
    }

    override fun reduce(
        state: LastLightState,
        action: LastLightAction,
        ctx: ReducerContext,
    ): Reduction<LastLightState, LastLightEvent> = apply(state, action)

    /** Same reducer entry without an unused time source; useful for deterministic recovery replay. */
    internal fun apply(state: LastLightState, action: LastLightAction): Reduction<LastLightState, LastLightEvent> {
        if (state.phase == GamePhase.FINISHED) return Reduction(state)
        if (!hasAuthorityBuckets(state)) return Reduction(state)
        return when (action) {
            is LastLightAction.MarkPlayerDisconnected -> markDisconnected(state, action.playerId)
            is LastLightAction.MarkPlayerReconnected -> markReconnected(state, action.playerId)
            is LastLightAction.ContinueWithoutPlayer -> continueWithout(state, action.playerId)
            LastLightAction.EndGame -> endGame(state)
            else -> {
                if (state.public.disconnectedPlayers.isNotEmpty()) Reduction(state)
                else applyGameplay(state, action)
            }
        }
    }

    private fun applyGameplay(
        state: LastLightState,
        action: LastLightAction,
    ): Reduction<LastLightState, LastLightEvent> {
        val rules = state.toRulesState()
        val engine = LastLightEngine(NoActionEntropy)
        val decision = when (action) {
            is LastLightAction.PlayCards -> engine.apply(rules, GameAction.Play(action.by.raw, action.cardIds))
            is LastLightAction.Challenge -> engine.apply(rules, GameAction.Challenge(action.by.raw))
            LastLightAction.NextRound -> {
                if (rules.phase != GamePhase.ROUND_ENDED || rules.roundNumber >= LastLightRules.MAX_ROUNDS) {
                    return Reduction(state)
                }
                LastLightEngine(randomForRound(state.hostOnly.randomSeed, rules.roundNumber + 1)).advanceRound(rules)
            }
            else -> return Reduction(state)
        }
        if (decision !is GameDecision.Applied) return Reduction(state)
        val entry = historyEntry(state, action) ?: return Reduction(state)
        val next = installRules(state, decision.state, entry)
        val event = when (action) {
            is LastLightAction.PlayCards -> LastLightEvent.CardsPlayed(
                action.by,
                action.cardIds.size,
                next.public.acceptedPlaySequence,
            )
            is LastLightAction.Challenge -> LastLightEvent.ChallengeResolved(next.public.outcomeSequence)
            else -> LastLightEvent.RoundStarted(next.public.roundNumber)
        }
        val events = if (next.phase == GamePhase.FINISHED) {
            listOf(event, LastLightEvent.GameEnded(next.public.winnerId?.let(::PlayerId)))
        } else listOf(event)
        return Reduction(next, events)
    }

    private fun historyEntry(state: LastLightState, action: LastLightAction): LastLightHistoryEntry? = when (action) {
        is LastLightAction.PlayCards -> LastLightHistoryEntry(
            seat = state.players.indexOfFirst { it.id == action.by },
            cardSlots = action.cardIds.map { requireNotNull(LastLightCardIds.slot(it, state.public.roundNumber)) },
        )
        is LastLightAction.Challenge -> LastLightHistoryEntry(state.players.indexOfFirst { it.id == action.by })
        LastLightAction.NextRound -> LastLightHistoryEntry(LastLightHistoryEntry.NEXT_ROUND)
        else -> null
    }

    private fun installRules(
        previous: LastLightState,
        rules: GameState,
        entry: LastLightHistoryEntry,
        endedEarly: Boolean = false,
    ): LastLightState = rules.toLastLightState(
        roster = previous.players,
        seed = previous.hostOnly.randomSeed,
        history = previous.hostOnly.history + entry,
        endedEarly = endedEarly,
    )

    private fun markDisconnected(state: LastLightState, id: PlayerId): Reduction<LastLightState, LastLightEvent> {
        if (!isSurvivor(state, id) || id in state.public.disconnectedPlayers) return Reduction(state)
        return Reduction(
            state.copy(public = state.public.copy(
                disconnectedPlayers = (state.public.disconnectedPlayers + id).immutableSetSnapshot(),
            )),
            listOf(LastLightEvent.PlayerDisconnected(id)),
        )
    }

    private fun markReconnected(state: LastLightState, id: PlayerId): Reduction<LastLightState, LastLightEvent> {
        if (id !in state.public.disconnectedPlayers) return Reduction(state)
        return Reduction(
            state.copy(public = state.public.copy(
                disconnectedPlayers = (state.public.disconnectedPlayers - id).immutableSetSnapshot(),
            )),
            listOf(LastLightEvent.PlayerReconnected(id)),
        )
    }

    private fun continueWithout(state: LastLightState, id: PlayerId): Reduction<LastLightState, LastLightEvent> {
        if (id !in state.public.disconnectedPlayers || !isSurvivor(state, id)) return Reduction(state)
        val ended = endGame(state)
        return ended.copy(newState = ended.newState.copy(public = ended.newState.public.copy(
            droppedPlayers = listOf(id).immutableSetSnapshot(),
        )))
    }

    private fun endGame(state: LastLightState): Reduction<LastLightState, LastLightEvent> {
        val rules = state.toRulesState().copy(
            phase = GamePhase.FINISHED,
            turnPlayerId = null,
            pendingPlay = null,
            winnerId = null,
        )
        val ended = installRules(state, rules, LastLightHistoryEntry(LastLightHistoryEntry.END_GAME), endedEarly = true)
        return Reduction(ended, listOf(LastLightEvent.GameEnded(null)))
    }

    private fun hasAuthorityBuckets(state: LastLightState): Boolean {
        val ids = state.players.map { it.id }.toSet()
        return ids.size in LastLightRules.MIN_PLAYERS..LastLightRules.MAX_PLAYERS &&
            state.privatePerPlayer.keys == ids && state.hostOnly.burnoutSteps.keys == ids &&
            state.hostOnly.openerPlayerId in ids.map { it.raw } &&
            state.hostOnly.history.size < LastLightRules.MAX_HISTORY_ENTRIES
    }

    private fun isSurvivor(state: LastLightState, id: PlayerId): Boolean =
        state.public.roster.any { it.id == id.raw && !it.eliminated }

    private object NoActionEntropy : Random() {
        override fun nextBits(bitCount: Int): Int = error("Player actions cannot consume game entropy")
    }
}
