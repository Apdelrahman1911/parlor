package com.parlor.games.mafia.domain.state

import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.GameState
import com.parlor.engine.state.Player
import com.parlor.games.mafia.domain.phase.MafiaPhase
import com.parlor.games.mafia.domain.settings.MafiaSettings
import kotlinx.serialization.Serializable

/**
 * Mafia full state — three buckets per ARCHITECTURE.md §7. The reducer only
 * mutates buckets it has authority to mutate; [com.parlor.games.mafia.domain.projection.MafiaProjectionPolicy]
 * strips the appropriate buckets per viewer.
 *
 * Privacy is enforced HERE, not in the UI. Living-player roles never appear
 * in [MafiaPublic]; Mafia coordination and Detective results never appear
 * outside their owning player's [MafiaPrivate]; the full role map is in
 * [MafiaHostOnly] and is stripped by both `toPublic` and `toPlayer`.
 */
@Serializable
data class MafiaState(
    val public: MafiaPublic,
    val privatePerPlayer: Map<PlayerId, MafiaPrivate>,
    val hostOnly: MafiaHostOnly,
    override val phase: MafiaPhase,
    override val players: List<Player>,
) : GameState

/** Applies host-only retention policy at persistence/transport boundaries. */
internal fun MafiaState.withBoundedHostLogs(): MafiaState {
    val bounded = hostOnly.withBoundedLogs()
    return if (bounded === hostOnly) this else copy(hostOnly = bounded)
}

@Serializable
data class MafiaPublic(
    val settings: MafiaSettings,
    val day: Int = 0,
    /**
     * Public view of each seat. While alive, `role == null` — roles are
     * never disclosed publicly during play. A death may reveal its role when
     * `settings.revealRoleOnDeath` is enabled; PostGame reveals every role.
     */
    val roster: List<PublicPlayerSlot>,
    val lastNight: NightAnnouncement? = null,
    val lastVote: VoteAnnouncement? = null,
    val activeVote: ActiveVote? = null,
    val winner: Team? = null,
    /**
     * Players the host bridge has flagged as transiently offline. The game is
     * paused while this set is non-empty. If grace expires, the host's
     * `ContinueWithoutPlayer` transition ends the game instead of continuing
     * with a reduced roster.
     */
    val disconnectedPlayers: Set<PlayerId> = emptySet(),
    val droppedPlayers: Set<PlayerId> = emptySet(),
)

@Serializable
data class PublicPlayerSlot(
    val playerId: PlayerId,
    val displayName: String,
    val seat: Int,
    val alive: Boolean = true,
    /** Non-null after an enabled death reveal, and for every seat in PostGame. */
    val revealedRole: Role? = null,
)

@Serializable
data class NightAnnouncement(
    val day: Int,
    val killedPlayerId: PlayerId?,
    /** True iff the Mafia targeted someone but the Doctor saved them. */
    val wasSaved: Boolean,
)

@Serializable
data class VoteAnnouncement(
    val day: Int,
    val tally: Map<PlayerId, Int>,
    val eliminatedPlayerId: PlayerId?,
    val outcome: VoteOutcome,
)

@Serializable
enum class VoteOutcome {
    /** A single player was eliminated. */
    Eliminated,

    /** The vote was tied and `voteTieBehavior` resulted in no elimination. */
    SkippedDueToTie,

    /** The maximum revote count was reached without resolution; no elimination. */
    MaxRevotesReached,

    /** No one voted; no elimination. */
    AllAbstained,
}

@Serializable
data class ActiveVote(
    val day: Int,
    val revoteRound: Int,
    /** The valid targets for this vote round. Reduced on subsequent revotes if `REVOTE_TIED_ONLY`. */
    val candidates: List<PlayerId>,
    /** Living players permitted to cast this round. */
    val ballot: List<PlayerId>,
    /** Casts so far. Targets are PUBLIC in this state — voting in Mafia is open by spec. */
    val castSoFar: Map<PlayerId, PlayerId> = emptyMap(),
    val abstained: Set<PlayerId> = emptySet(),
)

