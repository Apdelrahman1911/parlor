package com.parlor.games.dominoes.domain

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.phase.GamePhase
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import kotlinx.serialization.Serializable

/** A double-six tile has one canonical identity, independent of table orientation. */
@Serializable
data class DominoTile(val low: Int, val high: Int) {
    init { require(low in 0..6 && high in low..6) { "Invalid double-six tile" } }
    val id: Int get() = low * SUIT_COUNT + high
    val pips: Int get() = low + high
    val isDouble: Boolean get() = low == high
    fun contains(pips: Int): Boolean = low == pips || high == pips

    companion object {
        private const val SUIT_COUNT = 7
        val Set: List<DominoTile> = (0..6).flatMap { low -> (low..6).map { DominoTile(low, it) } }
        fun byId(id: Int): DominoTile? = Set.firstOrNull { it.id == id }
    }
}

@Serializable
enum class DominoVariant { Default, Draw, Block }

@Serializable
enum class DominoCompetition { Individual, Teams }

@Serializable
data class DominoSettings(
    val variant: DominoVariant = DominoVariant.Default,
    val target: Int = 101,
    val competition: DominoCompetition = DominoCompetition.Individual,
) {
    init {
        require(target in TARGETS) { "Unsupported domino target" }
        require(competition != DominoCompetition.Teams || variant == DominoVariant.Default) { "Teams require Default mode" }
    }
    val caseId: CaseId get() = CaseId("${variant.name.lowercase()}-$target-${competition.name.lowercase()}")
    fun supports(players: Int): Boolean = players in DominoRoster.MIN_PLAYERS..DominoRoster.MAX_PLAYERS &&
        (competition != DominoCompetition.Teams || players == DominoRoster.MAX_PLAYERS)

    companion object {
        val TARGETS = listOf(51, 101, 151)
        fun fromCaseId(value: String): DominoSettings? = DominoVariant.entries.firstNotNullOfOrNull { variant ->
            DominoCompetition.entries.firstNotNullOfOrNull { competition ->
                if (competition == DominoCompetition.Teams && variant != DominoVariant.Default) null else
                    TARGETS.firstOrNull { value == "${variant.name.lowercase()}-$it-${competition.name.lowercase()}" }
                        ?.let { DominoSettings(variant, it, competition) }
            }
        }
    }
}

@Serializable
enum class DominoPhase(override val id: String) : GamePhase {
    Playing("playing"), RoundResult("round-result"), MatchResult("match-result"), Aborted("aborted"),
}

@Serializable
enum class DominoEnd { Left, Right }

@Serializable
data class PlacedDomino(val tile: DominoTile, val left: Int, val right: Int, val by: PlayerId, val sequence: Int)

@Serializable
data class DominoRoundResult(
    val winner: PlayerId?,
    val blocked: Boolean,
    val remainingPips: Map<PlayerId, Int>,
    val points: Int,
)

@Serializable
data class DominoPublic(
    val settings: DominoSettings,
    val round: Int,
    val token: Long,
    val move: Int,
    val turn: PlayerId?,
    val chain: List<PlacedDomino>,
    val handCounts: Map<PlayerId, Int>,
    val stockCount: Int,
    val consecutivePasses: Int,
    /** One value per scoring side: each player, or the first seat of each opposite-seat partnership. */
    val scores: Map<PlayerId, Int>,
    val result: DominoRoundResult? = null,
    val matchWinners: Set<PlayerId> = emptySet(),
    val disconnected: Set<PlayerId> = emptySet(),
)

@Serializable
data class DominoPrivate(val hand: List<DominoTile> = emptyList(), val requiredOpening: DominoTile? = null)

@Serializable
data class DominoHostOnly(
    val seed: Long? = null,
    val firstToken: Long = 0L,
    val stock: List<DominoTile> = emptyList(),
    val requiredOpening: DominoTile? = null,
    val leader: PlayerId? = null,
    /** Bounded canonical replay proof; never a wire or UI field. */
    val history: List<DominoAction> = emptyList(),
)

@Serializable
data class DominoState(
    val public: DominoPublic,
    val privatePerPlayer: Map<PlayerId, DominoPrivate>,
    val hostOnly: DominoHostOnly,
    override val phase: DominoPhase,
    override val players: List<Player>,
) : GameState
