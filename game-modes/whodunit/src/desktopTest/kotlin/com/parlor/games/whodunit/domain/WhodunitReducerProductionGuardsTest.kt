package com.parlor.games.whodunit.domain

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.reducer.Reduction
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.CluePools
import com.parlor.games.whodunit.content.GuiltyBrief
import com.parlor.games.whodunit.content.InnocentBrief
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.event.WhodunitEvent
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import kotlinx.datetime.Instant
import kotlin.test.Test

class WhodunitReducerProductionGuardsTest {

    private val players = (1..4).map { index ->
        Player(PlayerId("p$index"), "Player $index", seat = index - 1)
    }
    private val killer = players[0].id
    private val outsider = PlayerId("outsider")
    private val ctx = WhodunitReducerContext(
        clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        random = RandomSource.seeded(7L),
        case = validatedWhodunitCaseForTest(case(), caseId = "test"),
    )

    @Test
    fun lifecycle_commands_are_phase_gated() {
        val state = roundState()
        val invalid = listOf(
            WhodunitAction.AssignRoles(99L),
            WhodunitAction.AdvanceFromIntro,
            WhodunitAction.AdvanceBriefingCard(1),
            WhodunitAction.AdvanceFromCharacterReveal,
            WhodunitAction.AcknowledgeReveal,
            WhodunitAction.BeginReplay,
            WhodunitAction.RequestReroll,
        )

        for (action in invalid) {
            assertNoOp(state, action)
        }
    }

    @Test
    fun clue_and_timer_start_are_idempotent() {
        val withoutClue = roundState(clue = null, timer = null)
        val firstReveal = reduce(withoutClue, WhodunitAction.RevealNextClue)
        assertThat(firstReveal.newState.public.revealedClues.size).isEqualTo(1)

        val duplicateReveal = reduce(firstReveal.newState, WhodunitAction.RevealNextClue)
        assertThat(duplicateReveal.newState).isEqualTo(firstReveal.newState)
        assertThat(duplicateReveal.events).isEqualTo(emptyList())

        val firstTimer = reduce(firstReveal.newState, WhodunitAction.StartDiscussionTimer(60))
        val ticked = reduce(firstTimer.newState, WhodunitAction.TimerTicked(20)).newState
        val duplicateStart = reduce(ticked, WhodunitAction.StartDiscussionTimer(60))
        assertThat(duplicateStart.newState.public.timer?.remainingSeconds).isEqualTo(20)
        assertThat(duplicateStart.events).isEqualTo(emptyList())
    }

    @Test
    fun timer_expiry_progresses_instead_of_restarting_the_same_discussion() {
        val classic = reduce(roundState(), WhodunitAction.TimerExpired)
        assertThat(classic.newState.phase).isEqualTo(WhodunitPhase.Round(2))
        assertThat(classic.newState.public.timer).isNull()
        assertThat(classic.events).contains(WhodunitEvent.TimerExhausted)

        val elimination = reduce(
            roundState(modeId = WhodunitIds.EliminationModeId),
            WhodunitAction.TimerExpired,
        )
        assertThat(elimination.newState.phase).isEqualTo(WhodunitPhase.Round(1))
        assertThat(elimination.newState.public.voteState).isInstanceOf(VoteState.Collecting::class)
        assertThat(elimination.newState.public.timer).isNull()
    }

    @Test
    fun ballot_rejects_self_unknown_and_replayed_submissions_and_cannot_close_early() {
        val collecting = VoteState.Collecting(
            isElimination = false,
            ballotPlayerIds = players.map { it.id },
        )
        val start = state(
            phase = WhodunitPhase.FinalVote,
            voteState = collecting,
            timer = null,
        )

        assertNoOp(start, WhodunitAction.CastVote(players[0].id, players[0].id))
        assertNoOp(start, WhodunitAction.CastVote(players[0].id, outsider))
        assertNoOp(start, WhodunitAction.CastVote(players[1].id, players[2].id))

        val first = reduce(
            start,
            WhodunitAction.CastVote(players[0].id, players[1].id),
        ).newState
        assertNoOp(first, WhodunitAction.CastVote(players[0].id, players[2].id))
        assertNoOp(first, WhodunitAction.AbstainVote(players[0].id))
        assertNoOp(first, WhodunitAction.CloseVote)

        var complete = first
        complete = reduce(complete, WhodunitAction.AbstainVote(players[1].id)).newState
        complete = reduce(
            complete,
            WhodunitAction.CastVote(players[2].id, players[1].id),
        ).newState
        complete = reduce(
            complete,
            WhodunitAction.CastVote(players[3].id, players[1].id),
        ).newState

        val closed = reduce(complete, WhodunitAction.CloseVote)
        assertThat(closed.newState.phase).isEqualTo(WhodunitPhase.Reveal)
        assertThat(closed.newState.public.voteState).isInstanceOf(VoteState.Resolved::class)
    }

