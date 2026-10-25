package com.parlor.games.wordimpostor.domain

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.phase.GamePhase
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import kotlinx.serialization.Serializable

@Serializable
data class WordImpostorSettings(val topic: WordTopic = WordTopic.Food, val impostors: Int = 1, val rounds: Int = 3) {
    init { require(impostors in 1..3 && rounds in ROUND_COUNTS) }
    val caseId: CaseId get() = CaseId("${topic.id}-impostors-$impostors-rounds-$rounds")
    fun supports(players: Int): Boolean = players in 3..12 && impostors * TEAM_FACTOR < players
    companion object {
        private const val TEAM_FACTOR = 2
        val ROUND_COUNTS = listOf(1, 3, 5)
        fun fromCaseId(value: String): WordImpostorSettings? = WordTopic.entries.firstNotNullOfOrNull { topic ->
            (1..3).firstNotNullOfOrNull { impostors ->
                ROUND_COUNTS.firstOrNull { value == "${topic.id}-impostors-$impostors-rounds-$it" }
                    ?.let { WordImpostorSettings(topic, impostors, it) }
            }
        }
    }
}

@Serializable
enum class WordRole { Ordinary, Impostor }

@Serializable
enum class WordImpostorPhase(override val id: String) : GamePhase {
    Reveal("reveal"), Questions("questions"), Discussion("discussion"), Voting("voting"), Guessing("guessing"),
    RoundResult("round-result"), MatchResult("match-result"), Aborted("aborted"),
}

@Serializable
data class QuestionPair(val asker: PlayerId, val answerer: PlayerId)

@Serializable
data class WordRoundResult(
    val wordId: String,
    val impostors: Set<PlayerId>,
    val identified: Set<PlayerId>,
    /** Ordinary players whose own sealed vote identified any impostor; only revealed at the final result. */
    val correctVoters: Set<PlayerId>,
    val guesses: Map<PlayerId, String>,
    val awarded: Map<PlayerId, Int>,
)

@Serializable
data class WordImpostorPublic(
    val settings: WordImpostorSettings,
    val round: Int,
    val token: Long,
    val ready: Set<PlayerId>,
    val interactions: List<QuestionPair>,
    val questionIndex: Int,
    val voted: Set<PlayerId>,
    val voteCounts: Map<PlayerId, Int>?,
    /** Count only: publishing the identities of guessers would expose secret roles. */
    val guessCount: Int,
    val scores: Map<PlayerId, Int>,
    val result: WordRoundResult? = null,
    val disconnected: Set<PlayerId> = emptySet(),
)

@Serializable
data class WordImpostorPrivate(
    val role: WordRole? = null,
    val wordId: String? = null,
    val teammates: Set<PlayerId> = emptySet(),
    val questionId: String? = null,
    val vote: PlayerId? = null,
    val choices: List<String> = emptyList(),
    val guess: String? = null,
)

@Serializable
data class WordImpostorHostOnly(
    val seed: Long? = null,
    val firstToken: Long = 0L,
    val wordId: String? = null,
    val impostors: Set<PlayerId> = emptySet(),
    val questions: List<String> = emptyList(),
    val choices: Map<PlayerId, List<String>> = emptyMap(),
    val votes: Map<PlayerId, PlayerId> = emptyMap(),
    val guesses: Map<PlayerId, String> = emptyMap(),
    val history: List<WordImpostorAction> = emptyList(),
)

@Serializable
data class WordImpostorState(
    val public: WordImpostorPublic,
    val privatePerPlayer: Map<PlayerId, WordImpostorPrivate>,
    val hostOnly: WordImpostorHostOnly,
    override val phase: WordImpostorPhase,
    override val players: List<Player>,
) : GameState