@Serializable
data class MafiaPrivate(
    val role: Role,
    val team: Team,
    /** Mafia members know each other; Town has empty set. */
    val knownTeammates: Set<PlayerId> = emptySet(),
    /** Non-null ONLY for living Mafia. Replicated identically across all Mafia members. */
    val mafiaCoordination: MafiaCoordinationSnapshot? = null,
    /** Detective's most recent inspection result; cleared at start of next night. */
    val pendingDetectiveResult: DetectiveResult? = null,
    /** Civilian-only private pick. No game effect. */
    val lastSuspicion: PlayerId? = null,
    /**
     * Doctor-only record of the previous night's effective protection.
     * Kept in the Doctor's private bucket so remote and local UIs can exclude
     * an illegal consecutive target without exposing it publicly.
     */
    val previousDoctorProtect: PlayerId? = null,
    /**
     * Own-only night submission (Mafia kill vote, Doctor protect, Detective inspect,
     * Civilian suspicion). Cleared on night resolution.
     */
    val pendingNightChoice: PlayerId? = null,
    /** True after the player has acknowledged viewing their role this round. */
    val roleAcknowledged: Boolean = false,
    /** True after the player has acknowledged the last night announcement. */
    val nightAcknowledged: Boolean = false,
    /** True after the player has acknowledged the last vote announcement. */
    val voteAcknowledged: Boolean = false,
    /** True after the Detective has viewed their private result this night. */
    val detectiveResultAcknowledged: Boolean = false,
    /**
     * True once this player has submitted their night action THIS night —
     * including a Civilian suspicion and including a Skip (null target), neither
     * of which sets [pendingNightChoice]. The night-action UI gates the
     * "submitted / waiting" screen on this flag (not pendingNightChoice), so
     * Civilians and skippers stop being able to resubmit and the host's
     * ResolveNight unblocks. Cleared on night resolution / mafia revote.
     * See PROBLEMS_PARLOR.md → mafia-ui-002.
     */
    val nightChoiceSubmitted: Boolean = false,
)

/**
 * Shared Mafia coordination snapshot. Replicated identically into every
 * living Mafia member's [MafiaPrivate.mafiaCoordination] field by the
 * reducer; Town members always have `null`. Standard `toPlayer(id)`
 * projection delivers it correctly — no transport plumbing needed.
 */
@Serializable
data class MafiaCoordinationSnapshot(
    val round: Int,
    /** What each Mafia member has submitted this round. */
    val submissions: Map<PlayerId, PlayerId> = emptyMap(),
    /** Anonymized tally from the previous round, shown on round 2. */
    val previousRoundTally: Map<PlayerId, Int>? = null,
)

@Serializable
data class DetectiveResult(
    val day: Int,
    val target: PlayerId,
    val seesAs: DetectiveSeesAs,
)

@Serializable
data class MafiaHostOnly(
    /** Full role map — NEVER projected. */
    val fullRoleMap: Map<PlayerId, Role>,
    val randomSeed: Long,
    val nightLog: List<NightResolutionRecord> = emptyList(),
    val voteLog: List<VoteRoundRecord> = emptyList(),
) {
    companion object {
        /**
         * Per-log serialized retention ceiling. A record is kept for the most
         * recent 128 resolved game days, which is well beyond a normal Mafia
         * session while placing a fixed upper bound on snapshots. Oldest
         * records are discarded first; these lists are diagnostics, not the
         * authoritative source for current game state.
         */
        const val MAX_SERIALIZED_LOG_ENTRIES = 128
    }

    internal fun recordNight(record: NightResolutionRecord): MafiaHostOnly =
        copy(nightLog = nightLog.appendBounded(record))

    internal fun recordVote(record: VoteRoundRecord): MafiaHostOnly =
        copy(voteLog = voteLog.appendBounded(record))

    /** Normalizes restored pre-bound snapshots before they are retained or re-encoded. */
    internal fun withBoundedLogs(): MafiaHostOnly {
        if (
            nightLog.size <= MAX_SERIALIZED_LOG_ENTRIES &&
            voteLog.size <= MAX_SERIALIZED_LOG_ENTRIES
        ) {
            return this
        }
        val boundedNight = nightLog.takeLast(MAX_SERIALIZED_LOG_ENTRIES)
        val boundedVote = voteLog.takeLast(MAX_SERIALIZED_LOG_ENTRIES)
        return copy(nightLog = boundedNight, voteLog = boundedVote)
    }

    private fun <T> List<T>.appendBounded(value: T): List<T> {
        val existing = this
        val retainedCount = minOf(existing.size, MAX_SERIALIZED_LOG_ENTRIES - 1)
        return buildList(retainedCount + 1) {
            if (retainedCount > 0) {
                addAll(existing.subList(existing.size - retainedCount, existing.size))
            }
            add(value)
        }
    }
}

@Serializable
data class NightResolutionRecord(
    val day: Int,
    val mafiaTarget: PlayerId?,
    val mafiaTargetTied: Boolean,
    val doctorProtect: PlayerId?,
    val detectiveInspect: PlayerId?,
    val detectiveResult: DetectiveSeesAs?,
    val killedPlayerId: PlayerId?,
)

@Serializable
data class VoteRoundRecord(
    val day: Int,
    val revoteRound: Int,
    val tally: Map<PlayerId, Int>,
    val eliminatedPlayerId: PlayerId?,
)
