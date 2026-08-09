package com.parlor.games.whodunit.snapshot

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
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

class WhodunitSnapshotValidationTest {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }
    private val codec = WhodunitSnapshotCodec(json)
    private val players = (0 until 4).map { index ->
        Player(PlayerId("p${index + 1}"), "Player ${index + 1}", index)
    }
    private val assigned: WhodunitState = assignedState()

    @Test
    fun validAuthoritativeStateStillRoundTripsExactly() {
        assertEquals(assigned, codec.decode(codec.encode(assigned)))
    }

    @Test
    fun syntacticallyValidSnapshotWithDivergentRosterIsRejected() {
        val impossible = assigned.copy(
            public = assigned.public.copy(playersAtTable = assigned.players.dropLast(1)),
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun syntacticallyValidSnapshotWithIncompletePrivateMapIsRejected() {
        val impossible = assigned.copy(
            privatePerPlayer = assigned.privatePerPlayer - players.last().id,
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun syntacticallyValidSnapshotWithTwoKillersIsRejected() {
        val innocent = assigned.privatePerPlayer.entries.first { it.value.role == PlayerRole.Innocent }
        val impossible = assigned.copy(
            privatePerPlayer = assigned.privatePerPlayer +
                (innocent.key to innocent.value.copy(role = PlayerRole.Killer)),
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun syntacticallyValidSnapshotWithOverlappingVoteAndAbstentionIsRejected() {
        val voter = players.first().id
        val target = players[1].id
        val impossible = assigned.copy(
            phase = WhodunitPhase.FinalVote,
            public = assigned.public.copy(
                currentRound = 3,
                voteState = VoteState.Collecting(
                    isElimination = false,
                    ballotPlayerIds = players.map { it.id },
                    castSoFar = mapOf(voter to target),
                    abstained = setOf(voter),
                    currentVoterIndex = 2,
                ),
            ),
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun privateRevealFlagsCannotSurviveOutsideCharacterReveal() {
        val playerId = players.first().id
        val privateState = assigned.privatePerPlayer.getValue(playerId)
        val impossible = assigned.copy(
            privatePerPlayer = assigned.privatePerPlayer + (
                playerId to privateState.copy(
                    dossierUnlocked = true,
                    privateReviewOpen = true,
                )
            ),
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun viewedRoleCannotRetainPrivateRevealFlags() {
        val playerId = players.first().id
        val privateState = assigned.privatePerPlayer.getValue(playerId)
        val impossible = assigned.copy(
            phase = WhodunitPhase.CharacterReveal(0),
            public = assigned.public.copy(rolesViewed = setOf(playerId)),
            privatePerPlayer = assigned.privatePerPlayer + (
                playerId to privateState.copy(dossierUnlocked = true)
            ),
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun currentRoundCannotExceedModeMaximum() {
        val impossible = assigned.copy(
            phase = WhodunitPhase.Round(4),
            public = assigned.public.copy(currentRound = 4),
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun revealedCluesMustBeOrderedAndWithinCurrentRound() {
        val outOfOrder = assigned.copy(
            phase = WhodunitPhase.Round(3),
            public = assigned.public.copy(
                currentRound = 3,
                revealedClues = listOf(
                    RevealedClue(ClueId("round-2"), "Second", 2),
                    RevealedClue(ClueId("round-1"), "First", 1),
                ),
            ),
            hostOnly = assigned.hostOnly.copy(
                drawnClueIds = setOf(ClueId("round-1"), ClueId("round-2")),
            ),
        )
        assertDecodeRejected(outOfOrder)

        val aheadOfState = assigned.copy(
            phase = WhodunitPhase.Round(1),
            public = assigned.public.copy(
                currentRound = 1,
                revealedClues = listOf(RevealedClue(ClueId("round-2"), "Second", 2)),
            ),
            hostOnly = assigned.hostOnly.copy(drawnClueIds = setOf(ClueId("round-2"))),
        )
        assertDecodeRejected(aheadOfState)
    }

    @Test
    fun activeTimerRequiresCurrentRoundClue() {
        val impossible = assigned.copy(
            phase = WhodunitPhase.Round(1),
            public = assigned.public.copy(
                currentRound = 1,
                timer = PublicTimerState("discussion-1", 60, 60),
            ),
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun collectingVoteMustContainCanonicalBallotPrefix() {
        val impossible = assigned.copy(
            phase = WhodunitPhase.FinalVote,
            public = assigned.public.copy(
                currentRound = 3,
                voteState = VoteState.Collecting(
                    isElimination = false,
                    ballotPlayerIds = players.map { it.id },
                    castSoFar = mapOf(players[1].id to players[2].id),
                    currentVoterIndex = 1,
                ),
            ),
        )
        assertDecodeRejected(impossible)
    }

    @Test
    fun legacyTieCountdownIsNormalizedToExplicitlyUntimedState() {
        val legacy = assigned.copy(
            phase = WhodunitPhase.TiedRevote,
            public = assigned.public.copy(
                currentRound = 3,
                voteState = VoteState.Tied(
                    tiedPlayerIds = listOf(players[0].id, players[1].id),
                    debateSecondsRemaining = 60,
                ),
            ),
        )
        val bytes = json.encodeToString(WhodunitState.serializer(), legacy).encodeToByteArray()

        val restored = codec.decode(bytes)

        assertEquals(0, (restored.public.voteState as VoteState.Tied).debateSecondsRemaining)
    }

    @Test
    fun legacySnapshotReconstructsFilteredKillerTargetsFromHostAuthority() {
        val root = json.encodeToJsonElement(WhodunitState.serializer(), assigned).jsonObject
        val legacyPrivate = JsonObject(
            root.getValue("privatePerPlayer").jsonObject.mapValues { (_, value) ->
                JsonObject(value.jsonObject - "deflectionTargets")
            },
        )
        val legacy = JsonObject(root + ("privatePerPlayer" to legacyPrivate))

        val restored = codec.decode(legacy.toString().encodeToByteArray())

        assertEquals(
            restored.hostOnly.redHerringTargets,
            restored.privatePerPlayer.getValue(restored.hostOnly.killerId).deflectionTargets,
        )
    }

    @Test
    fun loadedCaseBoundaryAcceptsMatchingAssignedState() {
        WhodunitStateValidator.requireValidForCase(
            assigned,
            expectedCaseId = CaseId("snapshot-case"),
            payload = case(),
        )
    }

    @Test
    fun loadedCaseBoundaryRejectsStaleCaseAndUnknownAssignedCharacter() {
        assertFailsWith<IllegalArgumentException> {
            WhodunitStateValidator.requireValidForCase(
                assigned,
                expectedCaseId = CaseId("replacement-case"),
                payload = case(),
            )
        }

        val playerId = players.first { it.id != assigned.hostOnly.killerId }.id
        val unknown = CharacterId("removed-character")
        val staleAssignment = assigned.copy(
            privatePerPlayer = assigned.privatePerPlayer + (
                playerId to assigned.privatePerPlayer.getValue(playerId).copy(characterId = unknown)
            ),
            hostOnly = assigned.hostOnly.copy(
                seatToCharacter = assigned.hostOnly.seatToCharacter + (playerId to unknown),
                redHerringTargets = emptyList(),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            WhodunitStateValidator.requireValidForCase(
                staleAssignment,
                expectedCaseId = CaseId("snapshot-case"),
                payload = case(),
            )
        }

        val killerId = assigned.hostOnly.killerId
        val unknownKillerCharacter = CharacterId("removed-killer-character")
        val staleKiller = assigned.copy(
            privatePerPlayer = assigned.privatePerPlayer + (
                killerId to assigned.privatePerPlayer.getValue(killerId).copy(
                    characterId = unknownKillerCharacter,
                )
            ),
            hostOnly = assigned.hostOnly.copy(
                killerCharacterId = unknownKillerCharacter,
                seatToCharacter = assigned.hostOnly.seatToCharacter +
                    (killerId to unknownKillerCharacter),
                redHerringTargets = emptyList(),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            WhodunitStateValidator.requireValidForCase(
                staleKiller,
                expectedCaseId = CaseId("snapshot-case"),
                payload = case(),
            )
        }
    }

    @Test
    fun loadedCaseBoundaryRejectsUnknownOrChangedClue() {
        val unknown = stateWithClue(ClueId("removed-clue"), "Removed")
        assertFailsWith<IllegalArgumentException> {
            WhodunitStateValidator.requireValidForCase(
                unknown,
                expectedCaseId = CaseId("snapshot-case"),
                payload = case(),
            )
        }

        val changedText = stateWithClue(ClueId("public"), "Changed after save")
        assertFailsWith<IllegalArgumentException> {
            WhodunitStateValidator.requireValidForCase(
                changedText,
                expectedCaseId = CaseId("snapshot-case"),
                payload = case(),
            )
        }

        WhodunitStateValidator.requireValidForCase(
            stateWithClue(ClueId("public"), "Public"),
            expectedCaseId = CaseId("snapshot-case"),
            payload = case(),
        )
    }

    @Test
    fun oversizedSnapshotIsRejectedBeforeJsonParsing() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode(ByteArray(256 * 1024 + 1) { ' '.code.toByte() })
        }
    }

    private fun assertDecodeRejected(state: WhodunitState) {
        val bytes = json.encodeToString(WhodunitState.serializer(), state).encodeToByteArray()
        assertFailsWith<IllegalArgumentException> { codec.decode(bytes) }
    }

    private fun stateWithClue(id: ClueId, text: String): WhodunitState = assigned.copy(
        phase = WhodunitPhase.Round(1),
        public = assigned.public.copy(
            currentRound = 1,
            revealedClues = listOf(RevealedClue(id, text, roundIndex = 1)),
        ),
        hostOnly = assigned.hostOnly.copy(drawnClueIds = setOf(id)),
    )

    private fun assignedState(): WhodunitState {
        val definition = WhodunitDefinition(json)
        val initial = definition.createInitialState(
            SessionConfig(
                sessionId = SessionId("snapshot-validation"),
                caseId = CaseId("snapshot-case"),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = players,
                randomSeed = 7L,
            ),
        )
        return WhodunitReducer.reduce(
            initial,
            WhodunitAction.AssignRoles(7L),
            WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochMilliseconds(0)),
                random = RandomSource.seeded(7L),
                case = case(),
            ),
        ).newState
    }

    private fun case(): WhodunitCase {
        val characters = (1..4).map { index -> character("c$index") }
        return WhodunitCase(
            publicIntro = "Intro",
            bedrockClues = listOf("Bedrock"),
            characters = characters,
            cluePools = CluePools(
                publicUniversal = listOf(Clue("public", "Public")),
                killerPointing = characters.associate { character ->
                    character.id to listOf(Clue("${character.id}-pointing", "Pointing"))
                },
                redHerring = emptyMap(),
                contradiction = characters.associate { character ->
                    character.id to listOf(Clue("${character.id}-contradiction", "Contradiction"))
                },
                finalStrong = characters.associate { character ->
                    character.id to listOf(Clue("${character.id}-final", "Final"))
                },
            ),
            revealNarratives = characters.associate { it.id to "Reveal" },
        )
    }

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
            deflectionTargets = (1..4).map { "c$it" }.filterNot { it == id },
            panicMove = "Panic",
        ),
    )
}
