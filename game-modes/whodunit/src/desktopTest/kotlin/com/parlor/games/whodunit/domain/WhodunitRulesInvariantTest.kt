package com.parlor.games.whodunit.domain

import com.parlor.core.ids.CaseId
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
import com.parlor.games.whodunit.domain.projection.WhodunitProjectionPolicy
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.testing.validatedWhodunitCaseForTest
import com.parlor.games.whodunit.domain.rules.WhodunitRules
import com.parlor.games.whodunit.domain.state.PlayerRole
import com.parlor.games.whodunit.domain.state.VoteState
import com.parlor.games.whodunit.domain.state.WhodunitState
import com.parlor.games.whodunit.domain.state.WhodunitStateValidator
import com.parlor.networking.room.RoomInputPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class WhodunitRulesInvariantTest {
    private val json = Json { encodeDefaults = true }
    private val definition = WhodunitDefinition(json)
    private val case = caseWithEightCharacters()

    @Test
    fun everySupportedCountAndSeedProducesOneDeterministicKillerAndUniqueDossiers() {
        val modeCounts = mapOf(
            WhodunitIds.ClassicVoteModeId to (4..8),
            WhodunitIds.EliminationModeId to (5..8),
        )
        modeCounts.forEach { (modeId, counts) ->
            counts.forEach { count ->
                (-50L..50L).forEach { seed ->
                    val first = assignedState(modeId, count, seed)
                    val second = assignedState(modeId, count, seed)

                    assertEquals(first, second)
                    assertSnapshotBoundaries(first)
                    assertEquals(count, first.privatePerPlayer.size)
                    assertEquals(count, first.hostOnly.seatToCharacter.values.toSet().size)
                    assertEquals(
                        1,
                        first.privatePerPlayer.values.count { it.role == PlayerRole.Killer },
                    )
                    assertEquals(
                        first.hostOnly.killerCharacterId,
                        first.privatePerPlayer.getValue(first.hostOnly.killerId).characterId,
                    )
                    val killerPrivate = first.privatePerPlayer.getValue(first.hostOnly.killerId)
                    assertEquals(first.hostOnly.redHerringTargets, killerPrivate.deflectionTargets)
                    assertTrue(
                        first.hostOnly.seatToCharacter.values.containsAll(
                            killerPrivate.deflectionTargets,
                        ),
                    )
                    assertTrue(killerPrivate.characterId !in killerPrivate.deflectionTargets)
                    first.privatePerPlayer
                        .filterKeys { it != first.hostOnly.killerId }
                        .values
                        .forEach { assertTrue(it.deflectionTargets.isEmpty()) }
                }
            }
        }
    }

    @Test
    fun seededKillerSelectionHasNoMaterialSeatBias() {
        val roster = players(4)
        val frequencies = roster.associate { it.id to 0 }.toMutableMap()
        repeat(4_000) { rawSeed ->
            val state = assignedState(
                WhodunitIds.ClassicVoteModeId,
                roster.size,
                rawSeed.toLong(),
            )
            val killerId = state.hostOnly.killerId
            frequencies[killerId] = frequencies.getValue(killerId) + 1
        }

        frequencies.forEach { (playerId, count) ->
            assertTrue(
                count in 850..1_150,
                "killer frequency for ${playerId.raw} was $count/4000",
            )
        }
    }

    @Test
    fun invalidCountModeIdentityAndSeatAreRejectedBeforeSessionStarts() {
        val valid = players(4)
        val invalidRosters = listOf(
            WhodunitIds.ClassicVoteModeId to players(3),
            WhodunitIds.EliminationModeId to players(4),
            WhodunitIds.ClassicVoteModeId to players(9),
            WhodunitIds.ClassicVoteModeId to valid.toMutableList().also {
                it[1] = it[1].copy(id = it[0].id)
            },
            WhodunitIds.ClassicVoteModeId to valid.toMutableList().also {
                it[1] = it[1].copy(seat = it[0].seat)
            },
            WhodunitIds.ClassicVoteModeId to valid.map { it.copy(seat = it.seat + 1) },
            WhodunitIds.ClassicVoteModeId to valid.toMutableList().also {
                it[1] = it[1].copy(displayName = "   ")
            },
        )

        invalidRosters.forEachIndexed { index, (modeId, roster) ->
            assertFailsWith<IllegalArgumentException>("invalid roster $index") {
                definition.createInitialState(config(modeId, roster, seed = index.toLong()))
            }
        }
    }

    @Test
    fun displayNamesMustBeCanonicalBoundedAndSafeAtTheDomainBoundary() {
        val invalidNames = listOf(
            " Player 1",
            "Player 1 ",
            "A".repeat(RoomInputPolicy.MAX_DISPLAY_NAME_LENGTH + 1),
            "Player\u0000One",
            "Player\u202eOne",
        )

        invalidNames.forEachIndexed { index, name ->
            val roster = players(4).toMutableList().also {
                it[index % it.size] = it[index % it.size].copy(displayName = name)
            }
            assertFalse(
                WhodunitRules.isValidRoster(WhodunitIds.ClassicVoteModeId, roster),
                "unsafe display name at index $index was accepted",
            )
            assertFailsWith<IllegalArgumentException> {
                definition.createInitialState(config(WhodunitIds.ClassicVoteModeId, roster, 1L))
            }
        }

        val international = players(4).toMutableList().also {
            it[0] = it[0].copy(displayName = "عبد الرحمن")
            it[1] = it[1].copy(
                displayName = "A".repeat(RoomInputPolicy.MAX_DISPLAY_NAME_LENGTH),
            )
            it[2] = it[2].copy(displayName = "Player 🎲")
        }
        assertTrue(WhodunitRules.isValidRoster(WhodunitIds.ClassicVoteModeId, international))
    }

    @Test
    fun exactDuplicateDisplayNamesAreRejectedAtTheAuthoritativeBoundary() {
        val duplicateNames = players(4).toMutableList().also {
            it[1] = it[1].copy(displayName = it[0].displayName)
        }

        assertFalse(
            WhodunitRules.isValidRoster(WhodunitIds.ClassicVoteModeId, duplicateNames),
        )
        assertFailsWith<IllegalArgumentException> {
            definition.createInitialState(
                config(WhodunitIds.ClassicVoteModeId, duplicateNames, seed = 1L),
            )
        }

        val assigned = assignedState(WhodunitIds.ClassicVoteModeId, 4, seed = 7L)
        val publicProjection = WhodunitProjectionPolicy.toPublic(assigned).state
        val duplicateProjectedPlayers = publicProjection.players.toMutableList().also {
            it[1] = it[1].copy(displayName = it[0].displayName)
        }
        assertFalse(
            WhodunitStateValidator.isValidPeerProjection(
                publicState = publicProjection.copy(
                    players = duplicateProjectedPlayers,
                    public = publicProjection.public.copy(
                        playersAtTable = duplicateProjectedPlayers,
                    ),
                ),
                ownPrivate = assigned.privatePerPlayer.getValue(assigned.players.first().id),
                selfPlayerId = assigned.players.first().id,
            ),
            "a privacy-safe peer projection still requires distinct roster labels",
        )

        val caseVariants = players(4).toMutableList().also {
            it[0] = it[0].copy(displayName = "Alice")
            it[1] = it[1].copy(displayName = "alice")
        }
        assertTrue(WhodunitRules.isValidRoster(WhodunitIds.ClassicVoteModeId, caseVariants))
    }

    @Test
    fun privacyRerollChangesKillerAndEveryPreviouslyViewedDossier() {
        for (count in 4..8) {
            for (seed in 0L..100L) {
                val assigned = assignedState(WhodunitIds.ClassicVoteModeId, count, seed)
                val reveal = assigned.copy(phase = WhodunitPhase.CharacterReveal(0))
                val rerolled = reduce(reveal, WhodunitAction.RequestReroll).newState

                assertSnapshotBoundaries(rerolled)
                assertEquals(WhodunitPhase.CharacterReveal(0), rerolled.phase)
                assertNotEquals(assigned.hostOnly.killerId, rerolled.hostOnly.killerId)
                assigned.players.forEach { player ->
                    assertNotEquals(
                        assigned.hostOnly.seatToCharacter[player.id],
                        rerolled.hostOnly.seatToCharacter[player.id],
                        "seed=$seed count=$count player=${player.id.raw}",
                    )
                }
                assertTrue(rerolled.public.rolesViewed.isEmpty())
            }
        }
    }

    @Test
    fun modelDrivePreservesInvariantsForEveryShippingModeAndSupportedCount() {
        val modeCounts = mapOf(
            WhodunitIds.ClassicVoteModeId to (4..8),
            WhodunitIds.EliminationModeId to (5..8),
        )
        modeCounts.forEach { (modeId, counts) ->
            counts.forEach { count ->
                for (seed in 0L..10L) {
                    val roster = players(count)
                    var state = definition.createInitialState(config(modeId, roster, seed))
                    assertSnapshotBoundaries(state)
                    state = submitValid(state, WhodunitAction.AssignRoles(seed))
                    roster.forEach { player ->
                        state = submitValid(state, WhodunitAction.AcknowledgeIntro(player.id))
                    }
                    state = submitValid(state, WhodunitAction.AdvanceFromIntro)
                    roster.forEach { player ->
                        state = submitValid(state, WhodunitAction.AcknowledgeBriefing(player.id))
                    }
                    for (card in 1..4) {
                        state = submitValid(state, WhodunitAction.AdvanceBriefingCard(card))
                    }
                    val assignmentGeneration = state.public.roleAssignmentGeneration
                    roster.forEach { player ->
                        state = submitValid(
                            state,
                            WhodunitAction.StartCharacterReveal(player.id, assignmentGeneration),
                        )
                        state = submitValid(
                            state,
                            WhodunitAction.CompleteCharacterReveal(player.id, assignmentGeneration),
                        )
                    }
                    state = submitValid(state, WhodunitAction.AdvanceFromCharacterReveal)

                    val rounds = requireNotNull(WhodunitRules.maximumRoundCount(modeId, count)) {
                        "Supported mode $modeId with $count players must define a round count"
                    }
                    val roundsToDrive = if (modeId == WhodunitIds.EliminationModeId) 1 else rounds
                    repeat(roundsToDrive) {
                        state = submitValid(state, WhodunitAction.RevealNextClue)
                        state = submitValid(state, WhodunitAction.StartDiscussionTimer(180))
                        state = submitValid(state, WhodunitAction.AdvanceFromDiscussion)
                    }

                    val vote = state.public.voteState as VoteState.Collecting
                    vote.ballotPlayerIds.forEach { voter ->
                        state = if (voter == state.hostOnly.killerId) {
                            submitValid(state, WhodunitAction.AbstainVote(voter))
                        } else {
                            submitValid(
                                state,
                                WhodunitAction.CastVote(voter, state.hostOnly.killerId),
                            )
                        }
                    }
                    state = submitValid(state, WhodunitAction.CloseVote)
                    assertEquals(WhodunitPhase.Reveal, state.phase)
                    assertTrue(state.public.verdict is com.parlor.games.whodunit.domain.event.Verdict.PlayersWin)

                    val innocent = state.privatePerPlayer.entries.first {
                        it.value.role == PlayerRole.Innocent
                    }
                    assertFalse(
                        WhodunitStateValidator.isValidPeerProjection(
                            publicState = WhodunitProjectionPolicy.toPublic(state).state,
                            ownPrivate = innocent.value.copy(role = PlayerRole.Killer),
                            selfPlayerId = innocent.key,
                        ),
                        "terminal peer snapshot must agree with the receiving player's dossier",
                    )
                }
            }
        }
    }

    private fun assignedState(
        modeId: com.parlor.core.ids.ModeId,
        count: Int,
        seed: Long,
    ): WhodunitState {
        val initial = definition.createInitialState(config(modeId, players(count), seed))
        val assigned = reduce(initial, WhodunitAction.AssignRoles(seed)).newState
        assertEquals(WhodunitPhase.PublicIntro, assigned.phase)
        return assigned
    }

    private fun reduce(state: WhodunitState, action: WhodunitAction) =
        WhodunitReducer.reduce(
            state,
            action,
            WhodunitReducerContext(
                clock = FakeClock(Instant.fromEpochMilliseconds(0)),
                random = RandomSource.seeded(1L),
                case = validatedWhodunitCaseForTest(case, caseId = "rules-case"),
            ),
        )

    private fun submitValid(state: WhodunitState, action: WhodunitAction): WhodunitState =
        reduce(state, action).newState.also(::assertSnapshotBoundaries)

    /** Validates each legal reducer state at canonical and peer trust boundaries. */
    private fun assertSnapshotBoundaries(state: WhodunitState) {
        WhodunitStateValidator.requireValid(state)
        val publicState = WhodunitProjectionPolicy.toPublic(state).state
        state.players.forEach { player ->
            val ownPrivate = WhodunitProjectionPolicy.toPlayer(state, player.id)
                .state
                .privatePerPlayer[player.id]
            assertTrue(
                WhodunitStateValidator.isValidPeerProjection(
                    publicState = publicState,
                    ownPrivate = ownPrivate,
                    selfPlayerId = player.id,
                ),
                "peer Whodunit projection is invalid in ${state.phase} for ${player.id.raw}",
            )
        }
    }

    private fun config(
        modeId: com.parlor.core.ids.ModeId,
        players: List<Player>,
        seed: Long,
    ) = SessionConfig(
        sessionId = SessionId("rules-$seed-${players.size}"),
        caseId = CaseId("rules-case"),
        modeId = modeId,
        players = players,
        randomSeed = seed,
    )

    private fun players(count: Int): List<Player> = (0 until count).map { index ->
        Player(PlayerId("p${index + 1}"), "Player ${index + 1}", seat = index)
    }

    private fun caseWithEightCharacters(): WhodunitCase {
        val characters = (1..8).map { index -> character("c$index", index) }
        val pools = characters.associate { character ->
            character.id to (1..8).map { clueIndex ->
                Clue("${character.id}-clue-$clueIndex", "Clue $clueIndex for ${character.id}")
            }
        }
        return WhodunitCase(
            publicIntro = "Intro",
            bedrockClues = listOf("Bedrock"),
            characters = characters,
            cluePools = CluePools(
                publicUniversal = listOf(Clue("public-clue", "Public clue")),
                killerPointing = pools.mapValues { (_, clues) -> clues.take(3) },
                redHerring = pools.mapValues { (_, clues) -> clues.drop(3).take(1) },
                contradiction = pools.mapValues { (_, clues) -> clues.drop(4).take(1) },
                finalStrong = pools.mapValues { (_, clues) -> clues.drop(5).take(2) },
            ),
            revealNarratives = characters.associate { it.id to "Reveal ${it.id}" },
        )
    }

    private fun character(id: String, index: Int) = Character(
        id = id,
        displayName = "Character $index",
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
            deflectionTargets = (1..8)
                .map { "c$it" }
                .filterNot { it == id },
            panicMove = "Panic",
        ),
    )
}
