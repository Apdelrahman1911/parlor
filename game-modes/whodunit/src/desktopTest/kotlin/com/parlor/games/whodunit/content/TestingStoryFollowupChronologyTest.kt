package com.parlor.games.whodunit.content

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Follow-up editorial regressions use shipping resources and the strict production validators. */
class TestingStoryFollowupChronologyTest {
    private val fixture = TestingStoryFixtures()

    @Test
    fun danielsLampClueUsesTenMinutesWithoutCorrectingHisDeliberateLie() = runTest {
        val case = fixture.loadCase("last-dinner").payload
        val daniel = case.characters.single { it.id == "daniel-hargrove" }.guiltyBrief
        val clue = case.cluePools.killerPointing.getValue("daniel-hargrove")
            .single { it.id == "kp-daniel-4" }.text

        assertEquals(
            "I was in the library from 9:00. Clara saw me at 9:25. I haven't moved.",
            daniel.fakeAlibi,
        )
        assertTrue(daniel.timeline.any { "Light the lamp at 9:10" in it.action })
        assertTrue("9:10 — ten minutes later" in clue)
        assertFalse("fifteen minutes" in clue)
        val contradiction = case.cluePools.contradiction.getValue("daniel-hargrove").single().text
        assertTrue("9:00" in contradiction && "9:10" in contradiction)
    }

    @Test
    fun viviennesBurnedDocumentStaysInTheLibraryAcrossEvidenceAndFinalAccount() = runTest {
        val case = fixture.loadCase("last-dinner").payload
        val vivienne = case.characters.single { it.id == "vivienne-cross" }.guiltyBrief
        val burning = vivienne.timeline.single { "Burn one incriminating page" in it.action }
        assertEquals("9:10 p.m.", burning.time)
        assertTrue("library" in burning.action)

        val clues = case.cluePools.killerPointing.getValue("vivienne-cross")
        assertTrue("library fireplace" in clues.single { it.id == "kp-vivienne-1" }.text)
        assertTrue("library fireplace" in clues.single { it.id == "kp-vivienne-4" }.text)
        assertTrue("library fireplace" in case.revealNarratives.getValue("vivienne-cross"))
        assertTrue("burned document" in case.cluePools.finalStrong.getValue("vivienne-cross")
            .single { it.id == "fs-vivienne-1" }.text)
        // Fetching a different document in the study is still part of this variant.
        assertTrue("study at 8:45" in vivienne.method)
        assertTrue("library from 9:10" in vivienne.fakeAlibi)
    }

    @Test
    fun laylasSingle845SpeechPreservesRanasEarlierTenMinuteDetour() = runTest {
        val case = fixture.loadCase("layla-halabi").payload
        val amal = case.characters.single { it.id == "amal-alhalabi" }.guiltyBrief
        val rana = case.characters.single { it.id == "rana-naqshabandi" }.guiltyBrief

        assertTrue("كأسه في التاسعة إلا ربعاً" in case.publicIntro)
        assertEquals("8:45 مساءً", amal.timeline.single { "كلمته القاسية" in it.action }.time)
        assertTrue("انتظرتِ" in rana.timeline.single { it.time == "8:30 مساءً" }.action)
        assertEquals("8:40 مساءً", rana.timeline.single { "أضفتِ الجرعة" in it.action }.time)
        assertEquals("8:50 مساءً", rana.timeline.single { "عدتِ إلى القاعة الصغيرة" in it.action }.time)
        assertTrue("في الثامنة وأربعين دقيقة، قبل أن يلقي كلمته في الثامنة وخمس وأربعين" in rana.method)
        assertFalse("حين كان منشغلاً بإلقاء كلمته" in rana.method)
        assertEquals(
            "كنت طوال الوقت مع الجدة سعاد في القاعة الصغيرة. (صحيح من 8:50 فصاعداً، لكن أخفِ الدقائق العشر السابقة.)",
            rana.fakeAlibi,
        )
        listOf(
            case.cluePools.killerPointing.getValue("rana-naqshabandi").single { it.id == "ld-rana-kp-4" },
            case.cluePools.contradiction.getValue("rana-naqshabandi").single(),
        ).forEach { assertTrue("الثامنة وأربعين دقيقة" in it.text, it.id) }
    }