    @Test
    fun simultaneous_ballots_commit_only_in_canonical_voter_order() {
        val start = state(
            phase = WhodunitPhase.FinalVote,
            voteState = VoteState.Collecting(
                isElimination = false,
                ballotPlayerIds = players.map { it.id },
            ),
            timer = null,
        )

        // Model the second peer's command reaching the serialized reducer
        // before the first peer's command. It must not reserve or skip a turn.
        assertNoOp(start, WhodunitAction.CastVote(players[1].id, players[2].id))

        val afterFirst = reduce(
            start,
            WhodunitAction.CastVote(players[0].id, players[1].id),
        ).newState
        val firstVote = afterFirst.public.voteState as VoteState.Collecting
        assertThat(firstVote.currentVoterIndex).isEqualTo(1)
        assertThat(firstVote.castSoFar).isEqualTo(mapOf(players[0].id to players[1].id))

        // A delayed duplicate from the first voter cannot execute twice; the
        // previously premature second-voter command is valid only when resent
        // against the state where that player is now current.
        assertNoOp(afterFirst, WhodunitAction.CastVote(players[0].id, players[2].id))
        val afterSecond = reduce(
            afterFirst,
            WhodunitAction.CastVote(players[1].id, players[2].id),
        ).newState
        val secondVote = afterSecond.public.voteState as VoteState.Collecting
        assertThat(secondVote.currentVoterIndex).isEqualTo(2)
        assertThat(secondVote.castSoFar.keys.toList())
            .isEqualTo(listOf(players[0].id, players[1].id))
    }

    @Test
    fun tied_revote_keeps_all_voters_but_only_tied_suspects_are_candidates() {
        val tied = VoteState.Tied(
            tiedPlayerIds = listOf(players[0].id, players[1].id),
            debateSecondsRemaining = 0,
        )
        val opened = reduce(
            state(
                phase = WhodunitPhase.TiedRevote,
                voteState = tied,
                timer = null,
            ),
            WhodunitAction.OpenVote,
        ).newState
        val collecting = opened.public.voteState as VoteState.Collecting

        assertThat(collecting.ballotPlayerIds).isEqualTo(players.map { it.id })
        assertThat(collecting.candidatePlayerIds)
            .isEqualTo(listOf(players[0].id, players[1].id))
        assertNoOp(opened, WhodunitAction.CastVote(players[2].id, players[3].id))
        assertNoOp(opened, WhodunitAction.CastVote(players[0].id, players[0].id))

        assertNoOp(opened, WhodunitAction.CastVote(players[2].id, players[0].id))
        val afterFirst = reduce(
            opened,
            WhodunitAction.CastVote(players[0].id, players[1].id),
        ).newState
        val afterSecond = reduce(
            afterFirst,
            WhodunitAction.CastVote(players[1].id, players[0].id),
        ).newState
        val valid = reduce(
            afterSecond,
            WhodunitAction.CastVote(players[2].id, players[0].id),
        )
        assertThat((valid.newState.public.voteState as VoteState.Collecting).castSoFar)
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
        val paused = reduce(roundState(), WhodunitAction.Pause).newState
        assertThat(paused.public.paused).isTrue()
        assertThat(paused.public.timer?.paused).isEqualTo(true)

        assertNoOp(paused, WhodunitAction.TimerExpired)
        assertNoOp(paused, WhodunitAction.AdvanceFromDiscussion)
        assertNoOp(paused, WhodunitAction.OpenVote)
        assertNoOp(paused, WhodunitAction.CastVote(players[0].id, players[1].id))

        val resumed = reduce(paused, WhodunitAction.Resume).newState
        assertThat(resumed.public.paused).isEqualTo(false)
        assertThat(resumed.public.timer?.paused).isEqualTo(false)
    }

