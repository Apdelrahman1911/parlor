package com.parlor.games.dominoes.domain

import com.parlor.core.ids.PlayerId

object DominoValidation {
    private const val TOTAL_TILES = 28
    private const val MAX_PIPS = 12
    private const val MAX_SCORE = 20_000

    fun publicState(state: DominoState): Boolean {
        if (!DominoRoster.isValidRoster(state.players)) return false
        val p = state.public
        val ids = state.players.map { it.id }.toSet()
        if (!validBounds(p, ids) || !validChain(p.chain, ids, p.move)) return false
        return when (state.phase) {
            DominoPhase.Playing -> p.turn in ids && p.result == null && p.matchWinners.isEmpty() &&
                p.handCounts.values.all { it > 0 } && p.consecutivePasses < ids.size
            DominoPhase.RoundResult -> p.turn == null && validResult(state) && p.matchWinners.isEmpty() &&
                p.round < DominoRules.MAX_HANDS && p.scores.values.none { it >= p.settings.target }
            DominoPhase.MatchResult -> p.turn == null && validResult(state) &&
                (p.round == DominoRules.MAX_HANDS || p.scores.values.any { it >= p.settings.target }) &&
                p.matchWinners == p.scores.filterValues { it == p.scores.values.max() }.keys
            DominoPhase.Aborted -> p.turn == null && p.result == null && p.matchWinners.isEmpty() && p.disconnected.isEmpty()
        }
    }

    fun playerState(state: DominoState, id: PlayerId): Boolean {
        if (!publicState(state) || state.hostOnly != DominoHostOnly() || state.privatePerPlayer.keys != setOf(id)) return false
        if (state.players.none { it.id == id }) return false
        val own = state.privatePerPlayer[id] ?: return false
        if (state.phase == DominoPhase.Aborted) return own == DominoPrivate()
        if (own.hand.size != state.public.handCounts[id] || own.hand.distinct().size != own.hand.size) return false
        if (own.hand.any { tile -> state.public.chain.any { it.tile == tile } }) return false
        if (state.public.result?.remainingPips?.get(id)?.let { it != own.hand.sumOf(DominoTile::pips) } == true) return false
        val requiresOpening = state.public.chain.isEmpty() && state.public.round == 1 && state.public.turn == id
        return if (requiresOpening) own.requiredOpening != null && own.requiredOpening in own.hand else own.requiredOpening == null
    }

    private fun validBounds(p: DominoPublic, ids: Set<PlayerId>): Boolean {
        if (p.token !in 1..DominoRules.MAX_TOKEN || p.round !in 1..DominoRules.MAX_HANDS || p.move !in 0..DominoRules.MAX_MOVE) return false
        if (p.handCounts.keys != ids || p.scores.keys != ids || !ids.containsAll(p.disconnected)) return false
        if (p.handCounts.values.any { it !in 0..TOTAL_TILES } || p.scores.values.any { it !in 0..MAX_SCORE }) return false
        val initialStock = TOTAL_TILES - ids.size * DominoRules.HAND_SIZE
        if (p.stockCount !in 0..initialStock) return false
        if (p.settings.variant == DominoVariant.Block && p.stockCount != initialStock) return false
        return p.chain.size + p.stockCount + p.handCounts.values.sum() == TOTAL_TILES && p.consecutivePasses in 0..ids.size
    }

    private fun validChain(chain: List<PlacedDomino>, ids: Set<PlayerId>, move: Int): Boolean {
        if (chain.size > TOTAL_TILES || chain.map { it.tile }.distinct().size != chain.size) return false
        if (chain.map { it.sequence }.distinct().size != chain.size) return false
        if (chain.any { it.by !in ids || it.sequence !in 1..move ||
                listOf(it.left, it.right).sorted() != listOf(it.tile.low, it.tile.high) }) return false
        return chain.zipWithNext().all { (a, b) -> a.right == b.left }
    }

    private fun validResult(state: DominoState): Boolean {
        val result = state.public.result ?: return false
        val ids = state.players.map { it.id }.toSet()
        if (result.remainingPips.keys != ids || result.remainingPips.any { (id, pips) ->
                pips !in 0..state.public.handCounts.getValue(id) * MAX_PIPS }) return false
        val stockPips = DominoTile.Set.sumOf { it.pips } - state.public.chain.sumOf { it.tile.pips } - result.remainingPips.values.sum()
        if (stockPips !in 0..state.public.stockCount * MAX_PIPS) return false
        val winner = result.winner
        if (winner != null && winner !in ids) return false
        if (!result.blocked && (winner == null || state.public.handCounts[winner] != 0)) return false
        if (result.blocked) {
            if (state.public.consecutivePasses != ids.size) return false
            val lowest = result.remainingPips.values.min()
            if (winner != result.remainingPips.filterValues { it == lowest }.keys.singleOrNull()) return false
        }
        val expectedPoints = if (winner == null) 0 else result.remainingPips.filterKeys { it != winner }.values.sum() -
            if (result.blocked) result.remainingPips.getValue(winner) else 0
        return result.points == expectedPoints && (winner == null || state.public.scores.getValue(winner) >= expectedPoints)
    }
}

object DominoActionValidation {
    fun valid(action: DominoAction): Boolean = when (action) {
        is DominoAction.Place -> turn(action.by, action.token, action.move) && DominoTile.byId(action.tileId) != null
        is DominoAction.Draw -> turn(action.by, action.token, action.move)
        is DominoAction.Pass -> turn(action.by, action.token, action.move)
        is DominoAction.NextRound -> action.token in 1..DominoRules.MAX_TOKEN
        is DominoAction.Rematch -> action.token in 1..DominoRules.MAX_TOKEN
        else -> false
    }
    private fun turn(by: PlayerId, token: Long, move: Int): Boolean =
        DominoRoster.isValidPlayerId(by.raw) && token in 1..DominoRules.MAX_TOKEN && move in 0..DominoRules.MAX_MOVE
}
