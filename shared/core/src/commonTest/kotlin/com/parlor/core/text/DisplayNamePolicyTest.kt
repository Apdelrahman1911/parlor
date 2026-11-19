package com.parlor.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayNamePolicyTest {
    @Test
    fun display_names_support_international_text_but_reject_control_and_bidi_formatting() {
        assertTrue(DisplayNamePolicy.isValidDisplayName("عبد الرحمن"))
        assertTrue(DisplayNamePolicy.isValidDisplayName("Zoë 🎲"))
        assertFalse(DisplayNamePolicy.isValidDisplayName("   "))
        assertFalse(DisplayNamePolicy.isValidDisplayName("Alice\nAdmin"))
        assertFalse(DisplayNamePolicy.isValidDisplayName("Alice\u202EAdmin"))
        assertFalse(DisplayNamePolicy.isValidDisplayName("A".repeat(33)))
        assertFalse(DisplayNamePolicy.isValidDisplayName("Alice\uD83C"))
        assertFalse(DisplayNamePolicy.isValidDisplayName("Alice\uDFB2"))
    }

    @Test
    fun display_name_sanitization_never_splits_a_unicode_surrogate_pair() {
        val die = "🎲"

        assertEquals(
            "A".repeat(31),
            DisplayNamePolicy.sanitizeDisplayNameInput("A".repeat(31) + die),
        )
        assertEquals(
            "A".repeat(30) + die,
            DisplayNamePolicy.sanitizeDisplayNameInput("A".repeat(30) + die + "B"),
        )
        assertEquals(
            "Alice",
            DisplayNamePolicy.sanitizeDisplayNameInput("Ali\uD83Cce\uDFB2"),
        )
    }

    @Test
    fun display_name_rosters_require_valid_canonical_exactly_distinct_names() {
        assertTrue(DisplayNamePolicy.areValidDistinctDisplayNames(listOf("Alice", "Bob")))
        assertTrue(DisplayNamePolicy.areValidDistinctDisplayNames(listOf("Alice", "alice")))

        assertFalse(DisplayNamePolicy.areValidDistinctDisplayNames(listOf("Alice", "Alice")))
        assertFalse(DisplayNamePolicy.areValidDistinctDisplayNames(listOf("Alice", " Alice ")))
        assertFalse(DisplayNamePolicy.areValidDistinctDisplayNames(listOf("Alice", "   ")))
    }

    @Test
    fun supplementary_format_controls_are_rejected_by_name_and_roster_validation() {
        supplementaryFormatCodePoints().forEach { codePoint ->
            val format = supplementaryScalar(codePoint)
            assertFalse(DisplayNamePolicy.isValidDisplayName("Alice$format"), codePoint.toString(16))
            assertFalse(DisplayNamePolicy.isValidDisplayName(format), codePoint.toString(16))
            assertFalse(DisplayNamePolicy.areValidDistinctDisplayNames(listOf("Alice", "Alice$format")))
            assertEquals("Alice", DisplayNamePolicy.sanitizeDisplayNameInput("Ali${format}ce"))
        }
    }

    @Test
    fun discarded_supplementary_format_controls_do_not_consume_the_input_length_budget() {
        val languageTag = supplementaryScalar(0xE0001)
        assertEquals("A".repeat(31) + "B", DisplayNamePolicy.sanitizeDisplayNameInput("A".repeat(31) + languageTag + "B"))
        assertEquals("عبد الرحمن", DisplayNamePolicy.sanitizeDisplayNameInput("عبد${languageTag} الرحمن"))
    }

    @Test
    fun supplementary_letters_emoji_and_variation_marks_remain_valid() {
        listOf(0x10400, 0x1F3B2, 0xE0100).forEach { codePoint ->
            val name = "Alice" + supplementaryScalar(codePoint)
            assertTrue(DisplayNamePolicy.isValidDisplayName(name), codePoint.toString(16))
            assertEquals(name, DisplayNamePolicy.sanitizeDisplayNameInput(name))
        }
        assertTrue(DisplayNamePolicy.areValidDistinctDisplayNames(listOf("Alice", "alice", "عبد الرحمن")))
    }

    // All 127 supplementary Cf scalars in UnicodeData 17.0.0; no network at test time.
    private fun supplementaryFormatCodePoints(): List<Int> =
        listOf(0x110BD, 0x110CD, 0xE0001) + (0x13430..0x1343F) +
            (0x1BCA0..0x1BCA3) + (0x1D173..0x1D17A) + (0xE0020..0xE007F)

    private fun supplementaryScalar(codePoint: Int): String {
        val payload = codePoint - 0x10000
        return charArrayOf((0xD800 + (payload shr 10)).toChar(), (0xDC00 + (payload and 0x3FF)).toChar()).concatToString()
    }
}
