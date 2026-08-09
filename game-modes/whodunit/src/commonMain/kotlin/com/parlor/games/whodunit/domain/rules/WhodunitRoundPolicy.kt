package com.parlor.games.whodunit.domain.rules

import com.parlor.games.whodunit.content.Round
import com.parlor.games.whodunit.content.RoundConfig
import com.parlor.games.whodunit.content.WhodunitCase
import kotlin.math.abs

/** Shared authored-round resolution for UI, reducer validation, and snapshot recovery. */
internal object WhodunitRoundPolicy {
    const val DEFAULT_DISCUSSION_SECONDS: Int = 180

    fun authoredRound(
        case: WhodunitCase,
        roundIndex: Int,
        playerCount: Int,
    ): Round? {
        if (roundIndex < 1 || case.roundConfigByPlayerCount.isEmpty()) return null
        val buckets = case.roundConfigByPlayerCount.entries
            .mapNotNull { (key, config) -> key.toIntOrNull()?.let { it to config } }
            .sortedBy { it.first }
        if (buckets.isEmpty()) return null

        buckets.firstOrNull { it.first == playerCount }
            ?.second
            ?.rounds
            ?.getOrNull(roundIndex - 1)
            ?.let { return it }

        return buckets
            .sortedWith(
                compareBy<Pair<Int, RoundConfig>> { abs(it.first - playerCount) }
                    .thenBy { it.first },
            )
            .firstNotNullOfOrNull { (_, config) -> config.rounds.getOrNull(roundIndex - 1) }
    }

    fun discussionSeconds(
        case: WhodunitCase,
        roundIndex: Int,
        playerCount: Int,
    ): Int = authoredRound(case, roundIndex, playerCount)
        ?.discussionSeconds
        ?.takeIf { it > 0 }
        ?: DEFAULT_DISCUSSION_SECONDS
}
