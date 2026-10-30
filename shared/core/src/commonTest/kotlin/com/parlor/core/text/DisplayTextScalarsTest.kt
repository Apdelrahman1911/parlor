package com.parlor.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayTextScalarsTest {
    @Test
    fun everySupplementaryScalarFollowsTheFrozenUnicode17FormatSet() {
        // UnicodeData17.0.0 SHA256 2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c.
        // Deliberately independent of production constants; no runtime data fetch.
        val format = (listOf(0x110BD, 0x110CD, 0xE0001) + (0x13430..0x1343F) +
            (0x1BCA0..0x1BCA3) + (0x1D173..0x1D17A) + (0xE0020..0xE007F)).toSet()
        assertEquals(127, format.size)
        for (codePoint in 0x10000..0x10FFFF) {
            val payload = codePoint - 0x10000
            val high = (0xD800 + (payload shr 10)).toChar()
            val low = (0xDC00 + (payload and 0x3FF)).toChar()
            val scalar = charArrayOf(high, low).concatToString()
            assertEquals(codePoint in format, DisplayTextScalars.isUnsafeSupplementaryPair(high, low))
            assertEquals(codePoint !in format, DisplayTextScalars.containsOnlySafeScalars(scalar))
        }
    }

    @Test
    fun everyUnpairedSurrogateIsRejectedAndSanitizedWithoutConsumingCapacity() {
        for (codeUnit in 0xD800..0xDFFF) {
            val broken = codeUnit.toChar().toString()
            assertFalse(DisplayTextScalars.containsOnlySafeScalars("Ada$broken"))
            assertFalse(DisplayNamePolicy.isValidDisplayName("Ada$broken"))
            assertEquals("A".repeat(31) + "B", DisplayNamePolicy.sanitizeDisplayNameInput("A".repeat(31) + broken + "B"))
        }
    }

    @Test
    fun invalidSupplementaryPairArgumentsFailClosed() {
        listOf('A' to 'B', '\uD800' to 'A', 'A' to '\uDC00', '\uDC00' to '\uD800').forEach { (high, low) ->
            assertTrue(DisplayTextScalars.isUnsafeSupplementaryPair(high, low))
        }
    }

    @Test
    fun bmpSafetyRetainsTheExistingControlAndFormatContract() {
        for (codeUnit in 0..0xFFFF) {
            val character = codeUnit.toChar()
            val safe = codeUnit !in 0xD800..0xDFFF &&
                !character.isISOControl() && character.category != CharCategory.FORMAT
            assertEquals(safe, DisplayTextScalars.containsOnlySafeScalars(character.toString()))
        }
    }
}
