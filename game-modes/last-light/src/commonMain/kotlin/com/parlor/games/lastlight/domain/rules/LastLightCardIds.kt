package com.parlor.games.lastlight.domain.rules

/** IDs denote shuffled slots; they never encode the underlying rank. */
object LastLightCardIds {
    fun slot(id: String, round: Int): Int? {
        if (round !in 1..LastLightRules.MAX_ROUNDS || id.length > LastLightRules.MAX_CARD_ID_LENGTH) return null
        val prefix = "r$round-c"
        if (!id.startsWith(prefix)) return null
        val slot = id.removePrefix(prefix).toIntOrNull() ?: return null
        return slot.takeIf { it in 0 until LastLightRules.DECK_SIZE && id == "$prefix$it" }
    }

    fun isValid(id: String): Boolean = (1..LastLightRules.MAX_ROUNDS).any { slot(id, it) != null }
}
