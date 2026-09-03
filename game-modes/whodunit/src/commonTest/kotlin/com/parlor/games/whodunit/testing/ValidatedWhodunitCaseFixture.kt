package com.parlor.games.whodunit.testing

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.IntRangePair
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.content.WhodunitCase
import com.parlor.games.whodunit.content.WhodunitPayloadValidator
import com.parlor.games.whodunit.content.Character
import com.parlor.games.whodunit.content.Clue
import com.parlor.games.whodunit.content.CluePools
import com.parlor.games.whodunit.content.GuiltyBrief
import com.parlor.games.whodunit.content.InnocentBrief
import com.parlor.games.whodunit.content.Round
import com.parlor.games.whodunit.content.RoundConfig
import com.parlor.games.whodunit.content.StructuredAction
import com.parlor.games.whodunit.content.TimelineEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Creates a [ValidatedCase] token through the same envelope and payload
 * validators used by production. Reducer tests must therefore use playable
 * content rather than fixtures that production admission would reject.
 */
internal fun validatedWhodunitCaseForTest(
    payload: WhodunitCase,
    caseId: String,
    supportedPlayerCounts: IntRange = 4..payload.characters.size,
    supportedModes: List<String> = buildList {
        if (supportedPlayerCounts.any { it in 4..8 }) {
            add(WhodunitIds.ClassicVoteModeId.raw)
        }
        if (supportedPlayerCounts.any { it in 5..8 }) {
            add(WhodunitIds.EliminationModeId.raw)
        }
    },
): ValidatedCase<WhodunitCase> {
    val json = Json { encodeDefaults = true }
    val envelope = CaseEnvelope(
        schemaVersion = 1,
        caseId = caseId,
        title = "Test case",
        version = SemVer(1, 0, 0),
        minimumAppVersion = SemVer(1, 0, 0),
        gameId = WhodunitIds.GameId.raw,
        supportedPlayerCounts = IntRangePair.of(supportedPlayerCounts),
        supportedModes = supportedModes,
        language = "en",
        theme = "test",
        estimatedDuration = IntRangePair(1, 1),
        payload = json.encodeToJsonElement(WhodunitCase.serializer(), payload),
    )
    val validator = DefaultCaseValidator(
        json = json,
        knownSchemaVersion = 1,
        installedAppVersion = SemVer(1, 0, 0),
        gameRegistry = DefaultGameRegistry(listOf(WhodunitDefinition(json))),
    )
    return when (
        val result = validator.validate(
            json.encodeToString(CaseEnvelope.serializer(), envelope),
            WhodunitPayloadValidator(json),
        )
    ) {
        is Result.Success -> result.data
        is Result.Failure -> error("Invalid Whodunit test envelope: ${result.error}")
    }
}

/** A compact, internally consistent case for peer-boundary and lifecycle tests. */
internal fun whodunitPeerCaseForTest(
    caseId: String = "last-dinner",
): ValidatedCase<WhodunitCase> {
    val ids = listOf("chef", "heir", "doctor", "guest")
    fun character(id: String) = Character(
        id = id,
        displayName = id,
        relationshipToVictim = "relationship",
        publicIdentity = "identity",
        publicMotive = "motive",
        privateSecret = "secret",
        innocentBrief = InnocentBrief("verdict", "alibi", "goal", "free", "hide"),
        guiltyBrief = GuiltyBrief(
            verdictLine = "verdict",
            method = "method",
            timeline = listOf(TimelineEntry("time", "action")),
            fakeAlibi = "alibi",
            deflectionTargets = ids.filterNot { it == id },
            panicMove = "panic",
        ),
    )
    fun keyedClue(prefix: String, id: String, index: Int) =
        Clue("$prefix-$id-$index", "$prefix clue $index for $id")
    val rounds = (1..3).map { round ->
        Round(
            id = "round-$round",
            titleCardText = "Round $round",
            taglineText = "Tagline $round",
            cluesToReveal = 1,
            structuredAction = StructuredAction.NONE,
            discussionSeconds = 180,
        )
    }
    return validatedWhodunitCaseForTest(
        payload = WhodunitCase(
            publicIntro = "Intro",
            bedrockClues = listOf("Bedrock"),
            characters = ids.map(::character),
            cluePools = CluePools(
                publicUniversal = listOf(Clue("public-one", "Public clue")),
                killerPointing = ids.associateWith { id ->
                    (1..3).map { keyedClue("pointing", id, it) }
                },
                redHerring = ids.associateWith { id ->
                    listOf(keyedClue("red", id, 1))
                },
                contradiction = ids.associateWith { id ->
                    listOf(keyedClue("contradiction", id, 1))
                },
                finalStrong = ids.associateWith { id ->
                    (1..2).map { keyedClue("final", id, it) }
                },
            ),
            revealNarratives = ids.associateWith { "Reveal $it" },
            roundConfigByPlayerCount = mapOf("4" to RoundConfig(rounds)),
        ),
        caseId = caseId,
        supportedPlayerCounts = 4..4,
    )
}
