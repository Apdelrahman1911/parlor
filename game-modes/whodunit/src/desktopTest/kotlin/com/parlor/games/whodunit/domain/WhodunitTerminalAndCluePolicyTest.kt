package com.parlor.games.whodunit.domain

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
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
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPrivate
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class WhodunitTerminalAndCluePolicyTest {
    private val players = (0 until 5).map { index ->
        Player(PlayerId("p${index + 1}"), "Player ${index + 1}", index)
    }
    private val killerId = players.first().id
    private val characters = players.mapIndexed { index, _ -> character("c${index + 1}") }

    @Test
    fun modeRestrictedCluesAreNeverDrawnForAnotherMode() {
        val currentCase = case(
            public = listOf(
                Clue("classic-only", "Classic", appliesToModes = listOf("classic-vote")),
            ),
            pointing = listOf(
                Clue("elimination-only", "Elimination", appliesToModes = listOf("elimination")),
            ),
        )
        for (seed in 0L..100L) {
            val state = roundState(round = 1, seed = seed)
            val result = reduce(state, WhodunitAction.RevealNextClue, currentCase).newState
            assertEquals(ClueId("elimination-only"), result.public.revealedClues.single().id)
        }
    }

    @Test
    fun fivePlayerEliminationTreatsRoundThreeAsFinalEvidence() {
        val currentCase = case(
            public = listOf(Clue("public", "Public")),
            pointing = listOf(Clue("pointing", "Pointing")),
            finalStrong = listOf(Clue("final", "Final")),
        )
        for (seed in 0L..100L) {
            val result = reduce(
                roundState(round = 3, seed = seed),
                WhodunitAction.RevealNextClue,
                currentCase,
            ).newState
            assertEquals(ClueId("final"), result.public.revealedClues.single().id)
        }
    }

    @Test
    fun eliminationAllAbstainCannotAdvanceBeyondFiniteInvestigation() {
        val ballot = players.map { it.id }
        val collecting = VoteState.Collecting(
            isElimination = true,
            ballotPlayerIds = ballot,
            abstained = ballot.toSet(),
            currentVoterIndex = ballot.size,
        )
        val finalRound = roundState(round = 3).copy(
            public = roundState(round = 3).public.copy(voteState = collecting),
        )

        val result = reduce(finalRound, WhodunitAction.CloseVote).newState

        assertEquals(WhodunitPhase.Reveal, result.phase)
        val verdict = assertIs<Verdict.KillerWins>(result.public.verdict)
        assertEquals(KillerWinCause.TieUnresolved, verdict.cause)
    }

    @Test
    fun innocentEliminationOnFinalRoundCannotCreateAnUnauthoredRound() {
        val innocent = players[1].id
        val finalRoundAnnouncement = roundState(round = 3).copy(
            public = roundState(round = 3).public.copy(
                eliminatedPlayers = listOf(innocent),
                voteState = VoteState.Resolved(innocent, wasKiller = false),
            ),
        )

        val result = reduce(
            finalRoundAnnouncement,
            WhodunitAction.AcknowledgeRevealCard,
        ).newState

        assertEquals(WhodunitPhase.Reveal, result.phase)
        val verdict = assertIs<Verdict.KillerWins>(result.public.verdict)
        assertEquals(KillerWinCause.TieUnresolved, verdict.cause)
    }

    @Test
    fun disconnectBeforeRoleAssignmentCancelsWithoutInventingAKiller() {
        val roster = players.take(4)
        val initial = WhodunitDefinition(Json { encodeDefaults = true }).createInitialState(
            SessionConfig(
                sessionId = SessionId("setup-disconnect"),
                caseId = CaseId("test"),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = roster,
                randomSeed = 1L,
            ),
        )
        val disconnected = reduce(
            initial,
            WhodunitAction.MarkPlayerDisconnected(roster[1].id),
        ).newState
        val ended = reduce(
            disconnected,
            WhodunitAction.ContinueWithoutPlayer(roster[1].id),
        ).newState

        assertEquals(WhodunitPhase.PostGame, ended.phase)
        assertNull(ended.public.verdict)
        assertEquals(emptySet(), ended.public.disconnectedPlayers)
        assertEquals("unassigned", ended.hostOnly.killerId.raw)
    }

    @Test
    fun revealCannotFinishAroundADisconnectedPlayerRace() {
        val reveal = roundState(round = 3).copy(
            phase = WhodunitPhase.Reveal,
            public = roundState(round = 3).public.copy(
                verdict = Verdict.PlayersWin("c1"),
                disconnectedPlayers = setOf(players[1].id),
            ),
        )

        val premature = reduce(reveal, WhodunitAction.AcknowledgeReveal).newState
        assertEquals(reveal, premature)

        val expired = reduce(
            reveal,
            WhodunitAction.ContinueWithoutPlayer(players[1].id),
        ).newState
        assertEquals(WhodunitPhase.PostGame, expired.phase)
        assertEquals(emptySet(), expired.public.disconnectedPlayers)
    }

    private fun roundState(round: Int, seed: Long = 1L): WhodunitState {
        val seatMap = players.mapIndexed { index, player ->
            player.id to CharacterId("c${index + 1}")
        }.toMap()
        return WhodunitState(
            public = WhodunitPublic(
                caseId = CaseId("test"),
                modeId = WhodunitIds.EliminationModeId,
                playersAtTable = players,
                roleAssignmentGeneration = 1L,
                currentRound = round,
            ),
            privatePerPlayer = players.associate { player ->
                player.id to WhodunitPrivate(
                    role = if (player.id == killerId) PlayerRole.Killer else PlayerRole.Innocent,
                    characterId = seatMap.getValue(player.id),
                )
            },
            hostOnly = WhodunitHostOnly(
                killerId = killerId,
                killerCharacterId = CharacterId("c1"),
                randomSeed = seed,
                seatToCharacter = seatMap,
                redHerringTargets = emptyList(),
            ),
            phase = WhodunitPhase.Round(round),
            players = players,
        )
    }

    private fun reduce(
        state: WhodunitState,
        action: WhodunitAction,
        currentCase: WhodunitCase = case(),
    ) = WhodunitReducer.reduce(
        state,
        action,
        WhodunitReducerContext(
            clock = FakeClock(Instant.fromEpochMilliseconds(0)),
            random = RandomSource.seeded(1L),
            case = validatedWhodunitCaseForTest(currentCase, caseId = "test"),
        ),
    )

    private fun case(
        public: List<Clue> = listOf(Clue("public", "Public")),
        pointing: List<Clue> = listOf(Clue("pointing", "Pointing")),
        finalStrong: List<Clue> = listOf(Clue("final", "Final")),
    ): WhodunitCase = WhodunitCase(
        publicIntro = "Intro",
        bedrockClues = listOf("Bedrock"),
        characters = characters,
        cluePools = CluePools(
            publicUniversal = public,
            killerPointing = mapOf("c1" to pointing),
            redHerring = emptyMap(),
            contradiction = mapOf("c1" to listOf(Clue("contradiction", "Contradiction"))),
            finalStrong = mapOf("c1" to finalStrong),
        ),
        revealNarratives = characters.associate { it.id to "Reveal" },
    )

    private fun character(id: String) = Character(
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
            deflectionTargets = emptyList(),
            panicMove = "Panic",
        ),
    )
}
