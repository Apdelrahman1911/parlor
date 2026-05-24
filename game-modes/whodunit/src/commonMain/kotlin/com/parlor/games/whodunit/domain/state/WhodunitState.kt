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
    /**
     * Session-wide pause. Distinct from `timer.paused`, which only affects the
     * discussion ticker. While `paused == true` the reducer ignores
     * `TimerTicked` and the UI renders a pause overlay on top of whatever
     * phase the players were in. The flag is part of public state so it
     * survives serialization — a snapshot persisted while paused resumes
     * paused.
     */
    val paused: Boolean = false,

    /**
     * Party Play readiness sets — Wave 9H. Each ack-required phase has
     * exactly one set. The host's "advance" CTA is gated by
     * `PartyReadiness.isComplete(set, activeRoster)`. Defaults to empty
     * so deserialised pre-9H snapshots fall through gracefully (no one
     * has acked yet — UI re-prompts on resume).
     */
    val introAcknowledged: Set<PlayerId> = emptySet(),
    val briefingReady: Set<PlayerId> = emptySet(),
    val rolesViewed: Set<PlayerId> = emptySet(),
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
