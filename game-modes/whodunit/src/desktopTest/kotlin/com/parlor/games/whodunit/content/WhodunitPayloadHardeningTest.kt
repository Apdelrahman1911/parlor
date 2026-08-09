package com.parlor.games.whodunit.content

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.IntRangePair
import com.parlor.core.result.Result
import com.parlor.games.whodunit.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
class WhodunitPayloadHardeningTest {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }
    private val validator = WhodunitPayloadValidator(json)

    @Test
    fun blankClueIdThatWouldCrashClueIdConstructionIsRejected() = runTest {
        val (envelope, payload) = loadCase()
        val invalid = payload.copy(
            cluePools = payload.cluePools.copy(
                publicUniversal = payload.cluePools.publicUniversal.mapIndexed { index, clue ->
                    if (index == 0) clue.copy(id = "") else clue
                },
            ),
        )

        assertRejected(envelope.withPayload(invalid))
    }

    @Test
    fun blankNestedTimelineAndRevealTextAreRejected() = runTest {
        val (envelope, payload) = loadCase()
        val firstCharacter = payload.characters.first()
        val blankTimeline = payload.copy(
            characters = listOf(
                firstCharacter.copy(
                    guiltyBrief = firstCharacter.guiltyBrief.copy(
                        timeline = firstCharacter.guiltyBrief.timeline.mapIndexed { index, entry ->
                            if (index == 0) entry.copy(action = " ") else entry
                        },
                    ),
                ),
            ) + payload.characters.drop(1),
        )
        val blankReveal = payload.copy(
            revealNarratives = payload.revealNarratives + (firstCharacter.id to ""),
        )

        assertRejected(envelope.withPayload(blankTimeline))
        assertRejected(envelope.withPayload(blankReveal))
    }

    @Test
    fun unboundedContentTextIsRejected() = runTest {
        val (envelope, payload) = loadCase()
        assertRejected(envelope.withPayload(payload.copy(publicIntro = "x".repeat(4_097))))
    }

    @Test
    fun roundDeclarationsTheReducerWouldIgnoreAreRejected() = runTest {
        val (envelope, payload) = loadCase()
        val key = payload.roundConfigByPlayerCount.keys.first()
        val config = payload.roundConfigByPlayerCount.getValue(key)
        val firstRound = config.rounds.first()
        val tooManyClues = payload.copy(
            roundConfigByPlayerCount = payload.roundConfigByPlayerCount +
                (key to config.copy(rounds = listOf(firstRound.copy(cluesToReveal = 2)) + config.rounds.drop(1))),
        )
        val unsupportedAction = payload.copy(
            roundConfigByPlayerCount = payload.roundConfigByPlayerCount +
                (key to config.copy(
                    rounds = listOf(
                        firstRound.copy(structuredAction = StructuredAction.DIRECTED_QUESTIONS),
                    ) + config.rounds.drop(1),
                )),
        )

        assertRejected(envelope.withPayload(tooManyClues))
        assertRejected(envelope.withPayload(unsupportedAction))
    }

    @Test
    fun declaredModeMustHaveAPlayableCountAndModeEligibleFinalEvidence() = runTest {
        val (envelope, payload) = loadCase()
        val fourOnly = envelope.copy(supportedPlayerCounts = IntRangePair(4, 4))
        assertRejected(fourOnly)

        val firstCharacter = payload.characters.first().id
        val classicOnlyFinal = payload.copy(
            cluePools = payload.cluePools.copy(
                finalStrong = payload.cluePools.finalStrong +
                    (firstCharacter to payload.cluePools.finalStrong.getValue(firstCharacter).map {
                        it.copy(appliesToModes = listOf("classic-vote"))
                    }),
            ),
        )
        assertRejected(envelope.withPayload(classicOnlyFinal))
    }

    @Test
    fun bundledSourceRejectsDuplicateIdsAndFilenameEnvelopeMismatch() = runTest {
        val (envelope, _) = loadCase()
        assertFailsWith<IllegalArgumentException> {
            BundledWhodunitCases(
                knownCaseIds = listOf("same", "same"),
                loadJson = { null },
                json = json,
            )
        }

        val mismatchedRaw = json.encodeToString(
            CaseEnvelope.serializer(),
            envelope.copy(caseId = "different-id"),
        )
        val source = BundledWhodunitCases(
            knownCaseIds = listOf("expected-id"),
            loadJson = { mismatchedRaw },
            json = json,
        )
        assertFailsWith<IllegalArgumentException> { source.availableCases() }
    }

    @Test
    fun bundledSourceFailsFastWhenDeclaredResourceIsMissing() = runTest {
        val source = BundledWhodunitCases(
            knownCaseIds = listOf("declared-case"),
            loadJson = { null },
            json = json,
        )

        assertFailsWith<IllegalArgumentException> { source.availableCases() }
    }

    @Test
    fun contentIdsRejectTrailingAndConsecutiveHyphens() = runTest {
        val (envelope, payload) = loadCase()
        val first = payload.characters.first()

        listOf("trailing-", "two--segments", "-leading").forEach { invalidId ->
            val invalid = payload.copy(
                characters = listOf(first.copy(id = invalidId)) + payload.characters.drop(1),
            )
            assertRejected(envelope.withPayload(invalid))
        }
    }

    @Test
    fun everyBundledMysteryAdvertisesOnlyItsAuthoredSixCharacterRoster() = runTest {
        BUNDLED_CASE_IDS.forEach { caseId ->
            val raw = Res.readBytes("files/cases/$caseId.json").decodeToString()
            val envelope = json.decodeFromString(CaseEnvelope.serializer(), raw)
            val validated = assertIs<Result.Success<WhodunitCase>>(validator.validate(envelope))

            assertEquals(IntRangePair(6, 6), envelope.supportedPlayerCounts, caseId)
            assertEquals(6, validated.data.characters.size, caseId)
            assertEquals(setOf("6"), validated.data.roundConfigByPlayerCount.keys, caseId)
        }
    }

    private suspend fun loadCase(): Pair<CaseEnvelope, WhodunitCase> {
        val raw = Res.readBytes("files/cases/last-dinner.json").decodeToString()
        val envelope = json.decodeFromString(CaseEnvelope.serializer(), raw)
        val payload = json.decodeFromJsonElement(WhodunitCase.serializer(), envelope.payload)
        return envelope to payload
    }

    private fun CaseEnvelope.withPayload(payload: WhodunitCase): CaseEnvelope = copy(
        payload = json.encodeToJsonElement(WhodunitCase.serializer(), payload),
    )

    private fun assertRejected(envelope: CaseEnvelope) {
        assertIs<Result.Failure<*>>(validator.validate(envelope))
    }

    private companion object {
        val BUNDLED_CASE_IDS = listOf(
            "last-dinner",
            "khan-el-khalili",
            "layla-halabi",
            "iskenderia-corniche",
            "zamalek-ramadan",
            "saidi-inheritance",
            "jasmine-ring",
        )
    }
}
