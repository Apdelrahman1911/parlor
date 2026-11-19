package com.parlor.games.whodunit.content

import com.parlor.core.result.Result
import com.parlor.core.result.ValidationError
import com.parlor.games.whodunit.testing.whodunitPeerCaseForTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class WhodunitAuthoredTextScalarBoundaryTest {
    private val json = Json { encodeDefaults = true }
    private val original = whodunitPeerCaseForTest()
    private val validator = WhodunitPayloadValidator(json)

    @Test
    fun publicIntroductionRejectsEverySupplementaryFormatControl() {
        supplementaryFormatScalars().forEach { scalar ->
            assertEquals(
                Result.Failure(ValidationError.PayloadInvalid("publicIntro contains unsafe characters")),
                validate(original.payload.copy(publicIntro = "Intro$scalar")),
            )
        }
    }

    @Test
    fun clueTagsRejectEverySupplementaryFormatControl() {
        val first = original.payload.cluePools.publicUniversal.first()
        supplementaryFormatScalars().forEach { scalar ->
            assertEquals(
                Result.Failure(ValidationError.PayloadInvalid("clue '${first.id}' has invalid tags")),
                validate(withFirstPublicClueTag("Tag$scalar")),
            )
        }
    }

    @Test
    fun validSupplementaryArabicTextEmojiAndVariationMarksRemainUnchanged() {
        val title = "ق".repeat(40) + "𐐀🙂\uFE0F"
        val payload = withFirstPublicClueTag("𐐀🙂\uFE0F").copy(publicIntro = title)
        assertEquals(Result.Success(payload), validate(payload))
    }

    private fun validate(payload: WhodunitCase): Result<WhodunitCase, ValidationError> =
        validator.validate(original.envelope.copy(payload = json.encodeToJsonElement(WhodunitCase.serializer(), payload)))

    private fun withFirstPublicClueTag(tag: String): WhodunitCase = original.payload.copy(
        cluePools = original.payload.cluePools.copy(
            publicUniversal = original.payload.cluePools.publicUniversal.mapIndexed { index, clue ->
                if (index == 0) clue.copy(tags = listOf(tag)) else clue
            },
        ),
    )

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
