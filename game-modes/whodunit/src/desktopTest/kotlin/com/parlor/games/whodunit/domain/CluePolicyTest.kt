package com.parlor.games.whodunit.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.PlayerId
import com.parlor.core.random.RandomSource
import com.parlor.core.time.FakeClock
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.CluePools
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.content.GuiltyBrief
import com.parlor.games.whodunit.content.InnocentBrief
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.phase.WhodunitPhase
import com.parlor.games.whodunit.domain.reducer.WhodunitReducer
import com.parlor.games.whodunit.domain.reducer.WhodunitReducerContext
import com.parlor.games.whodunit.domain.state.WhodunitHostOnly
import com.parlor.games.whodunit.domain.state.WhodunitPublic
import com.parlor.games.whodunit.domain.state.WhodunitState
import kotlinx.datetime.Instant
import kotlin.test.Test

/**
 * Clue-selection policy tests. Asserts the late-game pool is a weighted union
 * of multiple pools — NOT a guaranteed pull from `finalStrong[killer]` — so
 * the final round doesn't telegraph the killer.
 *
 * Determinism: each test pins seeds [1..50] and drives the reducer through
 * `RevealNextClue` actions. Same seed → same sequence; different seeds
 * produce at least one differing sequence.
 */
class CluePolicyTest {

    private val killerCharId = "k-1"
    private val killerPlayerId = PlayerId("p-killer")
    private val players: List<Player> = (1..5).map { idx ->
        Player(PlayerId("p-$idx"), "Player $idx", seat = idx)
    } + Player(killerPlayerId, "Killer", seat = 6)

    private val finalStrongClues = listOf(
        clue("fs-1", "Final strong 1"),
        clue("fs-2", "Final strong 2"),
        clue("fs-3", "Final strong 3"),
    )
    private val killerPointingClues = listOf(
        clue("kp-1", "Killer pointing 1"),
        clue("kp-2", "Killer pointing 2"),
        clue("kp-3", "Killer pointing 3"),
    )
    private val contradictionClues = listOf(
        clue("ct-1", "Contradiction 1"),
        clue("ct-2", "Contradiction 2"),
    )
    private val redHerringClues = listOf(
        clue("rh-1", "Red herring 1"),
        clue("rh-2", "Red herring 2"),
    )
    private val publicUniversalClues = listOf(
        clue("pu-1", "Public 1"),
        clue("pu-2", "Public 2"),
    )

    private val case = WhodunitCase(
        publicIntro = "intro",
        bedrockClues = emptyList(),
        characters = listOf(
            character(killerCharId, "Killer Cassidy"),
            character("c-2", "Other"),
        ),
        cluePools = CluePools(
            publicUniversal = publicUniversalClues,
            killerPointing = mapOf(killerCharId to killerPointingClues),
            contradiction = mapOf(killerCharId to contradictionClues),
            redHerring = mapOf(killerCharId to redHerringClues),
            finalStrong = mapOf(killerCharId to finalStrongClues),
        ),
        revealNarratives = emptyMap(),
    )

    @Test
    fun lastRoundIsNotAlwaysFinalStrong() {
        val finalStrongIds = finalStrongClues.map { it.id }.toSet()
        var sawNonFinalStrong = false
        for (seed in 1L..50L) {
            val clueId = pickLateRoundClueId(seed)
            if (clueId !in finalStrongIds) {
                sawNonFinalStrong = true
                break
            }
        }
        assertThat(sawNonFinalStrong).isTrue()
    }

    @Test
    fun sameSeedProducesSameLateClue() {
        for (seed in 1L..50L) {
            val first = pickLateRoundClueId(seed)
            val second = pickLateRoundClueId(seed)
            assertThat(second).isEqualTo(first)
        }
    }

    @Test
    fun differentSeedsProduceAtLeastOneDifferentSequence() {
        val sequences = (1L..50L).map { seed -> driveCluesThroughFinalRound(seed) }
        val distinct = sequences.toSet()
        // At least two distinct sequences proves variation across seeds.
        assertThat(distinct.size > 1).isTrue()
    }

