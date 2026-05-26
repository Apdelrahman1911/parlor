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

    /**
     * Players the host bridge has detected as transiently offline.
     * Submitted via `MarkPlayerDisconnected` / `MarkPlayerReconnected`.
     * Disconnected players are NOT auto-dropped; they still count
     * toward readiness invariants until the host explicitly
     * `ContinueWithoutPlayer`s them.
     */
    val disconnectedPlayers: Set<PlayerId> = emptySet(),

    /**
     * Players the host has explicitly chosen to continue without via
     * `ContinueWithoutPlayer`. Sticky for the session — only
     * `ReadmitPlayer` clears the slot, and only before the next host
     * phase advance. Dropped players are excluded from the active
     * roster everywhere: readiness invariants, vote ballots, all
     * SelfActor actions (authority + reducer enforce, see
     * `WhodunitActionAuthority.isAllowed`).
     */
    val droppedPlayers: Set<PlayerId> = emptySet(),

    /**
     * The decided verdict, set when the reducer transitions to [WhodunitPhase.Reveal].
     * Part of public state because the Reveal screen exposes it to the whole table
     * by definition. Living in state means:
     *   - Peers receive the verdict via [HostMessage.PublicStateSnapshot] (no
     *     extra protocol surface needed).
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
