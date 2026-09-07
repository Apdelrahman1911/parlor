package com.parlor.games.whodunit.content

import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Editorial decisions for WD-C2; deliberately false alibis are not factual timelines. */
@OptIn(ExperimentalResourceApi::class)
class TestingStoryChronologyTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun jamesLightsOneCigarAt915WhileHis910AlibiRemainsFalse() = runTest {
        val case = loadCase("last-dinner").payload
        val james = case.characters.single { it.id == "james-sutton" }.guiltyBrief
        val lighting = james.timeline.single { "Light a cigar" in it.action }

        assertEquals("9:15 p.m.", lighting.time)
        assertTrue("9:15" in james.method)
        assertEquals(
            "I was in the smoking room from 9:10. Alone, with a cigar. The ashtray will bear me out.",
            james.fakeAlibi,
        )
        val evidence = listOf(
            case.cluePools.killerPointing.getValue("james-sutton").single { it.id == "kp-james-4" },
            case.cluePools.contradiction.getValue("james-sutton").single(),
            case.cluePools.finalStrong.getValue("james-sutton").single { it.id == "fs-james-2" },
        )
        evidence.forEach { assertTrue("9:15" in it.text, it.id) }
        val finalEvidence = evidence.last().text
        assertTrue("9:10" in finalEvidence)
        assertFalse("twenty-five" in finalEvidence)
        assertTrue("lit a cigar at 9:15" in case.revealNarratives.getValue("james-sutton"))
    }

    @Test
    fun laylaHasOne905OutageAcrossIntroEvidenceAndWitnesses() = runTest {
        val validated = loadCase("layla-halabi")
        val case = validated.payload
        val setting = requireNotNull(validated.envelope.metadata)
            .jsonObject.getValue("settingNotes").jsonPrimitive.content

        assertTrue("التاسعة وخمس دقائق" in setting)
        assertTrue("في التاسعة وخمس دقائق انقطعت الكهرباء" in case.publicIntro)
        assertFalse("التاسعة وخمس وأربعين دقيقة" in case.publicIntro)
        assertTrue(
            "9:05" in case.cluePools.publicUniversal.single { it.id == "ld-bedrock-3" }.text,
        )
        val sami = case.characters.single { it.id == "sami-alhalabi" }
        assertTrue(
            "الكهرباء كانت قد انقطعت" in sami.guiltyBrief.timeline.single { it.time == "9:35 مساءً" }.action,
        )
        assertTrue("9:20" in sami.innocentBrief.alibi)
        val ghassan = case.characters.single { it.id == "ghassan-shoufan" }
        assertTrue("بعد انقطاع الكهرباء" in ghassan.innocentBrief.alibi)
        assertFalse("قبل أن تنقطع الكهرباء" in ghassan.innocentBrief.alibi)
        val tarek = case.characters.single { it.id == "tarek-alhalabi" }
        assertTrue("9:30" in tarek.innocentBrief.alibi)
    }

    @Test
    fun nadimsShortCellarWaitMatchesTheFinalAccountWithoutChangingHisLie() = runTest {
        val case = loadCase("jasmine-ring").payload
        val nadim = case.characters.single { it.id == "nadim-shams" }.guiltyBrief

        assertEquals(
            listOf("10:50 مساءً", "11:00 مساءً", "11:12 مساءً", "11:18 مساءً", "11:25 مساءً"),
            nadim.timeline.map(TimelineEntry::time),
        )
        assertTrue("10:50" in nadim.method)
        assertTrue("11:10" in nadim.method)
        val attack = nadim.timeline.single { it.time == "11:12 مساءً" }.action
        assertTrue("11:10" in attack)
        assertTrue("طعنتَه" in attack)
        val reveal = case.revealNarratives.getValue("nadim-shams")
        listOf("10:50", "11:00", "11:10", "11:12").forEach {
            assertTrue(it in reveal, "Final account must retain $it")
        }
        assertFalse("ساعة كاملة" in reveal)
        assertEquals(
            "خرجتُ لشراء سجائر حوالي 11:00. عدت في 11:25. اسألوا بائع البقالة في رأس الحارة.",
            nadim.fakeAlibi,
        )
        assertTrue("لم يره" in case.cluePools.contradiction.getValue("nadim-shams").single().text)
    }

    @Test
    fun karims940PoisoningPrecedesHisCorroborated1015RecordsAlibi() = runTest {
        val case = loadCase("khan-el-khalili").payload
        val karim = case.characters.single { it.id == "karim-almansoury" }
        val refaat = case.characters.single { it.id == "refaat-elsayed" }
        val poisoning = karim.guiltyBrief.timeline.single { "أفرغتَ المستخلص" in it.action }
        val records = karim.guiltyBrief.timeline.single { "للنزول إلى المحفوظات" in it.action }

        assertEquals("9:40 مساءً", poisoning.time)
        assertEquals("10:15 مساءً", records.time)
        listOf(karim.innocentBrief.alibi, refaat.innocentBrief.alibi, karim.guiltyBrief.fakeAlibi).forEach {
            assertTrue("10:15" in it)
            assertTrue("10:55" in it)
        }
        assertTrue("أخفِ اللحظة" in karim.guiltyBrief.fakeAlibi)
        val refaatRecords = refaat.guiltyBrief.timeline.single { "نزلتَ مع كريم" in it.action }
        assertEquals("10:15 مساءً", refaatRecords.time)
        val reveal = case.revealNarratives.getValue("karim-almansoury")
        assertTrue("منذ العاشرة وربع" in reveal)
        assertTrue("في 9:40" in reveal)
        assertTrue("بخمس وثلاثين دقيقة" in reveal)
        assertFalse("أمّا الدقيقة قبل ذلك" in reveal)
    }

    @Test
    fun correctionsBumpOnlyTheFourTestingCaseVersions() = runTest {
        val correctedIds = setOf("last-dinner", "layla-halabi", "jasmine-ring", "khan-el-khalili")
        bundledWhodunitCaseIds.forEach { id ->
            val case = loadCase(id)
            assertEquals(
                if (id in correctedIds) SemVer(1, 0, 1) else SemVer(1, 0, 0),
                case.envelope.version,
                id,
            )
            assertEquals(SemVer(1, 0, 0), case.envelope.minimumAppVersion, id)
            assertEquals(1, case.envelope.schemaVersion, id)
        }
    }

    private suspend fun loadCase(id: String): ValidatedCase<WhodunitCase> {
        val raw = Res.readBytes("files/cases/$id.json").decodeToString()
        val validator = DefaultCaseValidator(
            json = json,
            knownSchemaVersion = 1,
            installedAppVersion = SemVer(1, 0, 0),
            gameRegistry = DefaultGameRegistry(listOf(WhodunitDefinition(json))),
        )
        return assertIs<Result.Success<ValidatedCase<WhodunitCase>>>(
            validator.validate(raw, WhodunitPayloadValidator(json)),
        ).data
    }
}
