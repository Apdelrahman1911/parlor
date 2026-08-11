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
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun currentWritesUseTheExplicitCanonicalSnapshotEnvelope() {
        val root = currentEnvelope()

        assertEquals(setOf("kind", "schemaVersion", "state"), root.keys)
        assertEquals(WHODUNIT_SNAPSHOT_KIND, root.getValue("kind").jsonPrimitive.content)
        assertEquals(
            WHODUNIT_SNAPSHOT_SCHEMA_VERSION.toString(),
            root.getValue("schemaVersion").jsonPrimitive.content,
        )
        assertEquals(
            json.encodeToJsonElement(WhodunitState.serializer(), assigned).jsonObject,
            root.getValue("state").jsonObject,
        )
    }

    @Test
    fun currentEnvelopeNeverReceivesLegacyMissingFieldRepair() {
        val envelope = currentEnvelope()
        val state = envelope.getValue("state").jsonObject
        val public = JsonObject(
            state.getValue("public").jsonObject - "roleAssignmentGeneration",
        )
        val missingCurrentField = JsonObject(
            envelope + ("state" to JsonObject(state + ("public" to public))),
        )

        assertFails { codec.decode(missingCurrentField.toString().encodeToByteArray()) }
    }

    @Test
    fun reservedEnvelopeKeysCannotFallBackToLegacyDecoding() {
        val bare = json.encodeToJsonElement(WhodunitState.serializer(), assigned).jsonObject
        val partialEnvelope = JsonObject(bare + ("schemaVersion" to JsonPrimitive(1)))

        assertFails { codec.decode(partialEnvelope.toString().encodeToByteArray()) }
    }

    @Test
    fun currentEnvelopeRejectsWrongKindUnknownSchemaAndUnknownFields() {
        val envelope = currentEnvelope()
        listOf(
            JsonObject(envelope + ("kind" to JsonPrimitive("another.game.snapshot"))),
            JsonObject(envelope + ("schemaVersion" to JsonPrimitive(2))),
            JsonObject(envelope + ("unexpected" to JsonPrimitive(true))),
        ).forEach { invalid ->
            assertFails { codec.decode(invalid.toString().encodeToByteArray()) }
        }
    }

    @Test
    fun callerJsonSettingsCannotMakeSnapshotDecodingPermissive() {
        val permissiveCodec = WhodunitSnapshotCodec(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            },
        )
        val envelope = currentEnvelope()
        val state = envelope.getValue("state").jsonObject
        val stateWithUnknownField = JsonObject(state + ("unexpected" to JsonPrimitive("value")))
        val invalid = JsonObject(envelope + ("state" to stateWithUnknownField))

        assertFails { permissiveCodec.decode(invalid.toString().encodeToByteArray()) }
    }

    @Test
    fun malformedUtf8IsRejectedBeforeJsonInterpretation() {
        assertFails { codec.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
    }

    @Test
    fun activeStateCannotContainAFormerlyDroppedCompatibilitySeat() {
        assertDecodeRejected(
            assigned.copy(
                public = assigned.public.copy(droppedPlayers = setOf(players.last().id)),
            ),
        )
    }

    @Test
    fun exactDuplicateRosterNamesAreRejectedBySnapshotRecovery() {
        val duplicatePlayers = assigned.players.toMutableList().also {
            it[1] = it[1].copy(displayName = it[0].displayName)
        }
        assertDecodeRejected(
            assigned.copy(
                players = duplicatePlayers,
                public = assigned.public.copy(playersAtTable = duplicatePlayers),
            ),
        )
    }

    @Test
    fun graceExpiryRevealRoundTripsAndIsValidForPeerInstallation() {
        val missingPlayer = players.last().id
        val disconnected = WhodunitReducer.reduce(
            assigned,
            WhodunitAction.MarkPlayerDisconnected(missingPlayer),
            reducerContext(),
        ).newState
        val terminalReveal = WhodunitReducer.reduce(
            disconnected,
            WhodunitAction.ContinueWithoutPlayer(missingPlayer),
            reducerContext(),
        ).newState

        assertEquals(WhodunitPhase.Reveal, terminalReveal.phase)
        assertEquals(setOf(missingPlayer), terminalReveal.public.droppedPlayers)
        assertEquals(terminalReveal, codec.decode(codec.encode(terminalReveal)))

        val receivingPlayer = players.first().id
        val publicProjection = WhodunitProjectionPolicy.toPublic(terminalReveal).state
        val ownPrivate = WhodunitProjectionPolicy
            .toPlayer(terminalReveal, receivingPlayer)
            .state
            .privatePerPlayer[receivingPlayer]
        assertTrue(
            WhodunitStateValidator.isValidPeerProjectionForCase(
                publicState = publicProjection,
                ownPrivate = ownPrivate,
                selfPlayerId = receivingPlayer,
                case = validatedCase(),
            ),
        )
    }

    @Test
    fun reducerGeneratedEliminationFinalTwoRoundTripsAndIsValidForPeerInstallation() {
        val terminal = eliminationStateAfterInnocentEliminations(3)
        val verdict = terminal.public.verdict as Verdict.KillerWins
        val resolved = terminal.public.voteState as VoteState.Resolved
        val survivors = terminal.players.map { it.id }
            .filterNot(terminal.public.eliminatedPlayers::contains)

        assertEquals(WhodunitPhase.Reveal, terminal.phase)
        assertEquals(KillerWinCause.SurvivedToFinalTwo, verdict.cause)
        assertEquals(2, survivors.size)
        assertEquals(terminal.public.eliminatedPlayers.last(), resolved.accusedPlayerId)
        assertEquals(false, resolved.wasKiller)
        WhodunitStateValidator.requireValid(terminal)
        assertEquals(terminal, codec.decode(codec.encode(terminal)))

        val receivingPlayer = survivors.first()
        val publicProjection = WhodunitProjectionPolicy.toPublic(terminal).state
        val ownPrivate = WhodunitProjectionPolicy
            .toPlayer(terminal, receivingPlayer)
            .state
            .privatePerPlayer[receivingPlayer]
        assertTrue(
            WhodunitStateValidator.isValidPeerProjectionForCase(
                publicState = publicProjection,
                ownPrivate = ownPrivate,
                selfPlayerId = receivingPlayer,
                case = validatedCase(
                    caseId = "snapshot-elimination",
                    characterCount = 5,
                ),
            ),
        )
    }

    @Test
    fun eliminationTerminalOutcomesRejectReducerImpossibleHistories() {
        val finalTwo = eliminationStateAfterInnocentEliminations(3)
        val killerId = finalTwo.hostOnly.killerId
        val killerCharacterId = finalTwo.hostOnly.killerCharacterId.raw

        assertDecodeRejected(
            finalTwo.copy(
                public = finalTwo.public.copy(
                    voteState = VoteState.Resolved(killerId, wasKiller = true),
                ),
            ),
        )
        assertDecodeRejected(
            finalTwo.copy(
                public = finalTwo.public.copy(
                    verdict = Verdict.KillerWins(
                        killerCharacterId,
                        KillerWinCause.InnocentAccused,
                    ),
                ),
            ),
        )
        assertDecodeRejected(
            finalTwo.copy(
                public = finalTwo.public.copy(
                    verdict = Verdict.PlayersWin(killerCharacterId),
                    voteState = VoteState.Resolved(killerId, wasKiller = true),
                ),
            ),
        )

        val firstElimination = eliminationStateAfterInnocentEliminations(1)
        val nextRound = WhodunitReducer.reduce(
            firstElimination,
            WhodunitAction.AcknowledgeRevealCard,
            eliminationReducerContext(),
        ).newState
        val withSecondClue = WhodunitReducer.reduce(
            nextRound,
            WhodunitAction.RevealNextClue,
            eliminationReducerContext(),
        ).newState
        assertDecodeRejected(
            withSecondClue.copy(
                phase = WhodunitPhase.Reveal,
                public = withSecondClue.public.copy(
                    voteState = VoteState.Resolved(killerId, wasKiller = true),
                    verdict = Verdict.KillerWins(killerCharacterId, KillerWinCause.TieUnresolved),
                ),
            ),
        )
    }

    @Test
    fun eliminationProgressCannotOutrunRoundsOrEliminateTheKillerBeforeTerminalVictory() {
        val firstElimination = eliminationStateAfterInnocentEliminations(1)
        val nextRound = WhodunitReducer.reduce(
            firstElimination,
            WhodunitAction.AcknowledgeRevealCard,
            eliminationReducerContext(),
        ).newState
        val additionalInnocents = nextRound.players.map { it.id }
            .filterNot { it == nextRound.hostOnly.killerId }
            .filterNot(nextRound.public.eliminatedPlayers::contains)
            .take(2)

        assertDecodeRejected(
            nextRound.copy(
                public = nextRound.public.copy(
                    eliminatedPlayers = nextRound.public.eliminatedPlayers + additionalInnocents,
                ),
            ),
        )
        assertDecodeRejected(
            nextRound.copy(
                public = nextRound.public.copy(
                    eliminatedPlayers = nextRound.public.eliminatedPlayers + nextRound.hostOnly.killerId,
                ),
            ),
        )
    }

    @Test
    fun peerFinalTwoProjectionRejectsAKnownKillerInTheEliminationHistory() {
        val terminal = eliminationStateAfterInnocentEliminations(3)
        val killerId = terminal.hostOnly.killerId
        val forgedEliminations = terminal.public.eliminatedPlayers.toMutableList().also {
            it[0] = killerId
        }
        val forgedPublic = WhodunitProjectionPolicy.toPublic(terminal).state.copy(
            public = terminal.public.copy(eliminatedPlayers = forgedEliminations),
        )
        val killerPrivate = terminal.privatePerPlayer.getValue(killerId)

        assertFalse(
            WhodunitStateValidator.isValidPeerProjectionForCase(
                publicState = forgedPublic,
                ownPrivate = killerPrivate,
                selfPlayerId = killerId,
                case = validatedCase(
                    caseId = "snapshot-elimination",
                    characterCount = 5,
                ),
            ),
        )
    }

    @Test
    fun assignmentGenerationMustMatchWhetherRolesExist() {
        assertDecodeRejected(
            assigned.copy(
                public = assigned.public.copy(roleAssignmentGeneration = -1L),
            ),
        )
        assertDecodeRejected(
            assigned.copy(
                public = assigned.public.copy(roleAssignmentGeneration = 0L),
            ),
        )

        val unassigned = WhodunitDefinition(json).createInitialState(
            SessionConfig(
                sessionId = SessionId("invalid-unassigned-generation"),
                caseId = CaseId("snapshot-case"),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = players,
                randomSeed = 7L,
            ),
        )
        assertDecodeRejected(
            unassigned.copy(
                public = unassigned.public.copy(roleAssignmentGeneration = 1L),
            ),
        )
    }

    @Test
    fun legacyAssignedSnapshotWithoutGenerationMigratesButExplicitZeroDoesNot() {
        val root = json.encodeToJsonElement(WhodunitState.serializer(), assigned).jsonObject
        val legacyPublic = JsonObject(
            root.getValue("public").jsonObject - "roleAssignmentGeneration",
        )
        val legacy = JsonObject(root + ("public" to legacyPublic))

        val restored = codec.decode(legacy.toString().encodeToByteArray())

        assertEquals(1L, restored.public.roleAssignmentGeneration)
        assertDecodeRejected(
            assigned.copy(
                public = assigned.public.copy(roleAssignmentGeneration = 0L),
            ),
        )
    }

    @Test
    fun currentSnapshotPreservesLaterAssignmentGeneration() {
        val later = assigned.copy(
            public = assigned.public.copy(roleAssignmentGeneration = 7L),
        )

        assertEquals(later, codec.decode(codec.encode(later)))
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
    fun retiredPrivateReviewFlagIsRejectedEvenDuringCharacterReveal() {
        val playerId = players.first().id
        val privateState = assigned.privatePerPlayer.getValue(playerId)
        val impossible = assigned.copy(
            phase = WhodunitPhase.CharacterReveal(0),
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
    fun simultaneousCharacterRevealRejectsLegacySequentialCursor() {
        val canonical = assigned.copy(phase = WhodunitPhase.CharacterReveal(0))
        assertEquals(canonical, codec.decode(codec.encode(canonical)))

        assertDecodeRejected(
            canonical.copy(phase = WhodunitPhase.CharacterReveal(1)),
        )
    }

    @Test
    fun phaseSpecificReadinessAndBriefingCursorCannotLeakIntoAnotherPhase() {
        assertDecodeRejected(
            assigned.copy(
                public = assigned.public.copy(briefingReady = setOf(players.first().id)),
            ),
        )
        assertDecodeRejected(
            assigned.copy(
                public = assigned.public.copy(rolesViewed = setOf(players.first().id)),
            ),
        )
        assertDecodeRejected(
            assigned.copy(
                phase = WhodunitPhase.RulesBriefing,
                public = assigned.public.copy(briefingCardIndex = 99),
            ),
        )
    }

    @Test
    fun aLaterRoundCannotResumeWithoutItsContiguousPriorClues() {
        assertDecodeRejected(
            assigned.copy(
                phase = WhodunitPhase.Round(3),
                public = assigned.public.copy(currentRound = 3),
            ),
        )
    }

    @Test
    fun classicFinalBallotMustUseTheExactCanonicalRosterAndCandidates() {
        val valid = classicFinalVoteState()
        assertEquals(valid, codec.decode(codec.encode(valid)))
        val collecting = valid.public.voteState as VoteState.Collecting

        listOf(
            collecting.copy(ballotPlayerIds = collecting.ballotPlayerIds.dropLast(1)),
            collecting.copy(ballotPlayerIds = collecting.ballotPlayerIds.reversed()),
            collecting.copy(candidatePlayerIds = collecting.candidatePlayerIds.dropLast(1)),
            collecting.copy(isSecondRound = true),
        ).forEach { invalidVote ->
            assertDecodeRejected(
                valid.copy(public = valid.public.copy(voteState = invalidVote)),
            )
        }
    }

    @Test
    fun revoteCandidatesNeedAtLeastTwoAndCanonicalTableOrder() {
        val valid = classicFinalVoteState()
        val ballot = (valid.public.voteState as VoteState.Collecting).ballotPlayerIds
        assertDecodeRejected(
            valid.copy(
                phase = WhodunitPhase.TiedRevote,
                public = valid.public.copy(
                    voteState = VoteState.Collecting(
                        isElimination = false,
                        ballotPlayerIds = ballot,
                        candidatePlayerIds = listOf(ballot.first()),
                        isSecondRound = true,
                    ),
                ),
            ),
        )
        assertDecodeRejected(
            valid.copy(
                phase = WhodunitPhase.TiedRevote,
                public = valid.public.copy(
                    voteState = VoteState.Tied(
                        tiedPlayerIds = listOf(ballot[1], ballot[0]),
                    ),
                ),
            ),
        )
    }

    @Test
    fun voteAndVerdictCannotBeAttachedToAnUnreachablePhaseOrKiller() {
        val killerId = assigned.hostOnly.killerId
        assertDecodeRejected(
            assigned.copy(
                public = assigned.public.copy(
                    voteState = VoteState.Resolved(killerId, wasKiller = true),
                ),
            ),
        )

        val terminal = classicFinalVoteState().copy(
            phase = WhodunitPhase.Reveal,
            public = classicFinalVoteState().public.copy(
                voteState = VoteState.Resolved(killerId, wasKiller = true),
                verdict = Verdict.PlayersWin("another-character"),
            ),
        )
        assertDecodeRejected(terminal)

        val finalVote = classicFinalVoteState()
        val validTerminal = finalVote.copy(
            phase = WhodunitPhase.Reveal,
            public = finalVote.public.copy(
                voteState = VoteState.Resolved(killerId, wasKiller = true),
                verdict = Verdict.PlayersWin(assigned.hostOnly.killerCharacterId.raw),
            ),
        )
        assertEquals(validTerminal, codec.decode(codec.encode(validTerminal)))
        assertDecodeRejected(
            validTerminal.copy(
                public = validTerminal.public.copy(
                    currentRound = 0,
                    revealedClues = emptyList(),
                ),
                hostOnly = validTerminal.hostOnly.copy(drawnClueIds = emptySet()),
            ),
        )
    }

    @Test
    fun earlyTerminationClearsPhaseLocalStateBeforeItCanBePersisted() {
        val acknowledged = WhodunitReducer.reduce(
            assigned,
            WhodunitAction.AcknowledgeIntro(players.first().id),
            reducerContext(),
        ).newState

        val ended = WhodunitReducer.reduce(
            acknowledged,
            WhodunitAction.EndGameEarly(withReveal = false),
            reducerContext(),
        ).newState

        assertEquals(WhodunitPhase.PostGame, ended.phase)
        assertEquals(emptySet(), ended.public.introAcknowledged)
        assertEquals(emptySet(), ended.public.briefingReady)
        assertEquals(emptySet(), ended.public.rolesViewed)
        assertEquals(0, ended.public.briefingCardIndex)
        WhodunitStateValidator.requireValid(ended)
    }

    @Test
    fun unassigned_terminal_state_accepts_only_the_exact_setup_disconnect_cancellation_shape() {
        val definition = WhodunitDefinition(json)
        val initial = definition.createInitialState(
            SessionConfig(
                sessionId = SessionId("unassigned-terminal-shape"),
                caseId = CaseId("snapshot-case"),
                modeId = WhodunitIds.ClassicVoteModeId,
                players = players,
                randomSeed = 7L,
            ),
        )
        val disconnected = WhodunitReducer.reduce(
            initial,
            WhodunitAction.MarkPlayerDisconnected(players.first().id),
            reducerContext(),
        ).newState
        val ended = WhodunitReducer.reduce(
            disconnected,
            WhodunitAction.ContinueWithoutPlayer(players.first().id),
            reducerContext(),
        ).newState

        WhodunitStateValidator.requireValid(ended)
        assertDecodeRejected(
            ended.copy(public = ended.public.copy(currentRound = 1)),
        )
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
    fun eliminatedAudienceCannotBeRestoredAsGameplayDisconnected() {
        val (valid, eliminated) = eliminationAudienceState()
        WhodunitStateValidator.requireValid(valid)

        val impossible = valid.copy(
            public = valid.public.copy(
                disconnectedPlayers = setOf(eliminated),
                paused = true,
            ),
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
        val completedClues = (1..3).map { round ->
            RevealedClue(ClueId("legacy-round-$round"), "Clue $round", round)
        }
        val legacy = assigned.copy(
            phase = WhodunitPhase.TiedRevote,
            public = assigned.public.copy(
                currentRound = 3,
                revealedClues = completedClues,
                voteState = VoteState.Tied(
                    tiedPlayerIds = listOf(players[0].id, players[1].id),
                    debateSecondsRemaining = 60,
                ),
            ),
            hostOnly = assigned.hostOnly.copy(
                drawnClueIds = completedClues.map { it.id }.toSet(),
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

        val explicitEmptyKillerDossier = assigned.copy(
            privatePerPlayer = assigned.privatePerPlayer + (
                assigned.hostOnly.killerId to assigned.privatePerPlayer
                    .getValue(assigned.hostOnly.killerId)
                    .copy(deflectionTargets = emptyList())
            ),
        )
        assertDecodeRejected(explicitEmptyKillerDossier)
    }

    @Test
    fun loadedCaseBoundaryAcceptsMatchingAssignedState() {
        WhodunitStateValidator.requireValidForCase(
            assigned,
            case = validatedCase(),
        )
    }

    @Test
    fun loadedCaseBoundaryRejectsStaleCaseAndUnknownAssignedCharacter() {
        assertFailsWith<IllegalArgumentException> {
            WhodunitStateValidator.requireValidForCase(
                assigned,
                case = validatedCase(caseId = "replacement-case"),
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
                case = validatedCase(),
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
                case = validatedCase(),
            )
        }
    }

    @Test
    fun loadedCaseBoundaryRejectsUnknownOrChangedClue() {
        val valid = stateWithFirstClue()
        val unknown = valid.copy(
            public = valid.public.copy(
                revealedClues = listOf(RevealedClue(ClueId("removed-clue"), "Removed", 1)),
            ),
            hostOnly = valid.hostOnly.copy(drawnClueIds = setOf(ClueId("removed-clue"))),
        )
        assertFailsWith<IllegalArgumentException> {
            WhodunitStateValidator.requireValidForCase(
                unknown,
                case = validatedCase(),
            )
        }

        val changedText = valid.copy(
            public = valid.public.copy(
                revealedClues = listOf(valid.public.revealedClues.single().copy(text = "Changed after save")),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            WhodunitStateValidator.requireValidForCase(
                changedText,
                case = validatedCase(),
            )
        }

        WhodunitStateValidator.requireValidForCase(
            valid,
            case = validatedCase(),
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

    private fun currentEnvelope(): JsonObject = json
        .parseToJsonElement(codec.encode(assigned).decodeToString())
        .jsonObject

    private fun stateWithFirstClue(): WhodunitState = WhodunitReducer.reduce(
        assigned.copy(
            phase = WhodunitPhase.Round(1),
            public = assigned.public.copy(currentRound = 1),
        ),
        WhodunitAction.RevealNextClue,
        reducerContext(),
    ).newState

    private fun classicFinalVoteState(): WhodunitState {
        val clues = (1..3).map { round ->
            RevealedClue(ClueId("structural-round-$round"), "Clue $round", round)
        }
        val ballot = players.map { it.id }
        return assigned.copy(
            phase = WhodunitPhase.FinalVote,
            public = assigned.public.copy(
                currentRound = 3,
                revealedClues = clues,
                voteState = VoteState.Collecting(
                    isElimination = false,
                    ballotPlayerIds = ballot,
                ),
            ),
            hostOnly = assigned.hostOnly.copy(drawnClueIds = clues.map { it.id }.toSet()),
        )
    }

    private fun reducerContext() = WhodunitReducerContext(
        clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        random = RandomSource.seeded(7L),
        case = validatedWhodunitCaseForTest(case(), caseId = "snapshot-case"),
    )

    private fun validatedCase(
        caseId: String = "snapshot-case",
        characterCount: Int = 4,
    ) = validatedWhodunitCaseForTest(case(characterCount), caseId = caseId)

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
            reducerContext(),
        ).newState
    }

    private fun eliminationAudienceState(): Pair<WhodunitState, PlayerId> {
        val resolved = eliminationStateAfterInnocentEliminations(1)
        val eliminated = resolved.public.eliminatedPlayers.single()
        val advanced = WhodunitReducer.reduce(
            resolved,
            WhodunitAction.AcknowledgeRevealCard,
            eliminationReducerContext(),
        ).newState
        return advanced to eliminated
    }

    private fun eliminationStateAfterInnocentEliminations(count: Int): WhodunitState {
        val roster = (0 until 5).map { index ->
            Player(PlayerId("e${index + 1}"), "Elimination ${index + 1}", index)
        }
        val context = eliminationReducerContext()
        fun step(state: WhodunitState, action: WhodunitAction): WhodunitState =
            WhodunitReducer.reduce(state, action, context).newState

        var state = WhodunitDefinition(json).createInitialState(
            SessionConfig(
                sessionId = SessionId("snapshot-elimination"),
                caseId = CaseId("snapshot-elimination"),
                modeId = WhodunitIds.EliminationModeId,
                players = roster,
                randomSeed = 17L,
            ),
        )
        state = step(state, WhodunitAction.AssignRoles(17L))
        roster.forEach { state = step(state, WhodunitAction.AcknowledgeIntro(it.id)) }
        state = step(state, WhodunitAction.AdvanceFromIntro)
        roster.forEach { state = step(state, WhodunitAction.AcknowledgeBriefing(it.id)) }
        for (index in 1..4) state = step(state, WhodunitAction.AdvanceBriefingCard(index))
        roster.forEach { player ->
            val generation = state.public.roleAssignmentGeneration
            state = step(state, WhodunitAction.StartCharacterReveal(player.id, generation))
            state = step(state, WhodunitAction.CompleteCharacterReveal(player.id, generation))
        }
        state = step(state, WhodunitAction.AdvanceFromCharacterReveal)

        repeat(count) { index ->
            state = step(state, WhodunitAction.RevealNextClue)
            state = step(state, WhodunitAction.StartDiscussionTimer(180))
            state = step(state, WhodunitAction.AdvanceFromDiscussion)

            val innocent = roster.first { player ->
                player.id != state.hostOnly.killerId &&
                    player.id !in state.public.eliminatedPlayers
            }.id
            val ballot = (state.public.voteState as VoteState.Collecting).ballotPlayerIds
            ballot.forEach { voter ->
                state = step(
                    state,
                    if (voter == innocent) {
                        WhodunitAction.AbstainVote(voter)
                    } else {
                        WhodunitAction.CastVote(voter, innocent)
                    },
                )
            }
            state = step(state, WhodunitAction.CloseVote)
            if (index < count - 1) {
                state = step(state, WhodunitAction.AcknowledgeRevealCard)
            }
        }
        return state
    }

    private fun eliminationReducerContext() = WhodunitReducerContext(
        clock = FakeClock(Instant.fromEpochMilliseconds(0)),
        random = RandomSource.seeded(17L),
        case = validatedWhodunitCaseForTest(
            case(characterCount = 5),
            caseId = "snapshot-elimination",
        ),
    )

    private fun case(characterCount: Int = 4): WhodunitCase {
        val characters = (1..characterCount).map { index -> character("c$index") }
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
