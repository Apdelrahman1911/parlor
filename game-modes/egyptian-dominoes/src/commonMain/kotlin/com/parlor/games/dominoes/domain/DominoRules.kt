package com.parlor.games.dominoes.domain

import com.parlor.core.ids.PlayerId

object DominoRules {
    const val HAND_SIZE = 7
    private const val THREE_PLAYERS = 3
    private const val THREE_PLAYER_HAND_SIZE = 9
    const val MAX_HANDS = 64
    const val MAX_HISTORY = 8192
    const val MAX_TOKEN = 1_000_000_000L
    const val MAX_MOVE = 256

    /** Double-blank is the canonical 0–0 tile, not every tile containing a blank. */
    fun tiles(settings: DominoSettings, players: Int): List<DominoTile> =
        if (settings.variant == DominoVariant.Default && players == THREE_PLAYERS) DominoTile.Set.filter { it.id != 0 }
        else DominoTile.Set

    fun handSize(settings: DominoSettings, players: Int): Int =
        if (settings.variant == DominoVariant.Default && players == THREE_PLAYERS) THREE_PLAYER_HAND_SIZE else HAND_SIZE

    fun usesDraw(settings: DominoSettings, players: Int): Boolean = settings.variant == DominoVariant.Draw ||
        (settings.variant == DominoVariant.Default && players == DominoRoster.MIN_PLAYERS)

    fun initialStock(settings: DominoSettings, players: Int): Int = tiles(settings, players).size - players * handSize(settings, players)

    /** Works on an own-player projection; no opponent hand is needed by UI. */
    fun playableEnds(state: DominoState, playerId: PlayerId, tile: DominoTile): Set<DominoEnd> {
        val own = state.privatePerPlayer[playerId] ?: return emptySet()
        if (state.phase != DominoPhase.Playing || state.public.turn != playerId) return emptySet()
        if (state.public.disconnected.isNotEmpty() || tile !in own.hand) return emptySet()
        if (state.public.chain.isEmpty()) {
            return if (own.requiredOpening == null || own.requiredOpening == tile) setOf(DominoEnd.Right) else emptySet()
        }
        return buildSet {
            if (tile.contains(state.public.chain.first().left)) add(DominoEnd.Left)
            if (tile.contains(state.public.chain.last().right)) add(DominoEnd.Right)
        }
    }

    fun hasMove(state: DominoState, playerId: PlayerId): Boolean =
        state.privatePerPlayer[playerId]?.hand.orEmpty().any { playableEnds(state, playerId, it).isNotEmpty() }

    fun canDraw(state: DominoState, playerId: PlayerId): Boolean = isTurn(state, playerId) &&
        !hasMove(state, playerId) && usesDraw(state.public.settings, state.players.size) && state.public.stockCount > 0

    fun canPass(state: DominoState, playerId: PlayerId): Boolean = isTurn(state, playerId) &&
        !hasMove(state, playerId) && !canDraw(state, playerId)

    private fun isTurn(state: DominoState, id: PlayerId): Boolean = state.phase == DominoPhase.Playing &&
        state.public.turn == id && state.public.disconnected.isEmpty() && id in state.privatePerPlayer
}