    @Test
    fun elimination_final_two_counts_only_active_non_eliminated_players() {
        val vote = VoteState.Collecting(
            isElimination = true,
            ballotPlayerIds = listOf(players[0].id, players[1].id),
            candidatePlayerIds = listOf(players[0].id, players[1].id),
            castSoFar = mapOf(players[0].id to players[1].id),
            abstained = setOf(players[1].id),
            currentVoterIndex = 2,
        )
        val current = state(
            phase = WhodunitPhase.Round(2),
            modeId = WhodunitIds.EliminationModeId,
            voteState = vote,
            timer = null,
            eliminatedPlayers = listOf(players[3].id),
            droppedPlayers = setOf(players[2].id),
        )

        val result = reduce(current, WhodunitAction.CloseVote)
        assertThat(result.newState.phase).isEqualTo(WhodunitPhase.Reveal)
        val verdict = result.newState.public.verdict
        assertThat(verdict is Verdict.KillerWins).isTrue()
        assertThat((verdict as Verdict.KillerWins).cause)
            .isEqualTo(KillerWinCause.SurvivedToFinalTwo)
    }

    private fun reduce(
        state: WhodunitState,
        action: WhodunitAction,
    ): Reduction<WhodunitState, WhodunitEvent> =
        WhodunitReducer.reduce(state, action, ctx)

    private fun assertNoOp(state: WhodunitState, action: WhodunitAction) {
        val reduction = reduce(state, action)
        assertThat(reduction.newState, "state for $action").isEqualTo(state)
        assertThat(reduction.events, "events for $action").isEqualTo(emptyList())
    }

    private fun roundState(
        modeId: com.parlor.core.ids.ModeId = WhodunitIds.ClassicVoteModeId,
        clue: RevealedClue? = RevealedClue(ClueId("round-1"), "Clue", 1),
        timer: PublicTimerState? = PublicTimerState("discussion-1", 60, 60),
    ): WhodunitState = state(
        phase = WhodunitPhase.Round(1),
        modeId = modeId,
        voteState = VoteState.Idle,
        timer = timer,
        revealedClues = listOfNotNull(clue),
    )

    private fun state(
        phase: WhodunitPhase,
        modeId: com.parlor.core.ids.ModeId = WhodunitIds.ClassicVoteModeId,
        voteState: VoteState,
        timer: PublicTimerState?,
        revealedClues: List<RevealedClue> = listOf(
            RevealedClue(ClueId("round-1"), "Clue", 1),
        ),
        eliminatedPlayers: List<PlayerId> = emptyList(),
        droppedPlayers: Set<PlayerId> = emptySet(),
    ): WhodunitState = WhodunitState(
        public = WhodunitPublic(
            caseId = CaseId("test"),
            modeId = modeId,
            playersAtTable = players,
            eliminatedPlayers = eliminatedPlayers,
            currentRound = (phase as? WhodunitPhase.Round)?.index ?: 1,
            revealedClues = revealedClues,
            voteState = voteState,
            timer = timer,
            droppedPlayers = droppedPlayers,
        ),
        privatePerPlayer = emptyMap(),
        hostOnly = WhodunitHostOnly(
            killerId = killer,
            killerCharacterId = CharacterId("c1"),
            randomSeed = 7L,
            seatToCharacter = players.associate { it.id to CharacterId("c${it.seat + 1}") },
            redHerringTargets = emptyList(),
        ),
        phase = phase,
        players = players,
    )

    private fun case(): WhodunitCase {
        val characters = players.mapIndexed { index, _ -> character("c${index + 1}") }
        return WhodunitCase(
            publicIntro = "intro",
            bedrockClues = emptyList(),
            characters = characters,
            cluePools = CluePools(
                publicUniversal = listOf(Clue("round-1", "Clue")),
                killerPointing = mapOf("c1" to listOf(Clue("pointing", "Pointing"))),
                contradiction = mapOf("c1" to listOf(Clue("contradiction", "Contradiction"))),
                redHerring = mapOf("c1" to listOf(Clue("red-herring", "Red herring"))),
                finalStrong = mapOf("c1" to listOf(Clue("final", "Final clue"))),
            ),
            revealNarratives = mapOf("c1" to "Reveal"),
        )
    }

    private fun character(id: String): Character = Character(
        id = id,
        displayName = id,
        relationshipToVictim = "",
        publicIdentity = "",
        publicMotive = "",
        privateSecret = "",
        innocentBrief = InnocentBrief(
            verdictLine = "",
            alibi = "",
            goal = "",
            canSayFreely = "",
            mustHide = "",
        ),
        guiltyBrief = GuiltyBrief(
            verdictLine = "",
            method = "",
            timeline = emptyList(),
            fakeAlibi = "",
            deflectionTargets = emptyList(),
            panicMove = "",
        ),
    )
}