    @Test
    fun emptyLateGamePoolDoesNotCrash() {
        // Build a case whose pools are entirely empty for the killer.
        val emptyCase = case.copy(
            cluePools = CluePools(
                publicUniversal = emptyList(),
                killerPointing = mapOf(killerCharId to emptyList()),
                contradiction = mapOf(killerCharId to emptyList()),
                redHerring = mapOf(killerCharId to emptyList()),
                finalStrong = mapOf(killerCharId to emptyList()),
            ),
        )
        val ctx = ctxFor(emptyCase)
        val state = stateAtRound(roundIndex = 4, hostSeed = 1L)

        val reduction = WhodunitReducer.reduce(state, WhodunitAction.RevealNextClue, ctx)

        // With nothing to draw the reducer must be a no-op — same state, same
        // drawn set — not throw.
        assertThat(reduction.newState.public.revealedClues).isEqualTo(emptyList())
        assertThat(reduction.newState.hostOnly.drawnClueIds).isEqualTo(emptySet())
    }

    @Test
    fun drawnCluesAreNotPickedAgain() {
        // Pre-mark all but one clue as drawn; that one must come out.
        val onlyRemainingId = ClueId("kp-3")
        val allOtherIds: Set<ClueId> = (finalStrongClues + killerPointingClues + contradictionClues +
            redHerringClues + publicUniversalClues)
            .map { ClueId(it.id) }
            .toSet() - onlyRemainingId

        val ctx = ctxFor(case)
        val state = stateAtRound(roundIndex = 4, drawn = allOtherIds, hostSeed = 7L)

        val reduction = WhodunitReducer.reduce(state, WhodunitAction.RevealNextClue, ctx)
        val revealed = reduction.newState.public.revealedClues.last()
        assertThat(revealed.id).isEqualTo(onlyRemainingId)
    }

    // ----- helpers ------------------------------------------------------------

    /**
     * Drive the reducer through round 4 (the last round under the existing
     * isLastRound rule: playerCount=6, lastRound when idx>=4) and return the
     * single clue picked. The clue picker derives its random from
     * `state.hostOnly.randomSeed`, so we vary that — not `ctx.random` — to
     * control the seed.
     */
    private fun pickLateRoundClueId(seed: Long): String {
        val ctx = ctxFor(case)
        val state = stateAtRound(roundIndex = 4, hostSeed = seed)
        val reduction = WhodunitReducer.reduce(state, WhodunitAction.RevealNextClue, ctx)
        return reduction.newState.public.revealedClues.last().id.raw
    }

    /**
     * Drive multiple successive `RevealNextClue` actions through the late round
     * to get a *sequence* (so two seeds that pick the same first clue can still
     * be distinguished by later picks). Stops when no more clues come out.
     */
    private fun driveCluesThroughFinalRound(seed: Long): List<String> {
        val ctx = ctxFor(case)
        var state = stateAtRound(roundIndex = 4, hostSeed = seed)
        val picks = mutableListOf<String>()
        repeat(8) {
            val reduction = WhodunitReducer.reduce(state, WhodunitAction.RevealNextClue, ctx)
            val newPicks = reduction.newState.public.revealedClues
            if (newPicks.size == state.public.revealedClues.size) return@repeat
            picks += newPicks.last().id.raw
            state = reduction.newState
        }
        return picks
    }

    private fun ctxFor(payload: WhodunitCase): WhodunitReducerContext =
        WhodunitReducerContext(
            clock = FakeClock(Instant.fromEpochMilliseconds(0)),
            random = RandomSource.seeded(0L),
            case = payload,
        )

    private fun stateAtRound(
        roundIndex: Int,
        drawn: Set<ClueId> = emptySet(),
        hostSeed: Long = 0L,
    ): WhodunitState = WhodunitState(
        public = WhodunitPublic(
            caseId = CaseId("test-case"),
            modeId = WhodunitIds.ClassicVoteModeId,
            playersAtTable = players,
            currentRound = roundIndex,
        ),
        privatePerPlayer = emptyMap(),
        hostOnly = WhodunitHostOnly(
            killerId = killerPlayerId,
            killerCharacterId = CharacterId(killerCharId),
            randomSeed = hostSeed,
            seatToCharacter = emptyMap(),
            redHerringTargets = emptyList(),
            drawnClueIds = drawn,
        ),
        phase = WhodunitPhase.Round(roundIndex),
        players = players,
    )

    private fun clue(id: String, text: String): Clue = Clue(id = id, text = text)

    private fun character(id: String, displayName: String): Character = Character(
        id = id,
        displayName = displayName,
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
