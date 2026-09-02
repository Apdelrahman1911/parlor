package com.parlor.games.whodunit.domain

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.session.SessionConfig
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.CluePools
import com.parlor.games.whodunit.content.GuiltyBrief
import com.parlor.games.whodunit.content.InnocentBrief
import com.parlor.games.whodunit.content.TimelineEntry
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Instant

/** Reducer guard checks driven exclusively from validator-legal production states. */
class WhodunitReducerProductionGuardsTest {

    private val json = Json { encodeDefaults = true }
    private val definition = WhodunitDefinition(json)
    private val players = (1..5).map { index ->
        Player(PlayerId("p$index"), "Player $index", seat = index - 1)
    }
    private val outsider = PlayerId("outsider")
    private val validatedCase = validatedWhodunitCaseForTest(
        payload = case(),
        caseId = CASE_ID,
        supportedPlayerCounts = players.size..players.size,
    )
    private val ctx = WhodunitReducerContext(
        clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        random = RandomSource.seeded(SEED),
        case = validatedCase,
    )

    @Test
    fun lifecycle_commands_are_phase_gated() {
        val state = firstRound(WhodunitIds.ClassicVoteModeId)
        val invalid = listOf(
            WhodunitAction.AssignRoles(99L),
            WhodunitAction.AdvanceFromIntro,
            WhodunitAction.AdvanceBriefingCard(1),
            WhodunitAction.AdvanceFromCharacterReveal,
            WhodunitAction.AcknowledgeReveal,
            WhodunitAction.BeginReplay,
            WhodunitAction.RequestReroll,
        )

        invalid.forEach { action -> assertNoOp(state, action) }
    }

    @Test
    fun clue_and_timer_start_are_idempotent() {
        val withoutClue = firstRound(WhodunitIds.ClassicVoteModeId)
        val firstReveal = reduce(withoutClue, WhodunitAction.RevealNextClue)
        assertThat(firstReveal.newState.public.revealedClues.size).isEqualTo(1)

        val duplicateReveal = reduce(firstReveal.newState, WhodunitAction.RevealNextClue)
        assertThat(duplicateReveal.newState).isEqualTo(firstReveal.newState)
        assertThat(duplicateReveal.events).isEqualTo(emptyList())

        val firstTimer = reduce(firstReveal.newState, WhodunitAction.StartDiscussionTimer(180))
        val ticked = step(firstTimer.newState, WhodunitAction.TimerTicked(20))
        val duplicateStart = reduce(ticked, WhodunitAction.StartDiscussionTimer(180))
        assertThat(duplicateStart.newState.public.timer?.remainingSeconds).isEqualTo(20)
        assertThat(duplicateStart.events).isEqualTo(emptyList())
    }

    @Test
    fun timer_expiry_progresses_instead_of_restarting_the_same_discussion() {
        val classic = reduce(
            discussionAtOneSecond(WhodunitIds.ClassicVoteModeId),
            WhodunitAction.TimerExpired,
        )
        assertThat(classic.newState.phase).isEqualTo(WhodunitPhase.Round(2))
        assertThat(classic.newState.public.timer).isNull()
        assertThat(classic.events).contains(WhodunitEvent.TimerExhausted)

        val elimination = reduce(
            discussionAtOneSecond(WhodunitIds.EliminationModeId),
            WhodunitAction.TimerExpired,
        )
        assertThat(elimination.newState.phase).isEqualTo(WhodunitPhase.Round(1))
        assertThat(elimination.newState.public.voteState).isInstanceOf(VoteState.Collecting::class)
        assertThat(elimination.newState.public.timer).isNull()
    }

    @Test
    fun ballot_rejects_self_unknown_and_replayed_submissions_and_cannot_close_early() {
        val start = stateAtVote(WhodunitIds.ClassicVoteModeId)

        assertNoOp(start, WhodunitAction.CastVote(players[0].id, players[0].id))
        assertNoOp(start, WhodunitAction.CastVote(players[0].id, outsider))
        assertNoOp(start, WhodunitAction.CastVote(players[1].id, players[2].id))

        val first = step(start, WhodunitAction.CastVote(players[0].id, players[1].id))
        assertNoOp(first, WhodunitAction.CastVote(players[0].id, players[2].id))
        assertNoOp(first, WhodunitAction.AbstainVote(players[0].id))
        assertNoOp(first, WhodunitAction.CloseVote)

        var complete = step(first, WhodunitAction.AbstainVote(players[1].id))
        players.drop(2).forEach { voter ->
            complete = step(complete, WhodunitAction.CastVote(voter.id, players[1].id))
        }

        val closed = reduce(complete, WhodunitAction.CloseVote)
        assertThat(closed.newState.phase).isEqualTo(WhodunitPhase.Reveal)
        assertThat(closed.newState.public.voteState).isInstanceOf(VoteState.Resolved::class)
    }

