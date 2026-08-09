package com.parlor.games.mafia.domain.rules

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.TieBehavior
import com.parlor.games.mafia.domain.state.VoteOutcome
import kotlin.test.Test

class VoteResolutionTest {

    private val a = PlayerId("a")
    private val b = PlayerId("b")
    private val c = PlayerId("c")
    private val d = PlayerId("d")

    private fun settings(
        tie: TieBehavior = TieBehavior.REVOTE_TIED_ONLY,
        maxRevotes: Int = 1,
    ) = MafiaSettings(
        roleCounts = MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1),
        voteTieBehavior = tie,
        maxRevotes = maxRevotes,
    )

    @Test
    fun clear_plurality_resolves() {
        val out = VoteResolution.resolve(
            VoteResolution.Inputs(
                casts = mapOf(a to b, c to b, d to a),
                abstained = emptySet(),
                ballot = listOf(a, b, c, d),
                candidates = listOf(a, b, c, d),
                revoteRound = 0,
            ),
            settings = settings(),
        )
        val resolved = out as VoteResolution.Outcome.Resolved
        assertThat(resolved.eliminated).isEqualTo(b)
        assertThat(resolved.tally[b]).isEqualTo(2)
        assertThat(resolved.tally[a]).isEqualTo(1)
    }

    @Test
    fun all_abstained_returns_skipped() {
        val out = VoteResolution.resolve(
            VoteResolution.Inputs(
                casts = emptyMap(),
                abstained = setOf(a, b, c),
                ballot = listOf(a, b, c),
                candidates = listOf(a, b, c),
                revoteRound = 0,
            ),
            settings = settings(),
        )
        val skipped = out as VoteResolution.Outcome.Skipped
        assertThat(skipped.reason).isEqualTo(VoteOutcome.AllAbstained)
    }

    @Test
    fun tied_with_revote_tied_only_carries_only_top_targets() {
        val out = VoteResolution.resolve(
            VoteResolution.Inputs(
                casts = mapOf(a to b, c to a),
                abstained = emptySet(),
                ballot = listOf(a, b, c, d),
                candidates = listOf(a, b, c, d),
                revoteRound = 0,
            ),
            settings = settings(tie = TieBehavior.REVOTE_TIED_ONLY, maxRevotes = 1),
        )
        val tied = out as VoteResolution.Outcome.Tied
        assertThat(tied.tied).containsExactlyInAnyOrder(a, b)
        assertThat(tied.nextRoundCandidates).containsExactlyInAnyOrder(a, b)
    }

    @Test
    fun tied_with_revote_all_carries_full_candidate_list() {
        val out = VoteResolution.resolve(
            VoteResolution.Inputs(
                casts = mapOf(a to b, c to a),
                abstained = emptySet(),
                ballot = listOf(a, b, c, d),
                candidates = listOf(a, b, c, d),
                revoteRound = 0,
            ),
            settings = settings(tie = TieBehavior.REVOTE_ALL, maxRevotes = 1),
        )
        val tied = out as VoteResolution.Outcome.Tied
        assertThat(tied.nextRoundCandidates).containsExactlyInAnyOrder(a, b, c, d)
    }

    @Test
    fun tied_with_skip_elimination_short_circuits_to_skipped() {
        val out = VoteResolution.resolve(
            VoteResolution.Inputs(
                casts = mapOf(a to b, c to a),
                abstained = emptySet(),
                ballot = listOf(a, b, c, d),
                candidates = listOf(a, b, c, d),
                revoteRound = 0,
            ),
            settings = settings(tie = TieBehavior.SKIP_ELIMINATION, maxRevotes = 1),
        )
        val skipped = out as VoteResolution.Outcome.Skipped
        assertThat(skipped.reason).isEqualTo(VoteOutcome.SkippedDueToTie)
    }

    @Test
    fun skip_elimination_policy_remains_the_reason_when_revoting_is_disabled() {
        val out = VoteResolution.resolve(
            VoteResolution.Inputs(
                casts = mapOf(a to b, c to a),
                abstained = emptySet(),
                ballot = listOf(a, b, c, d),
                candidates = listOf(a, b, c, d),
                revoteRound = 0,
            ),
            settings = settings(tie = TieBehavior.SKIP_ELIMINATION, maxRevotes = 0),
        )

        val skipped = out as VoteResolution.Outcome.Skipped
        assertThat(skipped.reason).isEqualTo(VoteOutcome.SkippedDueToTie)
    }

    @Test
    fun tied_at_max_revotes_returns_max_revotes_reached() {
        // revoteRound == maxRevotes → no further revote possible
        val out = VoteResolution.resolve(
            VoteResolution.Inputs(
                casts = mapOf(a to b, c to a),
                abstained = emptySet(),
                ballot = listOf(a, b, c, d),
                candidates = listOf(a, b, c, d),
                revoteRound = 1,
            ),
            settings = settings(tie = TieBehavior.REVOTE_TIED_ONLY, maxRevotes = 1),
        )
        val skipped = out as VoteResolution.Outcome.Skipped
        assertThat(skipped.reason).isEqualTo(VoteOutcome.MaxRevotesReached)
    }

    @Test
    fun tied_at_max_revotes_zero_returns_max_revotes_reached() {
        // maxRevotes = 0 → first tie immediately skipped
        val out = VoteResolution.resolve(
            VoteResolution.Inputs(
                casts = mapOf(a to b, c to a),
                abstained = emptySet(),
                ballot = listOf(a, b, c, d),
                candidates = listOf(a, b, c, d),
                revoteRound = 0,
            ),
            settings = settings(tie = TieBehavior.REVOTE_TIED_ONLY, maxRevotes = 0),
        )
        val skipped = out as VoteResolution.Outcome.Skipped
        assertThat(skipped.reason).isEqualTo(VoteOutcome.MaxRevotesReached)
    }
}
