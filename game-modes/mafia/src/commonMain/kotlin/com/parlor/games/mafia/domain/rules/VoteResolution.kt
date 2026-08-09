package com.parlor.games.mafia.domain.rules

import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.TieBehavior
import com.parlor.games.mafia.domain.state.VoteOutcome

/**
 * Pure day-vote resolution. Tally → outcome (Eliminated / SkippedDueToTie /
 * MaxRevotesReached / AllAbstained) plus the candidates for the next revote
 * round if one is needed.
 */
object VoteResolution {

    data class Inputs(
        val casts: Map<PlayerId, PlayerId>,
        val abstained: Set<PlayerId>,
        val ballot: List<PlayerId>,
        val candidates: List<PlayerId>,
        val revoteRound: Int,
    )

    sealed interface Outcome {
        val tally: Map<PlayerId, Int>

        data class Resolved(val eliminated: PlayerId, override val tally: Map<PlayerId, Int>) : Outcome
        data class Tied(
            val tied: List<PlayerId>,
            val nextRoundCandidates: List<PlayerId>,
            override val tally: Map<PlayerId, Int>,
        ) : Outcome

        data class Skipped(val reason: VoteOutcome, override val tally: Map<PlayerId, Int>) : Outcome
    }

    fun resolve(inputs: Inputs, settings: MafiaSettings): Outcome {
        val tally = inputs.casts.values.groupingBy { it }.eachCount()
        if (tally.isEmpty()) return Outcome.Skipped(VoteOutcome.AllAbstained, tally)

        val maxCount = tally.values.max()
        val top = tally.filterValues { it == maxCount }.keys.toList()

        if (top.size == 1) return Outcome.Resolved(top.first(), tally)

        // Tied.
        val canRevote = inputs.revoteRound < settings.maxRevotes
        return when {
            settings.voteTieBehavior == TieBehavior.SKIP_ELIMINATION ->
                Outcome.Skipped(VoteOutcome.SkippedDueToTie, tally)
            !canRevote -> Outcome.Skipped(VoteOutcome.MaxRevotesReached, tally)
            else -> {
                val nextCandidates = when (settings.voteTieBehavior) {
                    TieBehavior.REVOTE_TIED_ONLY -> top
                    TieBehavior.REVOTE_ALL -> inputs.candidates
                    TieBehavior.SKIP_ELIMINATION -> emptyList() // unreachable
                }
                Outcome.Tied(top, nextCandidates, tally)
            }
        }
    }
}