    @Test
    fun simultaneous_ballots_commit_only_in_canonical_voter_order() {
        val start = stateAtVote(WhodunitIds.ClassicVoteModeId)

        // The second command can arrive first, but cannot reserve or skip a turn.
        assertNoOp(start, WhodunitAction.CastVote(players[1].id, players[2].id))

        val afterFirst = step(
            start,
            WhodunitAction.CastVote(players[0].id, players[1].id),
        )
        val firstVote = afterFirst.public.voteState as VoteState.Collecting
        assertThat(firstVote.currentVoterIndex).isEqualTo(1)
        assertThat(firstVote.castSoFar).isEqualTo(mapOf(players[0].id to players[1].id))

        assertNoOp(afterFirst, WhodunitAction.CastVote(players[0].id, players[2].id))
        val afterSecond = step(
            afterFirst,
            WhodunitAction.CastVote(players[1].id, players[2].id),
        )
        val secondVote = afterSecond.public.voteState as VoteState.Collecting
        assertThat(secondVote.currentVoterIndex).isEqualTo(2)
        assertThat(secondVote.castSoFar.keys.toList())
            .isEqualTo(listOf(players[0].id, players[1].id))
    }

    @Test
    fun tied_revote_keeps_all_voters_but_only_tied_suspects_are_candidates() {
        var tied = stateAtVote(WhodunitIds.ClassicVoteModeId)
        val actions = listOf(
            WhodunitAction.CastVote(players[0].id, players[1].id),
            WhodunitAction.CastVote(players[1].id, players[0].id),
            WhodunitAction.CastVote(players[2].id, players[0].id),
            WhodunitAction.CastVote(players[3].id, players[1].id),
            WhodunitAction.AbstainVote(players[4].id),
        )
        actions.forEach { action -> tied = step(tied, action) }
        tied = step(tied, WhodunitAction.CloseVote)

        val opened = step(tied, WhodunitAction.OpenVote)
        val collecting = opened.public.voteState as VoteState.Collecting

        assertThat(collecting.ballotPlayerIds).isEqualTo(players.map { it.id })
        assertThat(collecting.candidatePlayerIds)
            .isEqualTo(listOf(players[0].id, players[1].id))
        assertNoOp(opened, WhodunitAction.CastVote(players[2].id, players[3].id))
        assertNoOp(opened, WhodunitAction.CastVote(players[0].id, players[0].id))
        assertNoOp(opened, WhodunitAction.CastVote(players[2].id, players[0].id))

        val afterFirst = step(opened, WhodunitAction.CastVote(players[0].id, players[1].id))
        val afterSecond = step(afterFirst, WhodunitAction.CastVote(players[1].id, players[0].id))
        val valid = step(afterSecond, WhodunitAction.CastVote(players[2].id, players[0].id))
        assertThat((valid.public.voteState as VoteState.Collecting).castSoFar)
            .isEqualTo(
                mapOf(
                    players[0].id to players[1].id,
                    players[1].id to players[0].id,
                    players[2].id to players[0].id,
                ),
            )
    }

    @Test
    fun session_pause_blocks_gameplay_but_allows_resume() {
        val paused = step(
            discussionAtOneSecond(WhodunitIds.ClassicVoteModeId),
            WhodunitAction.Pause,
        )
        assertThat(paused.public.paused).isTrue()
        assertThat(paused.public.timer?.paused).isEqualTo(true)

        assertNoOp(paused, WhodunitAction.TimerExpired)
        assertNoOp(paused, WhodunitAction.AdvanceFromDiscussion)
        assertNoOp(paused, WhodunitAction.OpenVote)
        assertNoOp(paused, WhodunitAction.CastVote(players[0].id, players[1].id))

        val resumed = step(paused, WhodunitAction.Resume)
        assertThat(resumed.public.paused).isEqualTo(false)
        assertThat(resumed.public.timer?.paused).isEqualTo(false)
    }

    @Test
    fun eliminated_audience_disconnect_does_not_pause_or_end_the_case() {
        var active = stateAtVote(WhodunitIds.EliminationModeId)
        val eliminated = players.first { it.id != active.hostOnly.killerId }.id
        val ballot = (active.public.voteState as VoteState.Collecting).ballotPlayerIds
        ballot.forEach { voter ->
            active = step(
                active,
                if (voter == eliminated) {
                    WhodunitAction.AbstainVote(voter)
                } else {
                    WhodunitAction.CastVote(voter, eliminated)
                },
            )
        }
        active = step(active, WhodunitAction.CloseVote)
        assertThat(active.public.eliminatedPlayers).isEqualTo(listOf(eliminated))

        assertNoOp(active, WhodunitAction.MarkPlayerDisconnected(eliminated))
    }

