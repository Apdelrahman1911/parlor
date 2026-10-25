package com.parlor.games.dominoes.domain

import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.engine.reducer.GameReducer
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.reducer.ReducerContext
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.dominoes.DominoIds

/** Egyptian default/draw/block rules. Doubles are not spinners; side scoring occurs once per hand. */
class DominoReducer : GameReducer<DominoState, DominoAction, DominoChanged> {
    fun initial(config: SessionConfig): DominoState {
        require(config.modeId == DominoIds.Standard && DominoRoster.isValidPlayerId(config.sessionId.raw))
        return initial(config.players, requireNotNull(DominoSettings.fromCaseId(config.caseId.raw)), config.randomSeed)
    }

    fun initial(players: List<Player>, settings: DominoSettings, seed: Long, token: Long = 1L): DominoState {
        require(DominoRoster.isValidRoster(players) && settings.supports(players.size) && token in 1..DominoRules.MAX_TOKEN)
        return deal(players.toList(), settings, seed, token, 1, DominoScoring.sides(players, settings).mapValues { 0 }, null)
    }

    override fun reduce(state: DominoState, action: DominoAction, ctx: ReducerContext): Reduction<DominoState, DominoChanged> {
        val next = apply(state, action)
        return Reduction(next, if (next == state) emptyList() else listOf(DominoChanged(next.public.token, next.public.move)))
    }

    fun apply(state: DominoState, action: DominoAction): DominoState {
        if (state.hostOnly.seed == null || state.phase == DominoPhase.Aborted) return state
        if (state.hostOnly.history.size >= DominoRules.MAX_HISTORY) return state
        val next = when (action) {
            is DominoAction.Disconnected -> disconnect(state, action.playerId, true)
            is DominoAction.Reconnected -> disconnect(state, action.playerId, false)
            DominoAction.Abort -> aborted(state)
            else -> if (state.public.disconnected.isEmpty()) play(state, action) else state
        }
        if (next == state) return state
        if (action is DominoAction.Rematch) return next
        // Reserve the final replay entry for an explicit terminal transition.
        // Extreme lifecycle churn must not strand a room at a full history bound.
        val exhausted = state.hostOnly.history.size == DominoRules.MAX_HISTORY - 1
        val accepted = if (exhausted) aborted(state) else next
        val recorded = if (exhausted) DominoAction.Abort else action
        return accepted.copy(hostOnly = accepted.hostOnly.copy(history = state.hostOnly.history + recorded))
    }

    private fun aborted(state: DominoState): DominoState = state.copy(
        phase = DominoPhase.Aborted,
        public = state.public.copy(turn = null, result = null, matchWinners = emptySet(), disconnected = emptySet()),
    )

    private fun play(state: DominoState, action: DominoAction): DominoState = when (action) {
        is DominoAction.Place -> if (validTurn(state, action.by, action.token, action.move)) place(state, action) else state
        is DominoAction.Draw -> if (validTurn(state, action.by, action.token, action.move)) draw(state, action.by) else state
        is DominoAction.Pass -> if (validTurn(state, action.by, action.token, action.move)) pass(state, action.by) else state
        is DominoAction.NextRound -> nextRound(state, action.token)
        is DominoAction.Rematch -> if (
            state.phase == DominoPhase.MatchResult && action.token == state.public.token && action.token < DominoRules.MAX_TOKEN
        ) {
            initial(state.players, state.public.settings,
                RandomSource.seeded(checkNotNull(state.hostOnly.seed)).nextLong(), action.token + 1)
        } else state
        else -> state
    }

    private fun validTurn(state: DominoState, by: PlayerId, token: Long, move: Int): Boolean =
        state.phase == DominoPhase.Playing && by == state.public.turn &&
            token == state.public.token && move == state.public.move

    private fun place(state: DominoState, action: DominoAction.Place): DominoState {
        val own = state.privatePerPlayer[action.by] ?: return state
        val tile = own.hand.firstOrNull { it.id == action.tileId } ?: return state
        if (action.end !in DominoRules.playableEnds(state, action.by, tile)) return state
        val chain = state.public.chain
        val match = when {
            chain.isEmpty() -> tile.low
            action.end == DominoEnd.Left -> chain.first().left
            else -> chain.last().right
        }
        val other = if (match == tile.low) tile.high else tile.low
        val left = if (action.end == DominoEnd.Left) other else match
        val right = if (action.end == DominoEnd.Left) match else other
        val placed = PlacedDomino(tile, left, right, action.by, state.public.move + 1)
        val remaining = own.hand - tile
        val next = state.copy(
            public = state.public.copy(
                chain = if (action.end == DominoEnd.Left) listOf(placed) + chain else chain + placed,
                handCounts = state.public.handCounts + (action.by to remaining.size),
                move = state.public.move + 1, consecutivePasses = 0, turn = nextPlayer(state, action.by),
            ),
            privatePerPlayer = state.privatePerPlayer.mapValues { (id, private) ->
                if (id == action.by) DominoPrivate(remaining) else private.copy(requiredOpening = null)
            },
            hostOnly = state.hostOnly.copy(requiredOpening = null),
        )
        return if (remaining.isEmpty()) finishHand(next, action.by, blocked = false) else next
    }

