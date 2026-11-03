package com.parlor.content.validation

import com.parlor.content.schema.CaseSummary
import com.parlor.content.schema.IntRangePair
import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.core.versioning.SemVer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthoredTextScalarBoundaryTest {
    private val valid = CaseSummary(
        caseId = "last-dinner",
        title = "The Last Dinner",
        subtitle = "A mystery",
        version = SemVer(1, 0, 0),
        gameId = "whodunit",
        supportedPlayerCounts = IntRangePair(4, 6),
        supportedModes = listOf("classic-vote"),
        language = "en",
        theme = "country-manor",
        estimatedDuration = IntRangePair(25, 35),
        minimumAppVersion = SemVer(1, 0, 0),
    )

    @Test
    fun supplementaryFormatControlsAreRejectedByAuthoredTextPolicy() {
        supplementaryFormatScalars().forEach { scalar ->
            assertFalse(AuthoredTextPolicy.isSafeForDisplay("Case$scalar"))
            assertFalse(AuthoredTextPolicy.isSafeForDisplay(scalar))
        }
    }

    @Test
    fun summaryTitlesRejectEverySupplementaryFormatControl() {
        supplementaryFormatScalars().forEach { scalar ->
            assertUnsafeSummaryField("title", valid.copy(title = "Case$scalar"))
        }
    }

    @Test
    fun summarySubtitlesRejectEverySupplementaryFormatControl() {
        supplementaryFormatScalars().forEach { scalar ->
            assertUnsafeSummaryField("subtitle", valid.copy(subtitle = "Subtitle$scalar"))
        }
    }

    @Test
    fun summaryThemesRejectEverySupplementaryFormatControl() {
        supplementaryFormatScalars().forEach { scalar ->
            assertUnsafeSummaryField("theme", valid.copy(theme = "Theme$scalar"))
        }
    }

    @Test
    fun authoredTextPreservesInternationalScalarsAndHasNoDisplayNameLengthCap() {
        val title = "ق".repeat(40) + "𐐀🙂\uFE0F"
        listOf("", " ", title, "𐐀🙂\uFE0F").forEach { text ->
            assertTrue(AuthoredTextPolicy.isSafeForDisplay(text))
        }
        val summary = valid.copy(title = title, subtitle = title, theme = title)
        assertEquals(
            Result.Success(listOf(summary)),
            CaseSummaryValidator.validateShape(GameId("whodunit"), listOf(summary)),
        )
    }

    @Test
    fun malformedSurrogatesAndBmpControlsRemainRejected() {
        listOf("\uD800", "\uDC00", "\uD800X", "A\uDC00", "\u0000", "\n", "\u202E").forEach { unsafe ->
            assertFalse(AuthoredTextPolicy.isSafeForDisplay("Case$unsafe"))
        }
    }

    private fun assertUnsafeSummaryField(field: String, summary: CaseSummary) {
        assertEquals(
            Result.Failure(ValidationError.MalformedField(field, "contains unsafe characters")),
            CaseSummaryValidator.validateShape(GameId("whodunit"), listOf(summary)),
        )
    }

    private fun supplementaryFormatScalars(): List<String> {
        // Frozen UnicodeData17.0.0 Cf oracle, independent of production constants.
        val points = listOf(0x110BD, 0x110CD, 0xE0001) + (0x13430..0x1343F) +
            (0x1BCA0..0x1BCA3) + (0x1D173..0x1D17A) + (0xE0020..0xE007F)
        assertEquals(127, points.size)
        return points.map { point ->
            val payload = point - 0x10000
            charArrayOf((0xD800 + (payload shr 10)).toChar(), (0xDC00 + (payload and 0x3FF)).toChar()).concatToString()
        }
    }
}
