package com.parlor.games.whodunit.projection

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ModeId
import com.parlor.core.ids.PlayerId
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import kotlin.test.Test

/**
 * Wave 9H-4: while a vote is `Collecting`, the public projection scrubs
 * the target column of `castSoFar`. Voter ids (the keys) survive so the
 * UI can render "3 of 5 voted"; each individual choice is replaced with
 * the redacted sentinel. Once the vote resolves into `Resolved` /
 * `Tied` / `NoResolution`, the tally is public and the projection
 * passes through unchanged.
 */
class VoteRedactionTest {

    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val cara = PlayerId("cara")

    private fun baseState(vote: VoteState): WhodunitState = WhodunitState(
        public = WhodunitPublic(
            caseId = CaseId("c"),
            modeId = ModeId("m"),
            playersAtTable = listOf(
                Player(alice, "Alice", seat = 0),
                Player(bob, "Bob", seat = 1),
                Player(cara, "Cara", seat = 2),
            ),
            voteState = vote,
        ),
        privatePerPlayer = emptyMap(),
        hostOnly = WhodunitHostOnly(
            killerId = alice,
            killerCharacterId = CharacterId("X"),
            randomSeed = 1L,
            seatToCharacter = emptyMap(),
            redHerringTargets = emptyList(),
        ),
        phase = WhodunitPhase.FinalVote,
        players = listOf(
            Player(alice, "Alice", seat = 0),
            Player(bob, "Bob", seat = 1),
            Player(cara, "Cara", seat = 2),
        ),
    )

    @Test
    fun collecting_vote_redacts_target_values_in_public_projection() {
        val state = baseState(
            VoteState.Collecting(
                isElimination = false,
                ballotPlayerIds = listOf(alice, bob, cara),
                castSoFar = mapOf(alice to bob, cara to alice),
            ),
        )
        val publicVote = (WhodunitProjectionPolicy.toPublic(state).state.public.voteState as VoteState.Collecting)
        // Voters preserved.
        assertThat(publicVote.castSoFar.keys).containsExactlyInAnyOrder(alice, cara)
        // Every target redacted.
        for ((_, target) in publicVote.castSoFar) {
            assertThat(target).isEqualTo(WhodunitProjectionPolicy.redactedVoteTarget)
        }
    }

    @Test
    fun collecting_vote_redacts_targets_in_private_projection_too() {
        val state = baseState(
            VoteState.Collecting(
                isElimination = false,
                ballotPlayerIds = listOf(alice, bob),
                castSoFar = mapOf(alice to bob),
            ),
        )
        val playerVote = (WhodunitProjectionPolicy.toPlayer(state, alice).state.public.voteState as VoteState.Collecting)
        assertThat(playerVote.castSoFar[alice]).isEqualTo(WhodunitProjectionPolicy.redactedVoteTarget)
    }

    @Test
    fun resolved_vote_passes_through_unchanged() {
        val state = baseState(VoteState.Resolved(accusedPlayerId = bob, wasKiller = false))
        val publicVote = WhodunitProjectionPolicy.toPublic(state).state.public.voteState
        assertThat(publicVote).isInstanceOf(VoteState.Resolved::class)
        assertThat((publicVote as VoteState.Resolved).accusedPlayerId).isEqualTo(bob)
    }

    @Test
    fun tied_vote_passes_through_unchanged() {
        val state = baseState(VoteState.Tied(tiedPlayerIds = listOf(alice, bob)))
        val publicVote = WhodunitProjectionPolicy.toPublic(state).state.public.voteState
        assertThat(publicVote).isInstanceOf(VoteState.Tied::class)
        assertThat((publicVote as VoteState.Tied).tiedPlayerIds).containsExactlyInAnyOrder(alice, bob)
    }

    @Test
    fun empty_castSoFar_in_collecting_returns_same_instance() {
        val collecting = VoteState.Collecting(
            isElimination = false,
            ballotPlayerIds = listOf(alice, bob, cara),
        )
        val state = baseState(collecting)
        val publicVote = WhodunitProjectionPolicy.toPublic(state).state.public.voteState
        // mapValues on an empty map is a no-op; the projection short-circuits
        // for clarity. Either way the visible output is empty cast set.
        assertThat((publicVote as VoteState.Collecting).castSoFar).isEqualTo(emptyMap<PlayerId, PlayerId>())
    }

    @Test
    fun host_projection_does_not_redact_targets() {
        val state = baseState(
            VoteState.Collecting(
                isElimination = false,
                ballotPlayerIds = listOf(alice, bob, cara),
                castSoFar = mapOf(alice to bob),
            ),
        )
        val hostVote = (WhodunitProjectionPolicy.toHost(state).state.public.voteState as VoteState.Collecting)
        // Host has the canonical state with real targets — projection is
        // identity. UI is the layer responsible for not surfacing the
        // target column on the host's screen during Collecting.
        assertThat(hostVote.castSoFar[alice]).isEqualTo(bob)
    }
}
