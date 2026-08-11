package com.parlor.games.whodunit.domain.state

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import kotlinx.serialization.Serializable

/**
 * Whodunit canonical state is divided into public, per-player-private, and
 * host-only buckets. The projection policy strips the appropriate buckets for
 * each viewer before state crosses a trust boundary.
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
    /**
     * Monotonic epoch for the current role/character assignment.
     *
     * Reveal commands carry this value so a delayed command from before a
     * reroll or rematch cannot unlock, close, or acknowledge the replacement
     * dossier. Zero is reserved for states that have never had roles assigned;
     * the first assignment is generation one.
     */
    val roleAssignmentGeneration: Long = 0L,
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
     * Party Play readiness sets. Each acknowledgement-required phase has
     * exactly one set. The host's "advance" CTA is gated by
     * `PartyReadiness.isComplete(set, activeRoster)`. Defaults to empty
     * so a newly created phase starts with no acknowledgements.
     */
    val introAcknowledged: Set<PlayerId> = emptySet(),
    val briefingReady: Set<PlayerId> = emptySet(),
    val rolesViewed: Set<PlayerId> = emptySet(),

    /**
     * Players the host bridge has detected as transiently offline.
     * Submitted via `MarkPlayerDisconnected` / `MarkPlayerReconnected`.
     * A disconnect pauses the canonical game until every disconnected
     * player rejoins. Grace-period expiry ends the session; Whodunit never
     * removes a live session's player because every dossier is required.
     */
    val disconnectedPlayers: Set<PlayerId> = emptySet(),

    /**
     * Legacy snapshot compatibility for sessions saved by versions that
     * permitted continuing without a player. New reducers never add entries:
     * a missing player makes the current case impossible to continue safely.
     */
    val droppedPlayers: Set<PlayerId> = emptySet(),

    /**
     * The decided verdict, set when the reducer transitions to [WhodunitPhase.Reveal].
     * Part of public state because the Reveal screen exposes it to the whole table
     * by definition. Living in state means:
     *   - Peers receive the verdict in their authoritative player snapshot
     *     (no extra protocol surface is needed).
     *   - Snapshot resume restores the verdict for free.
     *   - The UI reads from a single source of truth instead of a side-channel
     *     event listener (which was prone to losing the verdict on resume).
     */
    val verdict: Verdict? = null,
)

/** Per-player private — visible only to the owning player. */
@Serializable
data class WhodunitPrivate(
    val role: PlayerRole,
    val characterId: CharacterId,
    /** Assigned, non-self suspects the killer may safely name. Empty for innocents. */
    val deflectionTargets: List<CharacterId> = emptyList(),
    val dossierUnlocked: Boolean = false,
    /** Legacy snapshot field. Current game version never opens a canonical review overlay. */
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

// VoteState lives in its own file to keep phase-specific invariants localized.

@Serializable
data class PublicTimerState(
    val timerId: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val paused: Boolean = false,
)
