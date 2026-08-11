package com.parlor.games.whodunit.domain.rules

import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.ModeId
import com.parlor.core.random.RandomSource
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.WhodunitCase

/**
 * Authoritative deterministic clue-selection policy.
 *
 * Pool order and seed derivation are persistence-sensitive. The reducer and
 * restored-state validator share this implementation so they cannot drift.
 */
internal object WhodunitCluePolicy {
    fun select(
        case: WhodunitCase,
        killerCharacterId: CharacterId,
        modeId: ModeId,
        playerCount: Int,
        randomSeed: Long,
        roundIndex: Int,
        drawnClueIds: Set<ClueId>,
    ): Clue? {
        val random = RandomSource.seeded(randomSeed xor roundIndex.toLong().shl(ROUND_SEED_SHIFT_BITS))
        val candidates = eligibleCandidates(
            case = case,
            killerCharacterId = killerCharacterId,
            modeId = modeId,
            playerCount = playerCount,
            roundIndex = roundIndex,
            drawnClueIds = drawnClueIds,
        )
        return candidates.takeIf { it.isNotEmpty() }?.let(random::pick)
    }

    /**
     * Exact candidate set from which [select] may draw for this state.
     * Snapshot validation shares this policy so a privacy-safe peer projection
     * cannot claim that a defensive fallback clue was used while preferred
     * authored final evidence was still available.
     */
    fun eligibleCandidates(
        case: WhodunitCase,
        killerCharacterId: CharacterId,
        modeId: ModeId,
        playerCount: Int,
        roundIndex: Int,
        drawnClueIds: Set<ClueId>,
    ): List<Clue> {
        val pools = case.cluePools
        val killerId = killerCharacterId.raw
        val lastRound = WhodunitRules.maximumRoundCount(modeId, playerCount)
            ?.let { roundIndex >= it }
            ?: return emptyList()

        fun List<Clue>.forCurrentMode(): List<Clue> = filter { clue ->
            clue.appliesToModes?.let { modes -> modeId.raw in modes } != false
        }

        fun List<Clue>.undrawn(): List<Clue> = filterNot { ClueId(it.id) in drawnClueIds }

        return when {
            lastRound -> {
                val preferred = pools.finalStrong[killerId].orEmpty().forCurrentMode().undrawn()
                if (preferred.isNotEmpty()) {
                    preferred
                } else {
                    (
                        pools.killerPointing[killerId].orEmpty() +
                            pools.contradiction[killerId].orEmpty() +
                            pools.redHerring[killerId].orEmpty() +
                            pools.publicUniversal
                        ).forCurrentMode().undrawn()
                }
            }
            roundIndex == 1 ->
                (pools.publicUniversal + pools.killerPointing[killerId].orEmpty())
                    .forCurrentMode()
                    .undrawn()
            else -> (
                pools.killerPointing[killerId].orEmpty() +
                    pools.contradiction[killerId].orEmpty() +
                    pools.redHerring[killerId].orEmpty()
                ).forCurrentMode().undrawn()
        }
    }

    private const val ROUND_SEED_SHIFT_BITS = 8
}
