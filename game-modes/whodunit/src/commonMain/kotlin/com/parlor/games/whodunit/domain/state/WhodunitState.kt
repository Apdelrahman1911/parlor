package com.parlor.games.whodunit.domain.state

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import kotlinx.serialization.Serializable

/**
 * Whodunit's full state. Three buckets per ARCHITECTURE.md §7. The reducer
 * only mutates buckets it has the authority to mutate; the projection policy
 * strips the appropriate buckets per viewer.
 */
@Serializable
data class WhodunitState(
    val public: WhodunitPublic,
    val privatePerPlayer: Map<PlayerId, WhodunitPrivate>,
    val hostOnly: WhodunitHostOnly,
    override val phase: WhodunitPhase,
    override val players: List<Player>,
) : GameState

/** Public — visible to everyone in the room. */
@Serializable
data class WhodunitPublic(
    val caseId: CaseId,
    val modeId: ModeId,
    val playersAtTable: List<Player>,
    val eliminatedPlayers: List<PlayerId> = emptyList(),
    val currentRound: Int = 0,
    val revealedClues: List<RevealedClue> = emptyList(),
    val voteState: VoteState = VoteState.Idle,
    val briefingCardIndex: Int = 0,
    val timer: PublicTimerState? = null,
)

/** Per-player private — visible only to the owning player. */
@Serializable
data class WhodunitPrivate(
    val role: PlayerRole,
    val characterId: CharacterId,
    val dossierUnlocked: Boolean = false,
    val privateReviewOpen: Boolean = false,
)

/** Host-only — never transmitted to peers; never logged. */
@Serializable
data class WhodunitHostOnly(
    val killerId: PlayerId,
    val killerCharacterId: CharacterId,
    val randomSeed: Long,
    val seatToCharacter: Map<PlayerId, CharacterId>,
    val redHerringTargets: List<CharacterId>,
    val drawnClueIds: Set<ClueId> = emptySet(),
)

@Serializable
enum class PlayerRole { Innocent, Killer }

@Serializable
data class RevealedClue(
    val id: ClueId,
    val text: String,
    val roundIndex: Int,
)

// VoteState lives in its own file (Phase 5 elaboration).

@Serializable
data class PublicTimerState(
    val timerId: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val paused: Boolean = false,
)