    @Test
    fun threeLaylaMethodsSay858LikeTheirOwnTimelines() = runTest {
        val case = fixture.loadCase("layla-halabi").payload
        listOf("ghassan-shoufan", "souad-alhalabi", "tarek-alhalabi").forEach { characterId ->
            val guilty = case.characters.single { it.id == characterId }.guiltyBrief
            assertTrue("في الثامنة وثمانٍ وخمسين دقيقة" in guilty.method, characterId)
            assertFalse("في الثامنة وخمسين" in guilty.method, characterId)
            assertTrue("الجرعة" in guilty.timeline.single { it.time == "8:58 مساءً" }.action, characterId)
        }
    }

    @Test
    fun amalBringsHerFathersNoteButTarekKeepsHisUncleRelationship() = runTest {
        val case = fixture.loadCase("layla-halabi").payload
        val amal = case.characters.single { it.id == "amal-alhalabi" }
        val tarek = case.characters.single { it.id == "tarek-alhalabi" }
        val reveal = case.revealNarratives.getValue("ghassan-shoufan")

        assertTrue("ابنته الكبرى" in amal.relationshipToVictim)
        assertTrue("أحضرت أمل ملاحظة والدها إلى محاميه" in reveal)
        assertFalse("ملاحظة عمها" in reveal)
        assertTrue("ملاحظة من فؤاد إلى محاميه" in case.cluePools.finalStrong.getValue("ghassan-shoufan")
            .single { it.id == "ld-ghassan-fs-2" }.text)
        assertTrue("ابن أخيه" in tarek.relationshipToVictim)
        assertTrue("عمه فؤاد" in tarek.privateSecret)
    }

    @Test
    fun walidsEightMinuteReturnIntervalDoesNotRewriteHisFalseAlibiOrWitnesses() = runTest {
        val case = fixture.loadCase("jasmine-ring").payload
        val walid = case.characters.single { it.id == "walid-qasem" }.guiltyBrief

        assertTrue("صعدتَ إلى القاعة" in walid.timeline.single { it.time == "11:22 مساءً" }.action)
        assertTrue("نزلتَ ثانيةً" in walid.timeline.single { it.time == "11:30 مساءً" }.action)
        assertTrue("بعد ثماني دقائق" in walid.method)
        assertFalse("بعد عشر دقائق" in walid.method)
        assertEquals(
            "كنت في القاعة الخارجية طوال الوقت. لم أنزل إلا حين تأخّر والدي. اسألوا أيّ واحد منهم.",
            walid.fakeAlibi,
        )
        assertTrue("قبل ذلك بعشر دقائق" in case.cluePools.killerPointing.getValue("walid-qasem")
            .single { it.id == "jr-walid-kp-2" }.text)
    }

    @Test
    fun fadisFollowingMorningNoteAgreesWithHisBriefAndThursdaySettingMetadata() = runTest {
        val case = fixture.loadCase("jasmine-ring")
        val fadi = case.payload.characters.single { it.id == "fadi-darwish" }
        // Setting metadata ships in the envelope but is not currently rendered by the UI.
        val setting = requireNotNull(case.envelope.metadata)
            .jsonObject.getValue("settingNotes").jsonPrimitive.content
        assertTrue("سهرة أعمال يوم خميس" in setting)
        assertTrue("صباح الغد بعد توقيع الوصية" in fadi.privateSecret)
        assertTrue("صباح الغد" in fadi.innocentBrief.mustHide)
        val note = case.payload.cluePools.finalStrong.getValue("fadi-darwish")
            .single { it.id == "jr-fadi-fs-2" }.text
        assertTrue("إنهاء وكالته صباح الغد" in note)
        assertFalse("صباح الخميس" in note)
    }

    @Test
    fun refaatsOriginalNotePredatesHisDiscoveryAndMorningReread() = runTest {
        val case = fixture.loadCase("khan-el-khalili").payload
        val refaat = case.characters.single { it.id == "refaat-elsayed" }
        assertTrue("اكتشف الأسبوع الماضي ورقة" in refaat.privateSecret)
        assertTrue("أعدتَ قراءة" in refaat.guiltyBrief.timeline.single { it.time == "7:00 صباحاً" }.action)
        assertTrue("حضّرتَ المستخلص قبل يومين" in refaat.guiltyBrief.method)
        val reveal = case.revealNarratives.getValue("refaat-elsayed")
        assertTrue("التاريخ من الأسبوع الماضي" in reveal)
        assertFalse("التاريخ أمس" in reveal)
        assertTrue("الورقة الأصلية بخط الحاج" in case.cluePools.finalStrong.getValue("refaat-elsayed")
            .single { it.id == "kk-refaat-fs-1" }.text)
    }

