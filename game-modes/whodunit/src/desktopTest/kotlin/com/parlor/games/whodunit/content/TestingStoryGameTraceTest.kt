package com.parlor.games.whodunit.content

import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.ModeId
import com.parlor.core.result.Result
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.rules.WhodunitRoundPolicy
import com.parlor.games.whodunit.domain.rules.WhodunitRules
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.ui.flow.ResumedSession
import com.parlor.games.whodunit.ui.flow.validateResumedSessionForCase
import com.parlor.session.PlayMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Six real bundled seats, not a widened test envelope or a forced killer/phase. */
class TestingStoryGameTraceTest {
    private val fixture = TestingStoryFixtures()

    @Test
    fun everyBundledCaseKillerAndModeCompletesDeterministicallyAndRoundTrips() = runTest {
        var combinations = 0
        bundledWhodunitCaseIds.forEach { caseId ->
            val case = fixture.loadCase(caseId)
            fixture.modes.forEach { mode ->
                val seeds = seedsForEveryKiller(case, mode)
                assertEquals(case.payload.characters.map { it.id }.toSet(), seeds.keys)
                seeds.forEach { (killer, seed) ->
                    val first = completeGame(case, mode, seed, killer)
                    val repeated = completeGame(case, mode, seed, killer)
                    assertEquals(first, repeated, "$caseId/${mode.raw}/$killer/$seed")
                    combinations += 1
                }
            }
        }
        assertEquals(84, combinations)
    }

    private fun seedsForEveryKiller(case: ValidatedCase<WhodunitCase>, mode: ModeId): Map<String, Long> {
        val seeds = mutableMapOf<String, Long>()
        for (seed in 0L..255L) {
            val assigned = fixture.assignedState(case, mode, seed)
            assertEquals(WhodunitPhase.PublicIntro, assigned.phase)
            seeds.putIfAbsent(assigned.hostOnly.killerCharacterId.raw, seed)
            if (seeds.size == case.payload.characters.size) break
        }
        return seeds
    }

    private fun completeGame(
        case: ValidatedCase<WhodunitCase>,
        mode: ModeId,
        seed: Long,
        expectedKiller: String,
    ): WhodunitState {
        val driver = StoryDriver(fixture, case, mode, seed)
        driver.apply(WhodunitAction.AssignRoles(seed))
        assertEquals(expectedKiller, driver.state.hostOnly.killerCharacterId.raw)
        fixture.players.forEach { driver.apply(WhodunitAction.AcknowledgeIntro(it.id)) }
        driver.apply(WhodunitAction.AdvanceFromIntro)
        fixture.players.forEach { driver.apply(WhodunitAction.AcknowledgeBriefing(it.id)) }
        (1..4).forEach { driver.apply(WhodunitAction.AdvanceBriefingCard(it)) }
        val generation = driver.state.public.roleAssignmentGeneration
        fixture.players.forEach {
            driver.apply(WhodunitAction.StartCharacterReveal(it.id, generation))
            driver.apply(WhodunitAction.CompleteCharacterReveal(it.id, generation))
        }
        driver.apply(WhodunitAction.AdvanceFromCharacterReveal)

        val rounds = requireNotNull(WhodunitRules.maximumRoundCount(mode, fixture.players.size))
        for (round in 1..rounds) {
            assertEquals(WhodunitPhase.Round(round), driver.state.phase)
            driver.apply(WhodunitAction.RevealNextClue)
            assertEquals(round, driver.state.public.revealedClues.size)
            if (round == rounds) {
                val finalClue = driver.state.public.revealedClues.last()
                val authoredFinal = case.payload.cluePools.finalStrong.getValue(expectedKiller)
                assertTrue(authoredFinal.any { it.id == finalClue.id.raw && it.text == finalClue.text })
            }
            driver.apply(WhodunitAction.StartDiscussionTimer(
                WhodunitRoundPolicy.discussionSeconds(case.payload, round, fixture.players.size),
            ))
            driver.apply(WhodunitAction.AdvanceFromDiscussion)
            if (mode == WhodunitIds.EliminationModeId || round == rounds) {
                val ballot = assertIs<VoteState.Collecting>(driver.state.public.voteState)
                val killer = driver.state.hostOnly.killerId
                val target = if (round == rounds) killer else ballot.candidatePlayerIds.first { it != killer }
                ballot.ballotPlayerIds.forEach { voter ->
                    driver.apply(
                        if (voter == target) WhodunitAction.AbstainVote(voter)
                        else WhodunitAction.CastVote(voter, target),
                    )
                }
                driver.apply(WhodunitAction.CloseVote)
                if (round < rounds) driver.apply(WhodunitAction.AcknowledgeRevealCard)
            }
        }

        assertEquals(WhodunitPhase.Reveal, driver.state.phase)
        assertIs<Verdict.PlayersWin>(driver.state.public.verdict)
        assertTrue(case.payload.revealNarratives.getValue(expectedKiller).isNotBlank())
        driver.apply(WhodunitAction.AcknowledgeReveal)
        assertEquals(WhodunitPhase.PostGame, driver.state.phase)
        val completed = driver.state
        driver.apply(WhodunitAction.BeginReplay)
        assertEquals(WhodunitPhase.PublicIntro, driver.state.phase)
        assertNotEquals(completed.hostOnly.killerId, driver.state.hostOnly.killerId)
        assertNotEquals(seed, driver.state.hostOnly.randomSeed)
        assertTrue(driver.state.public.revealedClues.isEmpty())
        assertTrue(driver.state.public.rolesViewed.isEmpty())
        return completed
    }
}

/** Every state is produced by actions and checked through both structural and loaded-case recovery. */
private class StoryDriver(
    fixture: TestingStoryFixtures,
    private val case: ValidatedCase<WhodunitCase>,
    mode: ModeId,
    seed: Long,
) {
    private val context = fixture.context(case, seed)
    private val codec = fixture.definition.snapshotCodec()
    private val identity = case.envelope.contentIdentity()
    var state = fixture.initialState(case, mode, seed)
        private set

    fun apply(action: WhodunitAction) {
        val next = WhodunitReducer.reduce(state, action, context).newState
        assertNotEquals(state, next, "${case.envelope.caseId}/${state.public.modeId.raw}: $action")
        val restored = codec.decode(codec.encode(next))
        assertEquals(next, restored, action.toString())
        assertEquals(
            Result.Success(Unit),
            validateResumedSessionForCase(
                ResumedSession(
                    sessionId = com.parlor.core.ids.SessionId("testing-story-recovery"),
                    state = restored,
                    contentIdentity = identity,
                    playMode = PlayMode.PassAndPlay,
                ),
                case,
            ),
            action.toString(),
        )
        state = restored
    }
}
