package com.parlor.games.whodunit.content

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Explicit pre-release editorial choices, not an inference about unrecorded author intent. */
class TestingStoryRemainingChronologyTest {
    private val fixture = TestingStoryFixtures()

    @Test
    fun walidsKnowledgeMatchesTheSameLastWeekWillWithoutInventingAnEarlierRevision() = runTest {
        val case = fixture.loadCase("jasmine-ring").payload
        val walid = case.characters.single { it.id == "walid-qasem" }
        val rasha = case.characters.single { it.id == "rasha-khouli" }

        assertTrue("اكتشف الأسبوع الماضي" in walid.privateSecret)
        assertFalse("قبل شهرين" in walid.privateSecret)
        assertTrue("درج القبو الأسبوع الماضي" in walid.innocentBrief.mustHide)
        assertTrue("نجحت في الأسبوع الماضي" in rasha.privateSecret)
        assertTrue("كتب وصية جديدة بناءً على ذلك" in rasha.privateSecret)
        assertTrue("سيوقّعها رسمياً غداً صباحاً" in walid.privateSecret)
        assertTrue("التوقيع غداً" in rasha.privateSecret)
        // Copies and drafts remain evidence; no earlier second will is invented.
        assertTrue("نسخة من الوصية الجديدة" in case.cluePools.finalStrong.getValue("walid-qasem")
            .single { it.id == "jr-walid-fs-1" }.text)
        assertTrue("مسوّدة الوصية الجديدة" in case.cluePools.finalStrong.getValue("rasha-khouli")
            .single { it.id == "jr-rasha-fs-2" }.text)
    }

    @Test
    fun nadimsCurrentClosureMatchesTheRegisterWithoutRewritingHisFalseCigaretteAlibi() = runTest {
        val case = fixture.loadCase("jasmine-ring").payload
        val nadim = case.characters.single { it.id == "nadim-shams" }
        val closure = "محله في حلب أُغلق رسمياً قبل أسبوع"

        assertTrue(closure in nadim.privateSecret)
        assertTrue(closure in case.cluePools.killerPointing.getValue("nadim-shams")
            .single { it.id == "jr-nadim-kp-3" }.text)
        assertFalse("على وشك الإغلاق" in nadim.privateSecret)
        assertTrue("بيعه في إسطنبول مقابل ثلاثة أضعاف" in nadim.privateSecret)
        assertEquals(
            "خرجتُ لشراء سجائر حوالي 11:00. عدت في 11:25. اسألوا بائع البقالة في رأس الحارة.",
            nadim.guiltyBrief.fakeAlibi,
        )
        assertTrue("قال إنه خرج لشراء سجائر" in case.cluePools.contradiction.getValue("nadim-shams")
            .single().text)
        // Retrospective motive formation is not a claim that the shop is still open tonight.
        assertTrue("حين كان المحل على وشك الإغلاق" in case.revealNarratives.getValue("nadim-shams"))
    }

    @Test
    fun guiltyCaptainsDiscoveryAndTwoMinuteHesitationMatchPublicFactsAndCamera() = runTest {
        val case = fixture.loadCase("iskenderia-corniche").payload
        val captain = case.characters.single { it.id == "captain-adel" }
        val timeline = captain.guiltyBrief.timeline

        assertTrue("عند التاسعة والربع وجده الكابتن عادل" in case.publicIntro)
        assertTrue("عند التاسعة والربع" in case.bedrockClues.first())
        assertEquals("خرجتَ إلى الشرفة. وجدتَه. انتظرتَ دقيقتين كاملتين.",
            timeline.single { it.time == "9:15 مساءً" }.action)
        assertEquals("صرختَ. ركض الجميع إلى الشرفة.", timeline.single { it.time == "9:17 مساءً" }.action)
        assertFalse(timeline.any { it.time == "9:10 مساءً" })
        assertTrue("بعد دقيقتين كاملتين من وصوله إلى الشرفة" in
            case.cluePools.killerPointing.getValue("captain-adel").single { it.id == "ic-adel-kp-4" }.text)
        assertTrue("كان قد انتظر دقيقتين كاملتين" in case.revealNarratives.getValue("captain-adel"))
        assertTrue("من 8:40 إلى 9:10" in captain.innocentBrief.alibi)
        // Other characters' guilty-only timelines are mutually exclusive variants.
        val alternateShouts = mapOf(
            "madame-magda" to "صرخة الكابتن عادل من الشرفة.",
            "tarek-aliskandarany" to "صرخة الكابتن عادل من الشرفة.",
            "nour-elbatrony" to "صرخة عادل من الشرفة.",
            "ustaz-wagdy" to "صرخة عادل. ركضتَ مع الباقين.",
            "hagga-fatma" to "صراخ. ركضتِ مع الباقين كأم تخسر ابناً.",
        )
        assertEquals(alternateShouts.keys, case.characters.filter { it.id != captain.id }.map { it.id }.toSet())
        alternateShouts.forEach { (id, shout) ->
            val alternate = case.characters.single { it.id == id }.guiltyBrief.timeline
            assertEquals(shout, alternate.single { it.time == "9:15 مساءً" }.action, id)
        }
    }
}