    private fun draw(state: DominoState, by: PlayerId): DominoState {
        if (!DominoRules.canDraw(state, by)) return state
        val tile = state.hostOnly.stock.firstOrNull() ?: return state
        val hand = state.privatePerPlayer.getValue(by).hand + tile
        return state.copy(
            public = state.public.copy(
                move = state.public.move + 1,
                stockCount = state.public.stockCount - 1,
                handCounts = state.public.handCounts + (by to hand.size),
            ),
            privatePerPlayer = state.privatePerPlayer + (by to DominoPrivate(hand)),
            hostOnly = state.hostOnly.copy(stock = state.hostOnly.stock.drop(1)),
        )
    }

    private fun pass(state: DominoState, by: PlayerId): DominoState {
        if (!DominoRules.canPass(state, by)) return state
        val next = state.copy(public = state.public.copy(
            move = state.public.move + 1, consecutivePasses = state.public.consecutivePasses + 1,
            turn = nextPlayer(state, by),
        ))
        if (next.public.consecutivePasses < next.players.size) return next
        val pipCounts = next.privatePerPlayer.mapValues { (_, private) -> private.hand.sumOf { it.pips } }
        return finishHand(next, DominoScoring.blockedWinner(next, pipCounts), blocked = true)
    }

    private fun finishHand(state: DominoState, winner: PlayerId?, blocked: Boolean): DominoState {
        val pips = state.privatePerPlayer.mapValues { (_, private) -> private.hand.sumOf { it.pips } }
        val points = DominoScoring.points(state, pips, winner, blocked)
        val side = winner?.let { DominoScoring.sideOf(state, it) }
        val scores = if (side == null) state.public.scores else
            state.public.scores + (side to state.public.scores.getValue(side) + points)
        val finished = DominoScoring.reachedTarget(state.public.settings, scores) || state.public.round == DominoRules.MAX_HANDS
        val winners = if (finished) DominoScoring.matchWinners(state, scores) else emptySet()
        return state.copy(
            phase = if (finished) DominoPhase.MatchResult else DominoPhase.RoundResult,
            public = state.public.copy(
                turn = null, scores = scores, result = DominoRoundResult(winner, blocked, pips, points), matchWinners = winners,
            ),
        )
    }

    private fun nextRound(state: DominoState, token: Long): DominoState {
        if (state.phase != DominoPhase.RoundResult || token != state.public.token || token >= DominoRules.MAX_TOKEN) return state
        val leader = state.public.result?.winner ?: nextPlayer(state, checkNotNull(state.hostOnly.leader))
        return deal(
            state.players, state.public.settings, checkNotNull(state.hostOnly.seed), token + 1,
            state.public.round + 1, state.public.scores, leader,
        ).let { it.copy(hostOnly = it.hostOnly.copy(firstToken = state.hostOnly.firstToken)) }
    }

    private fun nextPlayer(state: DominoState, by: PlayerId): PlayerId =
        state.players[(state.players.indexOfFirst { it.id == by } + 1) % state.players.size].id

    private fun disconnect(state: DominoState, id: PlayerId, offline: Boolean): DominoState {
        if (state.players.none { it.id == id }) return state
        val disconnected = if (offline) state.public.disconnected + id else state.public.disconnected - id
        return if (disconnected == state.public.disconnected) state else state.copy(public = state.public.copy(disconnected = disconnected))
    }

    private fun deal(
        players: List<Player>, settings: DominoSettings, seed: Long, token: Long, round: Int,
        scores: Map<PlayerId, Int>, previousWinner: PlayerId?,
    ): DominoState {
        val tiles = RandomSource.seeded(seed xor token).shuffled(DominoRules.tiles(settings, players.size))
        val handSize = DominoRules.handSize(settings, players.size)
        val hands = players.associate { player -> player.id to tiles.drop(player.seat * handSize).take(handSize) }
        val dealt = hands.values.flatten()
        val opening = if (previousWinner != null) null else
            dealt.filter { it.isDouble }.maxByOrNull { it.high } ?: dealt.maxWith(compareBy<DominoTile> { it.pips }.thenBy { it.high })
        val leader = previousWinner ?: hands.entries.first { opening in it.value }.key
        val stock = tiles.drop(players.size * handSize)
        return DominoState(
            public = DominoPublic(settings, round, token, 0, leader, emptyList(), hands.mapValues { it.value.size }, stock.size, 0, scores),
            privatePerPlayer = hands.mapValues { (id, hand) -> DominoPrivate(hand, opening.takeIf { id == leader }) },
            hostOnly = DominoHostOnly(seed, token, stock, opening, leader),
            phase = DominoPhase.Playing, players = players,
        )
    }
}
