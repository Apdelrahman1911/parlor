package com.parlor.games.whodunit.domain.rules

import com.parlor.core.ids.CharacterId
import com.parlor.core.ids.ClueId
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.CluePools
import com.parlor.games.whodunit.content.GuiltyBrief
import com.parlor.games.whodunit.content.InnocentBrief
import com.parlor.games.whodunit.content.Round
import com.parlor.games.whodunit.content.RoundConfig
import com.parlor.games.whodunit.content.StructuredAction
import com.parlor.games.whodunit.content.TimelineEntry
import com.parlor.games.whodunit.content.WhodunitCase
import kotlin.test.Test
import kotlin.test.assertEquals

class WhodunitPolicyGoldenTest {
    @Test
    fun clueSelectionHasStableCrossPlatformGoldenSequences() {
        val sequences = listOf(0L, 1L, 7L, 91L, -1L).associateWith(::clueSequence)

        assertEquals(
            mapOf(
                0L to listOf("public-a", "contra-b", "final-a"),
                1L to listOf("public-a", "red-a", "final-b"),
                7L to listOf("point-c", "contra-a", "final-a"),
                91L to listOf("point-a", "contra-b", "final-a"),
                -1L to listOf("public-b", "point-a", "final-a"),
            ),
            sequences,
        )
    }

    @Test
    fun roundResolutionPreservesExactNearestTieAndDefaultRules() {
        val case = case().copy(
            roundConfigByPlayerCount = mapOf(
                "4" to RoundConfig(listOf(round("four-1", 40), round("four-2", 41))),
                "6" to RoundConfig(listOf(round("six-1", 60))),
                "8" to RoundConfig(
                    listOf(round("eight-1", 80), round("eight-2", 81), round("eight-3", 82)),
                ),
            ),
        )

        assertEquals(60, WhodunitRoundPolicy.discussionSeconds(case, 1, 6))
        assertEquals(40, WhodunitRoundPolicy.discussionSeconds(case, 1, 5))
        assertEquals(41, WhodunitRoundPolicy.discussionSeconds(case, 2, 6))
        assertEquals(82, WhodunitRoundPolicy.discussionSeconds(case, 3, 6))
        assertEquals(180, WhodunitRoundPolicy.discussionSeconds(case, 4, 6))
        assertEquals(180, WhodunitRoundPolicy.discussionSeconds(case, 0, 6))
    }

    private fun clueSequence(seed: Long): List<String> {
        val case = case()
        val drawn = linkedSetOf<ClueId>()
        return (1..3).map { roundIndex ->
            val clue = requireNotNull(
                WhodunitCluePolicy.select(
                    case = case,
                    killerCharacterId = CharacterId("c1"),
                    modeId = WhodunitIds.ClassicVoteModeId,
                    playerCount = 4,
                    randomSeed = seed,
                    roundIndex = roundIndex,
                    drawnClueIds = drawn,
                ),
            )
            drawn += ClueId(clue.id)
            clue.id
        }
    }

    private fun case(): WhodunitCase = WhodunitCase(
        publicIntro = "Intro",
        bedrockClues = listOf("Bedrock"),
        characters = (1..4).map { character("c$it") },
        cluePools = CluePools(
            publicUniversal = listOf(clue("public-a"), clue("public-b")),
            killerPointing = mapOf(
                "c1" to listOf(clue("point-a"), clue("point-b"), clue("point-c")),
            ),
            redHerring = mapOf("c1" to listOf(clue("red-a"))),
            contradiction = mapOf("c1" to listOf(clue("contra-a"), clue("contra-b"))),
            finalStrong = mapOf("c1" to listOf(clue("final-a"), clue("final-b"))),
        ),
        revealNarratives = (1..4).associate { "c$it" to "Reveal $it" },
    )

    private fun clue(id: String) = Clue(id, id)

    private fun round(id: String, seconds: Int) = Round(
        id = id,
        titleCardText = id,
        taglineText = id,
        cluesToReveal = 1,
        structuredAction = StructuredAction.NONE,
        discussionSeconds = seconds,
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