    @Test
    fun zahraRemainsTheElderSiblingWithoutRewritingHerFiftyYearHistory() = runTest {
        val case = fixture.loadCase("saidi-inheritance").payload
        val zahra = case.characters.single { it.id == "hagga-zahra" }
        val mostafa = case.characters.single { it.id == "mostafa-saidy" }
        assertTrue("62 عاماً" in case.bedrockClues.first())
        assertEquals("شقيقته الكبرى، 68 عاماً.", zahra.relationshipToVictim)
        assertTrue("قبل خمسين سنة" in zahra.publicIdentity)
        assertTrue("في الثامنة عشرة" in requireNotNull(zahra.optionalDetails).backstory.orEmpty())
        assertTrue("لرجل في الستين" in mostafa.guiltyBrief.method)
        assertFalse("لرجل في السبعين" in mostafa.guiltyBrief.method)
    }

    @Test
    fun magdasElderSiblingAgeAllowsHerExistingCaregiverHistory() = runTest {
        val case = fixture.loadCase("iskenderia-corniche").payload
        val magda = case.characters.single { it.id == "madame-magda" }
        assertTrue("70 عاماً" in case.bedrockClues.first())
        assertEquals("شقيقته الكبرى، 75 عاماً.", magda.relationshipToVictim)
        assertTrue("وهي في الخامسة عشرة" in requireNotNull(magda.optionalDetails).backstory.orEmpty())
        assertTrue("لم تتزوّج" in magda.publicIdentity)
    }

    @Test
    fun fatmasPreparationMatchesHerFactualTimelineAndBottleEvidence() = runTest {
        val case = fixture.loadCase("iskenderia-corniche").payload
        val fatma = case.characters.single { it.id == "hagga-fatma" }.guiltyBrief
        assertTrue("أخذتِ العلبة" in fatma.timeline.single { it.time == "7:00 صباحاً" }.action)
        assertTrue("غرفة المؤونة" in fatma.timeline.single { it.time == "8:25 مساءً" }.action)
        assertTrue("أخذتِ علبة منه هذا الصباح" in fatma.method)
        assertTrue("عند الثامنة وخمس وعشرين مساءً" in fatma.method)
        assertTrue("في غرفة المؤونة" in fatma.method)
        assertTrue("زجاجة صغيرة في جيب مريلتكِ" in fatma.method)
        assertTrue("أفرغتِ المسحوق" in fatma.timeline.single { it.time == "8:35 مساءً" }.action)
        assertEquals(
            "كنتُ في المطبخ مع الكابتن طوال الوقت. (صحيح من 8:40، لكن أخفِ المرور بالمائدة.)",
            fatma.fakeAlibi,
        )
        assertTrue("زجاجة عطر فارغة في جيب مريلتها بها بقايا مسحوق" in
            case.cluePools.killerPointing.getValue("hagga-fatma").single { it.id == "ic-fatma-kp-2" }.text)
        assertTrue("زجاجة العطر في جيب المريلة" in case.revealNarratives.getValue("hagga-fatma"))
    }

    @Test
    fun mokhtarsTwoYearHistoryMatchesTheAuditAndFinalAccount() = runTest {
        val case = fixture.loadCase("zamalek-ramadan").payload
        val mokhtar = case.characters.single { it.id == "hagg-mokhtar" }
        assertTrue("منذ سنتين" in mokhtar.privateSecret)
        assertTrue("لسنتين" in case.cluePools.finalStrong.getValue("hagg-mokhtar")
            .single { it.id == "zr-mokhtar-fs-1" }.text)
        val reveal = case.revealNarratives.getValue("hagg-mokhtar")
        assertTrue("سنتان من اختلاسات" in reveal)
        assertFalse("ثلاث سنوات" in reveal)
    }

    @Test
    fun zamalekApproximateDelayDoesNotImposeAnImpossibleTwoHourMinimum() = runTest {
        val case = fixture.loadCase("zamalek-ramadan").payload
        case.characters.forEach { character ->
            assertTrue(character.guiltyBrief.timeline.any { it.time == "6:48 مساءً" }, character.id)
        }
        assertTrue("8:30 و8:45" in case.bedrockClues[2])
        assertTrue("بعد نحو ساعتين" in case.bedrockClues[2])
        val clue = case.cluePools.publicUniversal.single { it.id == "zr-bedrock-3" }.text
        assertTrue("قبل نحو ساعتين" in clue)
        assertTrue("قبل الإفطار" in clue)
        assertFalse("على الأقل" in clue)
    }
}