    private fun firstRound(modeId: com.parlor.core.ids.ModeId): WhodunitState {
        var state = definition.createInitialState(
            SessionConfig(
                sessionId = SessionId("guards-${modeId.raw}"),
                caseId = CaseId(CASE_ID),
                modeId = modeId,
                players = players,
                randomSeed = SEED,
            ),
        )
        WhodunitStateValidator.requireValidForCase(state, validatedCase)
        state = step(state, WhodunitAction.AssignRoles(SEED))
        players.forEach { player ->
            state = step(state, WhodunitAction.AcknowledgeIntro(player.id))
        }
        state = step(state, WhodunitAction.AdvanceFromIntro)
        players.forEach { player ->
            state = step(state, WhodunitAction.AcknowledgeBriefing(player.id))
        }
        for (card in 1..4) {
            state = step(state, WhodunitAction.AdvanceBriefingCard(card))
        }
        val generation = state.public.roleAssignmentGeneration
        players.forEach { player ->
            state = step(state, WhodunitAction.StartCharacterReveal(player.id, generation))
            state = step(state, WhodunitAction.CompleteCharacterReveal(player.id, generation))
        }
        return step(state, WhodunitAction.AdvanceFromCharacterReveal)
    }

    private fun discussionAtOneSecond(modeId: com.parlor.core.ids.ModeId): WhodunitState {
        var state = firstRound(modeId)
        state = step(state, WhodunitAction.RevealNextClue)
        state = step(state, WhodunitAction.StartDiscussionTimer(180))
        return step(state, WhodunitAction.TimerTicked(1))
    }

    private fun stateAtVote(modeId: com.parlor.core.ids.ModeId): WhodunitState {
        var state = firstRound(modeId)
        while (state.public.voteState !is VoteState.Collecting) {
            state = step(state, WhodunitAction.RevealNextClue)
            state = step(state, WhodunitAction.StartDiscussionTimer(180))
            state = step(state, WhodunitAction.AdvanceFromDiscussion)
        }
        return state
    }

    private fun reduce(
        state: WhodunitState,
        action: WhodunitAction,
    ): Reduction<WhodunitState, WhodunitEvent> =
        WhodunitReducer.reduce(state, action, ctx).also { reduction ->
            WhodunitStateValidator.requireValidForCase(reduction.newState, validatedCase)
        }

    private fun step(state: WhodunitState, action: WhodunitAction): WhodunitState =
        reduce(state, action).newState

    private fun assertNoOp(state: WhodunitState, action: WhodunitAction) {
        val reduction = reduce(state, action)
        assertThat(reduction.newState, "state for $action").isEqualTo(state)
        assertThat(reduction.events, "events for $action").isEqualTo(emptyList())
    }

    private fun case(): WhodunitCase {
        val ids = players.indices.map { "c${it + 1}" }
        val characters = ids.map { id -> character(id, ids) }
        return WhodunitCase(
            publicIntro = "Intro",
            bedrockClues = listOf("Bedrock"),
            characters = characters,
            cluePools = CluePools(
                publicUniversal = listOf(Clue("public", "Public")),
                killerPointing = ids.associateWith { id ->
                    (1..3).map { index -> Clue("pointing-$id-$index", "Pointing $index") }
                },
                contradiction = ids.associateWith { id ->
                    listOf(Clue("contradiction-$id", "Contradiction"))
                },
                redHerring = ids.associateWith { id ->
                    listOf(Clue("red-$id", "Red herring"))
                },
                finalStrong = ids.associateWith { id ->
                    (1..2).map { index -> Clue("final-$id-$index", "Final $index") }
                },
            ),
            revealNarratives = ids.associateWith { "Reveal $it" },
        )
    }

    private fun character(id: String, ids: List<String>) = Character(
        id = id,
        displayName = id,
        relationshipToVictim = "Friend",
        publicIdentity = "Identity",
        publicMotive = "Motive",
        privateSecret = "Secret",
        innocentBrief = InnocentBrief("Innocent", "Alibi", "Goal", "Say", "Hide"),
        guiltyBrief = GuiltyBrief(
            verdictLine = "Guilty",
            method = "Method",
            timeline = listOf(TimelineEntry("10:00", "Action")),
            fakeAlibi = "Alibi",
            deflectionTargets = ids.filterNot { it == id },
            panicMove = "Panic",
        ),
    )

    private companion object {
        const val CASE_ID = "test"
        const val SEED = 7L
    }
}
