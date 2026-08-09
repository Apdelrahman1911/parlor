package com.parlor.games.whodunit.domain.rules

import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.ModeId
import com.parlor.core.random.RandomSource
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.CluePools
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
        val pools = case.cluePools
        val killerId = killerCharacterId.raw
        val random = RandomSource.seeded(randomSeed xor roundIndex.toLong().shl(8))
        val lastRound = WhodunitRules.maximumRoundCount(modeId, playerCount)
            ?.let { roundIndex >= it }
            ?: return null

        fun List<Clue>.forCurrentMode(): List<Clue> = filter { clue ->
            clue.appliesToModes?.let { modes -> modeId.raw in modes } != false
        }

        return when {
            lastRound -> selectLateGameClue(
                pools = pools,
                killerCharacterId = killerId,
                modeId = modeId.raw,
                drawnClueIds = drawnClueIds,
                random = random,
            )
            roundIndex == 1 -> selectFromPool(
                pool = (pools.publicUniversal + pools.killerPointing[killerId].orEmpty())
                    .forCurrentMode(),
                drawnClueIds = drawnClueIds,
                random = random,
            )
            else -> selectFromPool(
                pool = (
                    pools.killerPointing[killerId].orEmpty() +
                        pools.contradiction[killerId].orEmpty() +
                        pools.redHerring[killerId].orEmpty()
                    ).forCurrentMode(),
                drawnClueIds = drawnClueIds,
                random = random,
            )
        }
    }

    private fun selectFromPool(
        pool: List<Clue>,
        drawnClueIds: Set<ClueId>,
        random: RandomSource,
    ): Clue? {
        val available = pool.filterNot { ClueId(it.id) in drawnClueIds }
        return available.takeIf { it.isNotEmpty() }?.let(random::pick)
    }

    /** Prefer undrawn final evidence; broader pools are defensive legacy fallback only. */
    private fun selectLateGameClue(
        pools: CluePools,
        killerCharacterId: String,
        modeId: String,
        drawnClueIds: Set<ClueId>,
        random: RandomSource,
    ): Clue? {
        fun List<Clue>.forCurrentMode(): List<Clue> = filter { clue ->
            clue.appliesToModes?.let { modes -> modeId in modes } != false
        }

        val finalStrong = pools.finalStrong[killerCharacterId].orEmpty().forCurrentMode()
        val killerPointing = pools.killerPointing[killerCharacterId].orEmpty().forCurrentMode()
        val contradiction = pools.contradiction[killerCharacterId].orEmpty().forCurrentMode()
        val redHerring = pools.redHerring[killerCharacterId].orEmpty().forCurrentMode()
        val publicUniversal = pools.publicUniversal.forCurrentMode()

        val undrawnFinalStrong = finalStrong.filterNot { ClueId(it.id) in drawnClueIds }
        if (undrawnFinalStrong.isNotEmpty()) return random.pick(undrawnFinalStrong)

        val fallback =
            killerPointing.filterNot { ClueId(it.id) in drawnClueIds } +
                contradiction.filterNot { ClueId(it.id) in drawnClueIds } +
                redHerring.filterNot { ClueId(it.id) in drawnClueIds } +
                publicUniversal.filterNot { ClueId(it.id) in drawnClueIds }
        return fallback.takeIf { it.isNotEmpty() }?.let(random::pick)
    }
}
