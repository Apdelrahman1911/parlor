package com.parlor.games.ghamza.domain

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.phase.GamePhase
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import kotlinx.serialization.Serializable

@Serializable
data class GhamzaSettings(val attempts: Int = 1, val rounds: Int = 3) {
    init { require(attempts in 1..3 && rounds in ROUND_COUNTS) }
    val caseId: CaseId get() = CaseId("attempts-$attempts-rounds-$rounds")
    companion object {
        val ROUND_COUNTS = listOf(1, 3, 5)
        fun fromCaseId(value: String): GhamzaSettings? = (1..3).firstNotNullOfOrNull { attempts ->
            ROUND_COUNTS.firstOrNull { value == "attempts-$attempts-rounds-$it" }?.let { GhamzaSettings(attempts, it) }
        }
    }
}

@Serializable
enum class GhamzaRole { Winker, Guest }

@Serializable
enum class GhamzaPhase(override val id: String) : GamePhase {
    Reveal("reveal"), Social("social"), FinalGuess("final-guess"),
    RoundResult("round-result"), MatchResult("match-result"), Aborted("aborted"),
}

@Serializable
data class WinkReport(val player: PlayerId, val attempt: Int, val number: Int)

@Serializable
data class GhamzaResult(val winker: PlayerId, val guesser: PlayerId, val guessed: PlayerId, val winner: PlayerId)

@Serializable
data class GhamzaPublic(
    val settings: GhamzaSettings,
    val round: Int,
    val token: Long,
    val ready: Set<PlayerId>,
    val reports: Map<PlayerId, Int>,
    val recentReports: List<WinkReport>,
    val finalGuesser: PlayerId?,
    val scores: Map<PlayerId, Int>,
    val result: GhamzaResult? = null,
    val disconnected: Set<PlayerId> = emptySet(),
)

@Serializable
data class GhamzaPrivate(val role: GhamzaRole? = null)

@Serializable
data class GhamzaHostOnly(
    val seed: Long? = null,
    val firstToken: Long = 0L,
    val winker: PlayerId? = null,
    val history: List<GhamzaAction> = emptyList(),
)

@Serializable
data class GhamzaState(
    val public: GhamzaPublic,
    val privatePerPlayer: Map<PlayerId, GhamzaPrivate>,
    val hostOnly: GhamzaHostOnly,
    override val phase: GhamzaPhase,
    override val players: List<Player>,
) : GameState
