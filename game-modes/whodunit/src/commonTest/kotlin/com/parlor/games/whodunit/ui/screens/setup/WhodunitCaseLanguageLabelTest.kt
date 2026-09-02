package com.parlor.games.whodunit.ui.screens.setup

import kotlin.test.Test
import kotlin.test.assertEquals

class WhodunitCaseLanguageLabelTest {
    @Test
    fun englishAndArabicUseTheirPrimaryBcp47Subtag() {
        assertEquals(CaseLanguageLabel.English, caseLanguageLabel("en"))
        assertEquals(CaseLanguageLabel.English, caseLanguageLabel("EN-US"))
        assertEquals(CaseLanguageLabel.Arabic, caseLanguageLabel("ar"))
        assertEquals(CaseLanguageLabel.Arabic, caseLanguageLabel("ar-EG"))
    }

    @Test
    fun anotherLanguageUsesTheFallbackClassification() {
        assertEquals(CaseLanguageLabel.Other, caseLanguageLabel("fr-CA"))
    }
}
