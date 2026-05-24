package com.parlor.games.whodunit.domain.projection

import com.parlor.core.ids.PlayerId
import com.parlor.engine.projection.HostProjection
import com.parlor.engine.projection.PrivateProjection
import com.parlor.engine.projection.ProjectionPolicy
import com.parlor.engine.projection.PublicProjection
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitState

/**
 * Strips state per viewer per ARCHITECTURE.md §7.
 *
 * - `toPublic`: clears `privatePerPlayer` AND `hostOnly`. Also scrubs
 *   vote targets from `voteState.castSoFar` while voting is in progress
 *   (Wave 9H — voters are public, targets are not, until the tally
 *   resolves the vote into [VoteState.Resolved] / [VoteState.Tied]).
 * - `toPlayer(id)`: clears `hostOnly`; keeps only `privatePerPlayer[id]`.
 *   Also applies the same vote-target redaction.
 * - `toHost`: full state.
 */
object WhodunitProjectionPolicy : ProjectionPolicy<WhodunitState> {

    private val redactedHostOnly: WhodunitHostOnly = WhodunitHostOnly(
        killerId = PlayerId("redacted"),
        killerCharacterId = com.parlor.core.ids.CharacterId("redacted"),
        randomSeed = 0L,
        seatToCharacter = emptyMap(),
        redHerringTargets = emptyList(),
    )

    /**
     * Sentinel target written into [VoteState.Collecting.castSoFar] when
     * the projection redacts the value. Preserves the keys (who has
     * voted) but hides each individual choice.
     */
    val redactedVoteTarget: PlayerId = PlayerId("redacted")

    override fun toPublic(state: WhodunitState): PublicProjection<WhodunitState> =
        PublicProjection(
            state.copy(
                privatePerPlayer = emptyMap(),
                hostOnly = redactedHostOnly,
                public = state.public.copy(
                    voteState = redactVoteTargets(state.public.voteState),
                ),
            ),
        )

    override fun toPlayer(state: WhodunitState, playerId: PlayerId): PrivateProjection<WhodunitState> {
        val ownEntry = state.privatePerPlayer[playerId]
        val filtered: Map<PlayerId, WhodunitPrivate> = if (ownEntry != null) {
            mapOf(playerId to ownEntry)
        } else emptyMap()
        return PrivateProjection(
            state.copy(
                privatePerPlayer = filtered,
                hostOnly = redactedHostOnly,
                public = state.public.copy(
                    voteState = redactVoteTargets(state.public.voteState),
                ),
            ),
            playerId = playerId,
        )
    }

    override fun toHost(state: WhodunitState): HostProjection<WhodunitState> =
        HostProjection(state)

    /**
     * During `Collecting`, replace every `castSoFar` value with the
     * redacted-target sentinel. Voter keys stay; target choices vanish.
     * `Resolved` / `Tied` / `NoResolution` are returned unchanged — at
     * those phases the tally is already public, so target disclosure is
     * intentional.
     */
    private fun redactVoteTargets(vote: VoteState): VoteState = when (vote) {
        is VoteState.Collecting -> {
            if (vote.castSoFar.isEmpty()) vote
            else vote.copy(castSoFar = vote.castSoFar.mapValues { redactedVoteTarget })
        }
        else -> vote
    }
}
